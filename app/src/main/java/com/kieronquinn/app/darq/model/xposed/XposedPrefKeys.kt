package com.kieronquinn.app.darq.model.xposed

// NOTE: The app writes these keys and every hooked process reads them, so names and defaults must never drift between the two sides.
// NOTE: The key strings are the same ones DarqSharedPreferences has always used, which keeps existing installs and backups compatible.
object XposedPrefKeys {

    // Remote preferences group the app writes through XposedService and the hooks read through getRemotePreferences.
    const val GROUP = "darq_xposed"

    const val ENABLED = "enabled"
    const val DEFAULT_ENABLED = true

    const val ALWAYS_FORCE_DARK = "always_force_dark"
    const val DEFAULT_ALWAYS_FORCE_DARK = false

    // 0 = Off, 1 = Sunset/Sunrise, 2 = Custom Time, 3 = Follow System Theme.
    const val AUTO_DARK_SCHEDULE_MODE = "auto_dark_schedule_mode"
    const val DEFAULT_AUTO_DARK_SCHEDULE_MODE = 0

    // Whether the Auto Dark schedule is currently in a dark period. Only the schedule writes it.
    const val AUTO_DARK_MANAGED_ENABLED = "auto_dark_managed_enabled"
    const val DEFAULT_AUTO_DARK_MANAGED_ENABLED = true

    // Stored as a JSON array string, matching the app's local preferences file.
    const val ENABLED_APPS = "enabled_apps"
    const val DEFAULT_ENABLED_APPS = "[]"

    const val AGGRESSIVE_DARK = "xposed_aggressive_dark"
    const val DEFAULT_AGGRESSIVE_DARK = true

    const val INVERT_STATUS_BAR = "xposed_invert_status_bar"
    const val DEFAULT_INVERT_STATUS_BAR = true

    val BOOLEAN_KEYS = mapOf(
        ENABLED to DEFAULT_ENABLED,
        ALWAYS_FORCE_DARK to DEFAULT_ALWAYS_FORCE_DARK,
        AUTO_DARK_MANAGED_ENABLED to DEFAULT_AUTO_DARK_MANAGED_ENABLED,
        AGGRESSIVE_DARK to DEFAULT_AGGRESSIVE_DARK,
        INVERT_STATUS_BAR to DEFAULT_INVERT_STATUS_BAR
    )

    val INT_KEYS = mapOf(
        AUTO_DARK_SCHEDULE_MODE to DEFAULT_AUTO_DARK_SCHEDULE_MODE
    )

    val STRING_KEYS = mapOf(
        ENABLED_APPS to DEFAULT_ENABLED_APPS
    )

    val ALL: Set<String> = BOOLEAN_KEYS.keys + INT_KEYS.keys + STRING_KEYS.keys

}
