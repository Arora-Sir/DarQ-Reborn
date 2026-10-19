package com.kieronquinn.app.darq.components.xposed

import com.kieronquinn.app.darq.model.xposed.XposedPrefKeys
import com.kieronquinn.app.darq.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedPrefsSyncTest {

    @Test
    fun writeAllCopiesEveryHookKeyWithItsTypeAndDefaults() {
        val local = FakeSharedPreferences(mapOf(
            XposedPrefKeys.ENABLED to false,
            XposedPrefKeys.AUTO_DARK_SCHEDULE_MODE to 2,
            XposedPrefKeys.ENABLED_APPS to "[\"x\"]",
            "monet_color" to 5
        ))
        val remote = FakeSharedPreferences()
        remote.edit().also { XposedPrefsSync.writeAll(local, it) }.apply()
        assertEquals(XposedPrefKeys.ALL, remote.values.keys)
        assertEquals(false, remote.values[XposedPrefKeys.ENABLED])
        assertEquals(2, remote.values[XposedPrefKeys.AUTO_DARK_SCHEDULE_MODE])
        assertEquals("[\"x\"]", remote.values[XposedPrefKeys.ENABLED_APPS])
        assertEquals(XposedPrefKeys.DEFAULT_AGGRESSIVE_DARK, remote.values[XposedPrefKeys.AGGRESSIVE_DARK])
    }

    @Test
    fun writeKeyIgnoresKeysTheHooksNeverRead() {
        val remote = FakeSharedPreferences()
        val editor = remote.edit()
        assertFalse(XposedPrefsSync.writeKey(FakeSharedPreferences(mapOf("monet_color" to 5)), editor, "monet_color"))
        assertTrue(XposedPrefsSync.writeKey(FakeSharedPreferences(), editor, XposedPrefKeys.INVERT_STATUS_BAR))
        editor.apply()
        assertEquals(setOf(XposedPrefKeys.INVERT_STATUS_BAR), remote.values.keys)
    }

}
