package com.kieronquinn.app.darq.components.settings

import com.kieronquinn.app.darq.model.settings.IPCSetting
import com.kieronquinn.app.darq.model.settings.SettingsBackup
import com.kieronquinn.app.darq.model.xposed.XposedSelfHooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

abstract class DarqSharedPreferences: BaseSharedPreferences() {

    companion object {

        private const val KEY_ENABLED = "enabled"
        private const val DEFAULT_ENABLED = true

        private const val KEY_AUTO_DARK_THEME = "auto_dark_theme"
        private const val DEFAULT_AUTO_DARK_THEME = false

        private const val KEY_SEND_APP_CLOSES = "send_app_closes"
        private const val DEFAULT_SEND_APP_CLOSES = false

        private const val KEY_OXYGEN_FORCE_DARK = "oxygen_force_dark"
        private const val DEFAULT_OXYGEN_FORCE_DARK = false

        private const val KEY_ALWAYS_FORCE_DARK = "always_force_dark"
        private const val DEFAULT_ALWAYS_FORCE_DARK = false

        private const val KEY_USE_LOCATION = "use_location"
        private const val DEFAULT_USE_LOCATION = false

        private const val KEY_CHECK_FOR_UPDATES = "check_for_updates"
        private const val DEFAULT_CHECK_FOR_UPDATES = true

        private const val KEY_CHECK_FOR_PRERELEASES = "check_for_prereleases"
        private const val DEFAULT_CHECK_FOR_PRERELEASES = false

        private const val KEY_DEVELOPER_OPTIONS = "developer_options"
        private const val DEFAULT_DEVELOPER_OPTIONS = false

        private const val KEY_PERSISTENT_SERVICE = "persistent_service"
        private const val DEFAULT_PERSISTENT_SERVICE = false

        private const val KEY_BOOT_WAIT_SHIZUKU = "boot_wait_shizuku"
        private const val DEFAULT_BOOT_WAIT_SHIZUKU = true

        private const val KEY_AUTO_DARK_SCHEDULE_MODE = "auto_dark_schedule_mode"
        private const val DEFAULT_AUTO_DARK_SCHEDULE_MODE = 0 // 0 = Off, 1 = Sunset/Sunrise, 2 = Custom Time

        private const val KEY_AUTO_DARK_TARGET_MODE = "auto_dark_target_mode"
        private const val DEFAULT_AUTO_DARK_TARGET_MODE = 1 // 0 = System Dark Mode, 1 = DarQ Force Dark Only

        private const val KEY_AUTO_DARK_START_TIME = "auto_dark_start_time"
        private const val DEFAULT_AUTO_DARK_START_TIME = 1200 // 8:00 PM (1200 mins from midnight)

        private const val KEY_AUTO_DARK_END_TIME = "auto_dark_end_time"
        private const val DEFAULT_AUTO_DARK_END_TIME = 420 // 7:00 AM (420 mins from midnight)

        // Whether Auto Dark Schedule is currently in a dark period. Managed ONLY by the schedule,
        // NOT by the user. This separates schedule state from settings.enabled (user master toggle).
        private const val KEY_AUTO_DARK_MANAGED_ENABLED = "auto_dark_managed_enabled"
        const val DEFAULT_AUTO_DARK_MANAGED_ENABLED = true

        // Stores the system night mode state BEFORE Auto Dark first took control (for System & DarQ mode restore)
        private const val KEY_PRE_AUTO_DARK_SYSTEM_NIGHT_MODE = "pre_auto_dark_system_night_mode"

        // Whether Auto Dark has ever activated and saved a pre-state to restore
        private const val KEY_AUTO_DARK_HAS_MANAGED = "auto_dark_has_managed"

        private const val KEY_XPOSED_AGGRESSIVE_DARK = "xposed_aggressive_dark"
        const val DEFAULT_XPOSED_AGGRESSIVE_DARK = true

        private const val KEY_XPOSED_INVERT_STATUS_BAR = "xposed_invert_status_bar"
        const val DEFAULT_XPOSED_INVERT_STATUS_BAR = true

        private const val KEY_UI_MONET_COLOR = "monet_color"

        const val KEY_ENABLED_APPS = "enabled_apps"

    }

    abstract val changed: Flow<IPCSetting>

    var enabled by this.shared(KEY_ENABLED, DEFAULT_ENABLED)
    var sendAppCloses by this.shared(KEY_SEND_APP_CLOSES, DEFAULT_SEND_APP_CLOSES)
    var oxygenForceDark by this.shared(KEY_OXYGEN_FORCE_DARK, DEFAULT_OXYGEN_FORCE_DARK)
    var alwaysForceDark by this.shared(KEY_ALWAYS_FORCE_DARK, DEFAULT_ALWAYS_FORCE_DARK)
    var useLocation by this.shared(KEY_USE_LOCATION, DEFAULT_USE_LOCATION)
    var checkForUpdates by this.shared(KEY_CHECK_FOR_UPDATES, DEFAULT_CHECK_FOR_UPDATES)
    var checkForPrereleases by this.shared(KEY_CHECK_FOR_PRERELEASES, DEFAULT_CHECK_FOR_PRERELEASES)
    var developerOptions by this.shared(KEY_DEVELOPER_OPTIONS, DEFAULT_DEVELOPER_OPTIONS)
    var persistentService by this.shared(KEY_PERSISTENT_SERVICE, DEFAULT_PERSISTENT_SERVICE)
    var bootWaitShizuku by this.shared(KEY_BOOT_WAIT_SHIZUKU, DEFAULT_BOOT_WAIT_SHIZUKU)
    var autoDarkScheduleMode by this.shared(KEY_AUTO_DARK_SCHEDULE_MODE, DEFAULT_AUTO_DARK_SCHEDULE_MODE)
    var autoDarkTheme: Boolean
        get() = autoDarkScheduleMode != 0
        set(value) {
            if (!value) {
                autoDarkScheduleMode = 0
            } else if (autoDarkScheduleMode == 0) {
                autoDarkScheduleMode = 2
            }
        }
    var autoDarkTargetMode by this.shared(KEY_AUTO_DARK_TARGET_MODE, DEFAULT_AUTO_DARK_TARGET_MODE)
    var isDarqTargetOnly: Boolean
        get() = autoDarkTargetMode == 1
        set(value) { autoDarkTargetMode = if (value) 1 else 0 }
    var isCustomSchedule: Boolean
        get() = autoDarkScheduleMode == 2
        set(value) { autoDarkScheduleMode = if (value) 2 else 1 }
    var autoDarkStartTime by this.shared(KEY_AUTO_DARK_START_TIME, DEFAULT_AUTO_DARK_START_TIME)
    var autoDarkEndTime by this.shared(KEY_AUTO_DARK_END_TIME, DEFAULT_AUTO_DARK_END_TIME)
    // Auto Dark schedule state - true = currently in dark period, false = in light period
    var autoDarkManagedEnabled by this.shared(KEY_AUTO_DARK_MANAGED_ENABLED, DEFAULT_AUTO_DARK_MANAGED_ENABLED)
    // Saved system night mode before Auto Dark first took control (used to restore on disable)
    var preAutoDarkSystemNightMode by this.shared(KEY_PRE_AUTO_DARK_SYSTEM_NIGHT_MODE, false)
    // True once Auto Dark has fired at least once and saved a pre-state
    var autoDarkHasManaged by this.shared(KEY_AUTO_DARK_HAS_MANAGED, false)
    var monetColor by this.shared(KEY_UI_MONET_COLOR, Integer.MAX_VALUE)
    var enabledApps by this.sharedJSONArray(KEY_ENABLED_APPS)

    var xposedAggressiveDark by this.shared(KEY_XPOSED_AGGRESSIVE_DARK, DEFAULT_XPOSED_AGGRESSIVE_DARK)
    var xposedInvertStatus by this.shared(KEY_XPOSED_INVERT_STATUS_BAR, DEFAULT_XPOSED_INVERT_STATUS_BAR)

    fun getIPCSettingForKey(key: String): IPCSetting? {
        val xposed = XposedSelfHooks.isXposedModuleEnabled()
        return when(key){
            KEY_ENABLED -> IPCSetting(enabled = enabled, isXposedActive = xposed)
            KEY_ALWAYS_FORCE_DARK -> IPCSetting(alwaysForceDark = alwaysForceDark, isXposedActive = xposed)
            KEY_OXYGEN_FORCE_DARK -> IPCSetting(oxygenForceDark = oxygenForceDark, isXposedActive = xposed)
            KEY_SEND_APP_CLOSES -> IPCSetting(sendAppCloses = sendAppCloses, isXposedActive = xposed)
            else -> null
        }
    }

    fun toIPCSetting(): IPCSetting {
        return IPCSetting(
            enabled = enabled,
            autoDarkManagedEnabled = autoDarkManagedEnabled,
            oxygenForceDark = oxygenForceDark,
            alwaysForceDark = alwaysForceDark,
            sendAppCloses = sendAppCloses,
            isXposedActive = XposedSelfHooks.isXposedModuleEnabled()
        )
    }

    fun getSettingsBackup(): SettingsBackup {
        return SettingsBackup(
            enabled = this.enabled,
            autoDarkTheme = this.autoDarkTheme,
            useLocation = this.useLocation,
            sendAppCloses = this.sendAppCloses,
            oxygenForceDark = this.oxygenForceDark,
            alwaysForceDark = this.alwaysForceDark,
            developerOptions = this.developerOptions,
            monetColor = this.monetColor,
            xposedAggressiveDark = this.xposedAggressiveDark,
            xposedInvertStatus = this.xposedInvertStatus,
            checkForUpdates = this.checkForUpdates,
            checkForPrereleases = this.checkForPrereleases,
            persistentService = this.persistentService,
            bootWaitShizuku = this.bootWaitShizuku,
            autoDarkScheduleMode = this.autoDarkScheduleMode,
            autoDarkTargetMode = this.autoDarkTargetMode,
            autoDarkStartTime = this.autoDarkStartTime,
            autoDarkEndTime = this.autoDarkEndTime,
            enabledApps = this.enabledApps.toList()
        )
    }

    /**
     *  Restores from a given [SettingsBackup]
     *  @return Whether useLocation is set, and therefore to prompt for location permission
     */
    suspend fun fromSettingsBackup(settingsBackup: SettingsBackup): Boolean = withContext(Dispatchers.IO) {
        val currentUseLocation = useLocation && autoDarkTheme
        enabled = settingsBackup.enabled
        autoDarkTheme = settingsBackup.autoDarkTheme
        sendAppCloses = settingsBackup.sendAppCloses
        oxygenForceDark = settingsBackup.oxygenForceDark
        alwaysForceDark = settingsBackup.alwaysForceDark
        developerOptions = settingsBackup.developerOptions
        monetColor = settingsBackup.monetColor
        xposedAggressiveDark = settingsBackup.xposedAggressiveDark
        xposedInvertStatus = settingsBackup.xposedInvertStatus
        checkForUpdates = settingsBackup.checkForUpdates
        checkForPrereleases = settingsBackup.checkForPrereleases
        persistentService = settingsBackup.persistentService
        bootWaitShizuku = settingsBackup.bootWaitShizuku
        autoDarkScheduleMode = settingsBackup.autoDarkScheduleMode
        autoDarkTargetMode = settingsBackup.autoDarkTargetMode
        autoDarkStartTime = settingsBackup.autoDarkStartTime
        autoDarkEndTime = settingsBackup.autoDarkEndTime
        enabledApps = settingsBackup.enabledApps.toTypedArray()
        return@withContext (useLocation && autoDarkTheme) && !currentUseLocation
    }

    internal fun JSONArray.toStringArray(): Array<String> {
        return ArrayList<String>().apply {
            for (i in 0 until this@toStringArray.length()) {
                add(this@toStringArray.getString(i))
            }
        }.toTypedArray()
    }

}