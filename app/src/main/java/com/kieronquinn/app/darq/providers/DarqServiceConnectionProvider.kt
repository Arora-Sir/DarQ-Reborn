package com.kieronquinn.app.darq.providers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.IDarqService
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.model.shizuku.ShizukuConstants
import com.kieronquinn.app.darq.service.impl.DarqService
import com.kieronquinn.app.darq.service.root.DarqRootService
import com.kieronquinn.app.darq.utils.extensions.isShizukuInstalled
import com.kieronquinn.app.darq.utils.extensions.suspendCoroutineWithTimeout
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import kotlin.coroutines.resume

class DarqServiceConnectionProvider(private val context: Context, private val settings: DarqSharedPreferences) {

    companion object {
        private const val SERVICE_TIMEOUT = 25000L

        //Every failure path below logs under this tag. Service connection failures are otherwise
        //silent, which means a user reporting "Service Timeout" gives us nothing to work with -
        //the only clues end up being Shizuku's own logs rather than ours.
        private const val TAG = "DarqServiceConn"
    }

    private val rootServiceIntent = Intent(context, DarqRootService::class.java)
    private var rootService: IDarqService? = null
    private val serviceLock = Mutex()

    private val darqProcessArgs = UserServiceArgs(ComponentName(context, DarqService::class.java)).apply {
        processNameSuffix(ShizukuConstants.SERVICE_NAME)
        debuggable(BuildConfig.DEBUG)
        version(BuildConfig.VERSION_CODE)
    }

    sealed class ServiceResult {
        data class Success(val service: IDarqService, val serviceType: ServiceType): ServiceResult()
        data class Failed(val reason: ServiceFailureReason): ServiceResult()
    }

    enum class ServiceFailureReason {
        TIMEOUT, SHIZUKU_PERMISSION_REQUIRED, SHIZUKU_NOT_STARTED, SHIZUKU_NOT_INSTALLED
    }

    enum class ServiceType {
        SHIZUKU, ROOT, UNKNOWN
    }

    private var serviceType: ServiceType = ServiceType.UNKNOWN

    suspend fun getService(): ServiceResult {
        return serviceLock.withLock {
            withContext(Dispatchers.Main) {
                getServiceLocked()
            }
        }
    }

    private suspend fun getServiceLocked(): ServiceResult = withTimeoutOrNull(SERVICE_TIMEOUT) {
        suspendCancellableCoroutine<ServiceResult> { continuation ->
            rootService?.let { service ->
                try {
                    service.ping()
                    if (continuation.isActive) {
                        continuation.resume(ServiceResult.Success(service, serviceType))
                    }
                    return@suspendCancellableCoroutine
                } catch (e: Exception) {
                    // Service died, fall through and rebind
                    Log.w(TAG, "Cached service did not respond to ping, rebinding", e)
                }
            }

            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    try {
                        val rootService = IDarqService.Stub.asInterface(service)
                        rootService.setupService()
                        this@DarqServiceConnectionProvider.rootService = rootService
                        if (continuation.isActive) {
                            continuation.resume(ServiceResult.Success(rootService, serviceType))
                        }
                    } catch (e: Exception) {
                        //The service connected but setup failed. Reported as TIMEOUT for
                        //compatibility with existing UI handling, so log the real cause here.
                        Log.e(TAG, "Service connected but setup failed", e)
                        if (continuation.isActive) {
                            continuation.resume(ServiceResult.Failed(ServiceFailureReason.TIMEOUT))
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.w(TAG, "Service disconnected: $name")
                    rootService = null
                }
            }

            continuation.invokeOnCancellation {
                try {
                    if (Shell.rootAccess()) {
                        RootService.unbind(serviceConnection)
                    } else if (context.isShizukuInstalled()) {
                        Shizuku.unbindUserService(darqProcessArgs, serviceConnection, true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unbind service on cancellation", e)
                }
            }

            if (Shell.rootAccess()) {
                serviceType = ServiceType.ROOT
                RootService.stop(rootServiceIntent)
                RootService.bind(rootServiceIntent, serviceConnection)
            } else if (context.isShizukuInstalled()) {
                serviceType = ServiceType.SHIZUKU
                GlobalScope.launch {
                    runCatching {
                        val selfPermission = Shizuku.checkSelfPermission()
                        if (selfPermission == PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, "Binding Shizuku user service")
                            Shizuku.bindUserService(darqProcessArgs, serviceConnection)
                        } else {
                            Log.i(TAG, "Shizuku permission not granted")
                            if (continuation.isActive) {
                                continuation.resume(ServiceResult.Failed(ServiceFailureReason.SHIZUKU_PERMISSION_REQUIRED))
                            }
                        }
                    }.onFailure {
                        Log.e(TAG, "Shizuku bind failed, reporting service as not started", it)
                        if (continuation.isActive) {
                            continuation.resume(ServiceResult.Failed(ServiceFailureReason.SHIZUKU_NOT_STARTED))
                        }
                    }
                }
            } else {
                Log.i(TAG, "No root access and no Shizuku provider installed")
                if (continuation.isActive) {
                    continuation.resume(ServiceResult.Failed(ServiceFailureReason.SHIZUKU_NOT_INSTALLED))
                }
            }
        }
    } ?: ServiceResult.Failed(ServiceFailureReason.TIMEOUT).also {
        //Nothing called back within SERVICE_TIMEOUT. On Shizuku this usually means the spawned
        //service process failed to start, which is only visible in Shizuku's own logs.
        Log.e(TAG, "No service connection after ${SERVICE_TIMEOUT}ms (type=$serviceType)")
    }

    private fun IDarqService.setupService(){
        GlobalScope.launch {
            withContext(Dispatchers.IO){
                //Shizuku's UserServiceArgs defaults to daemon(true), so a service process outlives
                //the app and survives app updates, and nothing reaped the leftovers:
                //killOtherInstances() was only reachable from the Developer Options button. Two
                //orphans were found alive for 7h and 8.5h on a test device after a 3.1.3 -> 3.1.4
                //update, each with its own registered IProcessObserver and its own stale whitelist,
                //writing conflicting debug.hwui.force_dark values. Reap them before we bind.
                try {
                    killOtherInstances()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to reap orphaned service instances", e)
                }
                //Send settings and the whitelist before onBind(), which is what registers the
                //process observer. Registering first left a window where the observer was live with
                //an empty whitelist and would clear force dark for apps that are actually selected.
                setupSettings(settings.toIPCSetting())
                val enabledApps = settings.enabledApps
                enabledApps.forEachIndexed { index, app ->
                    setupWhitelist(index == 0, index == enabledApps.size - 1, app)
                }
                onBind()
            }
        }
    }

}