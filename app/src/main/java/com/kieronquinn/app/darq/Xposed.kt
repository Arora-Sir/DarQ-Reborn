package com.kieronquinn.app.darq

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.View
import com.kieronquinn.app.darq.model.xposed.EnabledAppsParser
import com.kieronquinn.app.darq.model.xposed.ForceDarkMapping
import com.kieronquinn.app.darq.model.xposed.XposedPrefKeys
import com.kieronquinn.app.darq.model.xposed.XposedRules
import com.kieronquinn.app.darq.model.xposed.XposedSettings
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

// NOTE: LibXposed (API 101 and 102) module entry, declared in META-INF/xposed/java_init.list.
// NOTE: Settings arrive through the framework's remote preferences, which the app writes via XposedBridge, so no world-readable file or self-hook is involved.
class Xposed : XposedModule() {

    companion object {
        private const val TAG = "DarQXposed"
        private const val FORCE_DARK_PROP = "debug.hwui.force_dark"
    }

    private val hooksInstalled = AtomicBoolean(false)
    private val enabledAppsParser = EnabledAppsParser()

    @Volatile
    private var targetPackage = ""

    @Volatile
    private var cachedSettings: XposedSettings? = null

    private val remotePrefs: SharedPreferences? by lazy {
        try {
            getRemotePreferences(XposedPrefKeys.GROUP)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Remote preferences unavailable for $targetPackage", t)
            null
        }
    }

    // Held as a field because SharedPreferences implementations may keep listeners weakly.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        cachedSettings = null
    }

    private val isSystemDark: Boolean
        get() = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        // Framework hooks are process-wide, so they install once for the app and skip code packages loaded later (WebView, GMS modules).
        if (!param.isFirstPackage) return
        if (param.packageName == BuildConfig.APPLICATION_ID) return
        if (!hooksInstalled.compareAndSet(false, true)) return
        targetPackage = param.packageName
        try {
            remotePrefs?.registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Preference change listener unavailable, settings refresh on activity resume only", t)
        }
        installHooks()
    }

    private fun settings(): XposedSettings {
        cachedSettings?.let { return it }
        val prefs = remotePrefs ?: return XposedSettings(enabled = false)
        return XposedRules.resolve(prefs, targetPackage, enabledAppsParser).also {
            cachedSettings = it
        }
    }

    private fun installHooks() {
        val systemProperties = Class.forName("android.os.SystemProperties")

        hookSafely("SystemProperties.getBoolean") {
            val method = HiddenApiBypass.getDeclaredMethod(systemProperties, "getBoolean", String::class.java, java.lang.Boolean.TYPE)
            intercept(method) { chain ->
                if (chain.getArg(0) == FORCE_DARK_PROP && settings().isActive) isSystemDark else chain.proceed()
            }
        }

        hookSafely("SystemProperties.get(String)") {
            val method = HiddenApiBypass.getDeclaredMethod(systemProperties, "get", String::class.java)
            intercept(method) { chain ->
                if (chain.getArg(0) == FORCE_DARK_PROP && settings().isActive) isSystemDark.toString() else chain.proceed()
            }
        }

        hookSafely("SystemProperties.get(String, String)") {
            val method = HiddenApiBypass.getDeclaredMethod(systemProperties, "get", String::class.java, String::class.java)
            intercept(method) { chain ->
                if (chain.getArg(0) == FORCE_DARK_PROP && settings().isActive) isSystemDark.toString() else chain.proceed()
            }
        }

        hookSafely("View.setForceDarkAllowed") {
            val method = View::class.java.getDeclaredMethod("setForceDarkAllowed", java.lang.Boolean.TYPE)
            intercept(method) { chain ->
                if (settings().isActive) chain.proceed(arrayOf<Any?>(true)) else chain.proceed()
            }
        }

        hookSafely("Activity.onResume") {
            val method = Activity::class.java.getDeclaredMethod("onResume")
            intercept(method) { chain ->
                val result = chain.proceed()
                // Re-resolving on resume keeps settings fresh even when the framework does not deliver preference change callbacks.
                cachedSettings = null
                val settings = settings()
                if (settings.isActive && settings.invertStatus == true && isSystemDark) {
                    (chain.thisObject as? Activity)?.clearLightStatusBar()
                }
                result
            }
        }

        hookSafely("HardwareRenderer.setForceDark") {
            val renderer = Class.forName("android.graphics.HardwareRenderer")
            val typeVariant = try {
                HiddenApiBypass.getDeclaredMethod(renderer, "setForceDark", Integer.TYPE)
            } catch (e: NoSuchMethodException) {
                null
            }
            if (typeVariant != null) {
                // Android 15 and later pass a ForceDarkType int.
                intercept(typeVariant) { chain ->
                    val settings = settings()
                    if (settings.isActive && settings.aggressiveDark == true) {
                        chain.proceed(arrayOf<Any?>(ForceDarkMapping.mapType(chain.getArg(0) as Int, isSystemDark)))
                    } else {
                        chain.proceed()
                    }
                }
            } else {
                // Android 10 to 14 pass a boolean.
                val booleanVariant = HiddenApiBypass.getDeclaredMethod(renderer, "setForceDark", java.lang.Boolean.TYPE)
                intercept(booleanVariant) { chain ->
                    val settings = settings()
                    if (settings.isActive && settings.aggressiveDark == true) {
                        chain.proceed(arrayOf<Any?>(ForceDarkMapping.mapBoolean(isSystemDark)))
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
    }

    private fun intercept(method: Method, hooker: XposedInterface.Hooker) {
        hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(hooker)
    }

    private inline fun hookSafely(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Skipped hook $name in $targetPackage", t)
        }
    }

    // Matches the legacy module, which cleared the flag through systemUiVisibility so the app's own flag handling keeps working.
    @Suppress("DEPRECATION")
    private fun Activity.clearLightStatusBar() {
        val decorView = window?.decorView ?: return
        decorView.post {
            decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

}
