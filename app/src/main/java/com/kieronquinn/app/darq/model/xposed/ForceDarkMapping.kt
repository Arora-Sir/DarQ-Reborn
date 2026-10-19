package com.kieronquinn.app.darq.model.xposed

// NOTE: HardwareRenderer.setForceDark took a boolean up to Android 14 and takes a ForceDarkType int from Android 15.
// NOTE: ForceDarkType is a hidden framework class, so its constants are mirrored here and must match AOSP (NONE = 0, FORCE_DARK = 1, FORCE_INVERT_COLOR_DARK = 2).
object ForceDarkMapping {

    const val TYPE_NONE = 0
    const val TYPE_FORCE_DARK = 1
    const val TYPE_FORCE_INVERT_COLOR_DARK = 2

    // The accessibility color inversion type is always passed through, because it is a user accessibility choice and not app theming.
    fun mapType(incomingType: Int, isSystemDark: Boolean): Int {
        if (incomingType == TYPE_FORCE_INVERT_COLOR_DARK) return incomingType
        return if (isSystemDark) TYPE_FORCE_DARK else TYPE_NONE
    }

    fun mapBoolean(isSystemDark: Boolean): Boolean = isSystemDark

}
