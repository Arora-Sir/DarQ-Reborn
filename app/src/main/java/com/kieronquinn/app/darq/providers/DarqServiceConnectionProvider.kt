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
        private const val SERVICE_TIMEOUT = 40000L

        // Every failure path below logs under this tag. Service connection failures are otherwise
        // silent. A report of a timeout provides little diagnostic value without local log entries.
        // The only remaining indicators would be external Shizuku system logs.
        private const val TAG = "DarqServiceConn"
    }

    private val rootServiceIntent = Intent(context, DarqRootService::class.java)
    private var rootService: IDarqService? = null
    private val serviceLock = Mutex()

    private val darqProcessArgs = UserServiceArgs(ComponentName(context, DarqService::class.java)).apply {
        processNameSuffix(ShizukuConstants.SERVICE_NAME)
        debuggable(BuildConfig.DEBUG)
        version(BuildConfig.VERSION_CODE)
        daemon(false)
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

    init {
        if (context.isShizukuInstalled()) {
            try {
                Shizuku.addBinderDeadListener {
                    Log.w(TAG, "Shizuku binder died, invalidating cached service")
                    rootService = null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register Shizuku binder dead listener", e)
            }
        }
    }

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
                    rootService = null
                }
            }

            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val rootService = IDarqService.Stub.asInterface(service)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            rootService.setupService()
                            this@DarqServiceConnectionProvider.rootService = rootService
                            if (continuation.isActive) {
                                continuation.resume(ServiceResult.Success(rootService, serviceType))
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Service connected but setup failed (${t.javaClass.simpleName})", t)
                            this@DarqServiceConnectionProvider.rootService = null
                            if (continuation.isActive) {
                                continuation.resume(ServiceResult.Failed(ServiceFailureReason.TIMEOUT))
                            }
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

    private suspend fun IDarqService.setupService() = withContext(Dispatchers.IO) {
        //Reap stale orphaned service processes from previous app version launches.
        //killOtherInstances is already guarded. Keep it isolated so a failure here
        //does not abort the more critical setup steps below.
        try {
            killOtherInstances()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to reap orphaned service instances", t)
        }
        //Send settings and whitelist before onBind() registers the process observer.
        //Any Binder failure here (DeadObjectException, RemoteException) propagates up
        //so onServiceConnected can return Failed instead of crashing the application process.
        setupSettings(settings.toIPCSetting())
        val enabledApps = settings.enabledApps
        enabledApps.forEachIndexed { index, app ->
            setupWhitelist(index == 0, index == enabledApps.size - 1, app)
        }
        onBind()
    }

}