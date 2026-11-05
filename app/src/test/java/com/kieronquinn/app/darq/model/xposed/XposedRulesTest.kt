package com.kieronquinn.app.darq.model.xposed

import com.kieronquinn.app.darq.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedRulesTest {

    private val app = "com.example.app"

    private fun resolve(vararg values: Pair<String, Any?>) =
        XposedRules.resolve(FakeSharedPreferences(mapOf(*values)), app, EnabledAppsParser())

    @Test
    fun selectedAppIsActiveWithDefaults() {
        val settings = resolve(XposedPrefKeys.ENABLED_APPS to "[\"$app\"]")
        assertTrue(settings.isActive)
        assertEquals(true, settings.aggressiveDark)
        assertEquals(true, settings.invertStatus)
    }

    @Test
    fun unselectedAppIsInactiveUnlessAlwaysForceDark() {
        assertFalse(resolve(XposedPrefKeys.ENABLED_APPS to "[\"other\"]").isActive)
        assertTrue(resolve(XposedPrefKeys.ALWAYS_FORCE_DARK to true).isActive)
    }

    @Test
    fun darqDisabledWinsOverEverything() {
        assertFalse(resolve(XposedPrefKeys.ENABLED to false, XposedPrefKeys.ALWAYS_FORCE_DARK to true).isActive)
    }

    @Test
    fun scheduleBlocksOnlyInALightPeriod() {
        for (mode in 1..3) {
            assertFalse(resolve(XposedPrefKeys.ALWAYS_FORCE_DARK to true, XposedPrefKeys.AUTO_DARK_SCHEDULE_MODE to mode,
                XposedPrefKeys.AUTO_DARK_MANAGED_ENABLED to false).isActive)
            assertTrue(resolve(XposedPrefKeys.ALWAYS_FORCE_DARK to true, XposedPrefKeys.AUTO_DARK_SCHEDULE_MODE to mode,
                XposedPrefKeys.AUTO_DARK_MANAGED_ENABLED to true).isActive)
        }
        // Schedule off never blocks, even with a stale managed flag.
        assertTrue(resolve(XposedPrefKeys.ALWAYS_FORCE_DARK to true, XposedPrefKeys.AUTO_DARK_MANAGED_ENABLED to false).isActive)
    }

    @Test
    fun malformedOrMistypedValuesDisableInsteadOfCrashing() {
        assertFalse(resolve(XposedPrefKeys.ENABLED_APPS to "not json").isActive)
        assertFalse(resolve(XposedPrefKeys.ENABLED to "true").isActive)
    }

    @Test
    fun parserCachesByRawString() {
        val parser = EnabledAppsParser()
        val first = parser.parse("[\"a\",\"b\"]")
        assertTrue(first === parser.parse("[\"a\",\"b\"]"))
        assertEquals(setOf("c"), parser.parse("[\"c\"]"))
    }

}
