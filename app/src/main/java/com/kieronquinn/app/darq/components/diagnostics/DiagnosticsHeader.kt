package com.kieronquinn.app.darq.components.diagnostics

import android.os.Build
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.xposed.XposedBridge
import com.kieronquinn.app.darq.components.xposed.XposedState
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider.ServiceType
import rikka.shizuku.Shizuku

/**
 *  The facts at the top of every report, and the short fields prefilled in the GitHub bug form.
 *  Settings are summarized as counts and switches only, never the list of apps.
 */
class DiagnosticsHeader(
    private val settings: DarqSharedPreferences,
    private val connection: DarqServiceConnectionProvider
) {

    val appVersion: String
        get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"

    val android: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}"

    val device: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"

    val mode: String
        get() {
            val service = when (connection.connectedServiceType) {
                ServiceType.SHIZUKU -> "Shizuku"
                ServiceType.ROOT -> "Root"
                ServiceType.UNKNOWN -> "Service not connected"
            }
            return if (XposedState.isActive) "Xposed module active, $service" else service
        }

    private val shizuku: String
        get() = try {
            if (Shizuku.pingBinder()) "version ${Shizuku.getVersion()}, server uid ${Shizuku.getUid()}" else "not running"
        } catch (t: Throwable) {
            "not available"
        }

    private val xposed: String
        get() = when {
            XposedBridge.isActiveNow -> "active through LibXposed"
            XposedState.isActive -> "active through the legacy entry"
            else -> "not active"
        }

    fun build(): String = buildString {
        appendLine("# DarQ Reborn diagnostic report")
        appendLine("App: $appVersion")
        appendLine("Android: $android, build ${Build.DISPLAY}")
        appendLine("Device: $device")
        appendLine("Mode: $mode")
        appendLine("Shizuku: $shizuku")
        appendLine("Xposed: $xposed")
        appendLine(
            "Settings: enabled=${settings.enabled}, selected apps=${settings.enabledApps.size}, always force dark=${settings.alwaysForceDark}, " +
                "auto dark schedule=${settings.autoDarkScheduleMode}, target=${settings.autoDarkTargetMode}, persistent service=${settings.persistentService}, " +
                "oxygen=${settings.oxygenForceDark}, theme=${settings.themeMode}/${settings.themeAccent}/black=${settings.themePureBlack}"
        )
    }

}
