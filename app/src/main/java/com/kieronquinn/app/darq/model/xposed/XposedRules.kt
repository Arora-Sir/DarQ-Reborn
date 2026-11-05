package com.kieronquinn.app.darq.model.xposed

import android.content.SharedPreferences
import org.json.JSONArray

// NOTE: Pure decision logic for the hooks, kept free of framework calls so it runs in JVM unit tests.
// NOTE: The rules match the legacy module exactly: DarQ must be enabled, the app must be selected or "Always use force dark" must be on, and a light Auto Dark period blocks everything.
object XposedRules {

    fun resolve(prefs: SharedPreferences, packageName: String, enabledAppsParser: EnabledAppsParser): XposedSettings {
        return try {
            val darqEnabled = prefs.getBoolean(XposedPrefKeys.ENABLED, XposedPrefKeys.DEFAULT_ENABLED)
            val scheduleMode = prefs.getInt(XposedPrefKeys.AUTO_DARK_SCHEDULE_MODE, XposedPrefKeys.DEFAULT_AUTO_DARK_SCHEDULE_MODE)
            val managedEnabled = prefs.getBoolean(XposedPrefKeys.AUTO_DARK_MANAGED_ENABLED, XposedPrefKeys.DEFAULT_AUTO_DARK_MANAGED_ENABLED)
            val isScheduleBlocking = scheduleMode != 0 && !managedEnabled
            val enabledApps = enabledAppsParser.parse(prefs.getString(XposedPrefKeys.ENABLED_APPS, XposedPrefKeys.DEFAULT_ENABLED_APPS))
            val alwaysForceDark = prefs.getBoolean(XposedPrefKeys.ALWAYS_FORCE_DARK, XposedPrefKeys.DEFAULT_ALWAYS_FORCE_DARK)
            if (!darqEnabled || (!enabledApps.contains(packageName) && !alwaysForceDark)) {
                XposedSettings(enabled = false, isScheduleBlocking = isScheduleBlocking)
            } else {
                XposedSettings(
                    enabled = true,
                    isScheduleBlocking = isScheduleBlocking,
                    aggressiveDark = prefs.getBoolean(XposedPrefKeys.AGGRESSIVE_DARK, XposedPrefKeys.DEFAULT_AGGRESSIVE_DARK),
                    invertStatus = prefs.getBoolean(XposedPrefKeys.INVERT_STATUS_BAR, XposedPrefKeys.DEFAULT_INVERT_STATUS_BAR)
                )
            }
        } catch (e: RuntimeException) {
            // A value stored with an unexpected type must never crash the hooked app, so it disables force dark instead.
            XposedSettings(enabled = false)
        }
    }

}

// NOTE: Caches the parsed enabled_apps set by its raw JSON string, because hooks resolve settings far more often than the list changes.
class EnabledAppsParser {

    private var lastRaw: String? = null
    private var lastApps: Set<String> = emptySet()

    @Synchronized
    fun parse(raw: String?): Set<String> {
        if (raw == lastRaw) return lastApps
        val apps = try {
            val array = JSONArray(raw ?: XposedPrefKeys.DEFAULT_ENABLED_APPS)
            HashSet<String>(array.length()).apply {
                for (i in 0 until array.length()) {
                    add(array.getString(i))
                }
            }
        } catch (e: Exception) {
            emptySet()
        }
        lastRaw = raw
        lastApps = apps
        return apps
    }

}
