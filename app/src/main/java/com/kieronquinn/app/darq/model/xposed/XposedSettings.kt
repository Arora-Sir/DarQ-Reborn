package com.kieronquinn.app.darq.model.xposed

data class XposedSettings(
    val enabled: Boolean,
    val isScheduleBlocking: Boolean = false,
    val aggressiveDark: Boolean? = null,
    val invertStatus: Boolean? = null
){

    // Force dark applies only while the app is selected (or always on) and the Auto Dark schedule is not in a light period.
    val isActive: Boolean
        get() = enabled && !isScheduleBlocking

    override fun toString(): String {
        return "XposedSettings enabled $enabled isScheduleBlocking $isScheduleBlocking aggressiveDark $aggressiveDark $invertStatus"
    }
}
