package com.kieronquinn.app.darq.model.settings

data class SettingsBackup(
    val enabled: Boolean,
    val autoDarkTheme: Boolean,
    //useLocation is backed up but not restored automatically - it shows a prompt first
    val useLocation: Boolean,
    val sendAppCloses: Boolean,
    val oxygenForceDark: Boolean,
    val alwaysForceDark: Boolean,
    val developerOptions: Boolean,
    val monetColor: Int,
    val xposedAggressiveDark: Boolean,
    val xposedInvertStatus: Boolean,
    val checkForUpdates: Boolean = true,
    val checkForPrereleases: Boolean = false,
    val persistentService: Boolean = false,
    val bootWaitShizuku: Boolean = true,
    val autoDarkScheduleMode: Int = 0,
    val autoDarkTargetMode: Int = 0,
    val autoDarkStartTime: Int = 1200,
    val autoDarkEndTime: Int = 420,
    val enabledApps: List<String>,
    //Nullable because Gson leaves a missing field null and ignores Kotlin defaults, so older backups restore without touching the theme
    val themeMode: Int? = null,
    val themePureBlack: Boolean? = null,
    val themePureBlackLift: Int? = null,
    val themeAccent: String? = null,
    val themeTintedIcons: Boolean? = null
)