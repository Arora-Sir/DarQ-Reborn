package com.kieronquinn.app.darq.components.xposed

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kieronquinn.app.darq.model.xposed.XposedPrefKeys
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// NOTE: App-side link to the LSPosed framework service, replacing the legacy self-hook and the world-readable preferences file.
// NOTE: A bound service is the "module active" signal. It arrives asynchronously, so synchronous callers read isActiveNow and UI collects isActive.
// NOTE: The listener is registered in Application.attachBaseContext, before Koin exists, so this is a plain object and the local preferences are attached later.
object XposedBridge : XposedServiceHelper.OnServiceListener {

    private const val TAG = "XposedBridge"

    sealed class ScopeResult {
        data class Approved(val packages: List<String>) : ScopeResult()
        data class Failed(val message: String) : ScopeResult()
        object AlreadyInScope : ScopeResult()
        object Unavailable : ScopeResult()
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var service: XposedService? = null
    private var remotePrefs: SharedPreferences? = null
    private var localPrefs: SharedPreferences? = null
    private var onActiveChanged: ((Boolean) -> Unit)? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    val isActiveNow: Boolean
        get() = _isActive.value

    fun register() {
        XposedServiceHelper.registerListener(this)
    }

    // Called from Application.onCreate once the local preferences exist. A service that bound earlier is synced right away.
    fun attach(localPrefs: SharedPreferences, onActiveChanged: (Boolean) -> Unit) {
        val active = synchronized(lock) {
            this.localPrefs = localPrefs
            this.onActiveChanged = onActiveChanged
            service != null
        }
        if (active) syncAll()
    }

    override fun onServiceBind(service: XposedService) {
        val callback = synchronized(lock) {
            this.service = service
            remotePrefs = try {
                service.getRemotePreferences(XposedPrefKeys.GROUP)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open remote preferences", t)
                null
            }
            onActiveChanged
        }
        Log.i(TAG, "Bound to ${service.frameworkName} ${service.frameworkVersion} (API ${service.apiVersion})")
        // Writes made before the bind (boot, alarms, the Auto Dark service) are covered by this full resync.
        syncAll()
        _isActive.value = true
        callback?.invoke(true)
    }

    override fun onServiceDied(service: XposedService) {
        val callback = synchronized(lock) {
            if (this.service !== service) return
            this.service = null
            remotePrefs = null
            onActiveChanged
        }
        Log.w(TAG, "Framework service died")
        _isActive.value = false
        callback?.invoke(false)
    }

    fun syncAll() {
        synchronized(lock) {
            val local = localPrefs ?: return
            val remote = remotePrefs ?: return
            try {
                remote.edit().also { XposedPrefsSync.writeAll(local, it) }.apply()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to sync remote preferences", t)
            }
        }
    }

    fun mirror(key: String) {
        if (key !in XposedPrefKeys.ALL) return
        synchronized(lock) {
            val local = localPrefs ?: return
            val remote = remotePrefs ?: return
            try {
                val editor = remote.edit()
                if (XposedPrefsSync.writeKey(local, editor, key)) editor.apply()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to mirror $key", t)
            }
        }
    }

    // Asks LSPosed to add the given packages to the module scope, skipping ones already in it. The result is delivered on the main thread.
    fun requestScope(packages: Collection<String>, onResult: (ScopeResult) -> Unit) {
        val current = synchronized(lock) { service }
        if (current == null) {
            postResult(onResult, ScopeResult.Unavailable)
            return
        }
        try {
            val inScope = current.scope.toSet()
            val missing = packages.filter { it !in inScope }.distinct()
            if (missing.isEmpty()) {
                postResult(onResult, ScopeResult.AlreadyInScope)
                return
            }
            current.requestScope(missing, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    postResult(onResult, ScopeResult.Approved(approved))
                }

                override fun onScopeRequestFailed(message: String) {
                    postResult(onResult, ScopeResult.Failed(message))
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Scope request failed", t)
            postResult(onResult, ScopeResult.Failed(t.message ?: t.javaClass.simpleName))
        }
    }

    private fun postResult(onResult: (ScopeResult) -> Unit, result: ScopeResult) {
        mainHandler.post { onResult(result) }
    }

}
