package com.kieronquinn.app.darq.model.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class ForceDarkMappingTest {

    @Test
    fun darkSystemForcesDarkForEveryNonInvertType() {
        assertEquals(ForceDarkMapping.TYPE_FORCE_DARK, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_NONE, isSystemDark = true))
        assertEquals(ForceDarkMapping.TYPE_FORCE_DARK, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_FORCE_DARK, isSystemDark = true))
    }

    @Test
    fun lightSystemClearsForceDark() {
        assertEquals(ForceDarkMapping.TYPE_NONE, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_FORCE_DARK, isSystemDark = false))
        assertEquals(ForceDarkMapping.TYPE_NONE, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_NONE, isSystemDark = false))
    }

    @Test
    fun accessibilityColorInversionIsNeverOverridden() {
        assertEquals(ForceDarkMapping.TYPE_FORCE_INVERT_COLOR_DARK, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_FORCE_INVERT_COLOR_DARK, isSystemDark = true))
        assertEquals(ForceDarkMapping.TYPE_FORCE_INVERT_COLOR_DARK, ForceDarkMapping.mapType(ForceDarkMapping.TYPE_FORCE_INVERT_COLOR_DARK, isSystemDark = false))
    }

    @Test
    fun booleanVariantFollowsSystemDark() {
        assertEquals(true, ForceDarkMapping.mapBoolean(isSystemDark = true))
        assertEquals(false, ForceDarkMapping.mapBoolean(isSystemDark = false))
    }

    @Test
    fun constantsMatchAospForceDarkType() {
        // AOSP graphics/java/android/graphics/ForceDarkType.java (Android 15 and later).
        assertEquals(0, ForceDarkMapping.TYPE_NONE)
        assertEquals(1, ForceDarkMapping.TYPE_FORCE_DARK)
        assertEquals(2, ForceDarkMapping.TYPE_FORCE_INVERT_COLOR_DARK)
    }

}
