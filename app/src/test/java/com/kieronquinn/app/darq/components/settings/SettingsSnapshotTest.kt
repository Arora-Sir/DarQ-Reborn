package com.kieronquinn.app.darq.components.settings

import com.kieronquinn.app.darq.model.settings.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSnapshotTest {

    private val backup = SettingsBackup(
        enabled = true,
        autoDarkTheme = true,
        useLocation = false,
        sendAppCloses = false,
        oxygenForceDark = false,
        alwaysForceDark = true,
        developerOptions = false,
        monetColor = Integer.MAX_VALUE,
        xposedAggressiveDark = true,
        xposedInvertStatus = false,
        persistentService = true,
        autoDarkScheduleMode = 2,
        autoDarkTargetMode = 1,
        autoDarkStartTime = 1260,
        autoDarkEndTime = 400,
        enabledApps = listOf("com.example.one", "com.example.two")
    )

    @Test
    fun noSnapshotNeverRestores() {
        assertFalse(SettingsSnapshot.shouldRestore(preferencesRevision = 0, snapshotRevision = null))
    }

    @Test
    fun emptyPreferencesRestoreAnySnapshot() {
        assertTrue(SettingsSnapshot.shouldRestore(preferencesRevision = 0, snapshotRevision = 1))
    }

    @Test
    fun stalePreferencesRestoreNewerSnapshot() {
        // LSPosed module toggled back on: the redirected folder returns with an older revision.
        assertTrue(SettingsSnapshot.shouldRestore(preferencesRevision = 10, snapshotRevision = 15))
    }

    @Test
    fun currentOrNewerPreferencesAreKept() {
        assertFalse(SettingsSnapshot.shouldRestore(preferencesRevision = 15, snapshotRevision = 15))
        assertFalse(SettingsSnapshot.shouldRestore(preferencesRevision = 16, snapshotRevision = 15))
    }

    @Test
    fun encodeDecodeRoundTripKeepsEverySetting() {
        val stored = SettingsSnapshot.Stored(revision = 42, settings = backup)
        assertEquals(stored, SettingsSnapshot.decode(SettingsSnapshot.encode(stored)))
    }

    @Test
    fun snapshotFromBeforeTheThemeSystemLeavesThemeFieldsUnset() {
        val stored = SettingsSnapshot.Stored(revision = 7, settings = backup)
        val legacyJson = SettingsSnapshot.encode(stored)
            .replace(Regex(""",\s*"theme[A-Za-z]+"\s*:\s*[^,}]+"""), "")
        val decoded = SettingsSnapshot.decode(legacyJson)!!.settings
        assertNull(decoded.themeMode)
        assertNull(decoded.themePureBlack)
        assertNull(decoded.themeAccent)
        assertNull(decoded.themeTintedIcons)
        assertEquals(backup.enabledApps, decoded.enabledApps)
    }

    @Test
    fun themeFieldsRoundTrip() {
        val themed = backup.copy(themeMode = 2, themePureBlack = true, themeAccent = "teal", themeTintedIcons = true)
        val stored = SettingsSnapshot.Stored(revision = 8, settings = themed)
        assertEquals(themed, SettingsSnapshot.decode(SettingsSnapshot.encode(stored))!!.settings)
    }

    @Test
    fun corruptSnapshotIsIgnored() {
        assertNull(SettingsSnapshot.decode("{not json"))
        assertNull(SettingsSnapshot.decode(""))
    }

    @Test
    fun incompleteSnapshotIsIgnored() {
        // Gson leaves missing non-null Kotlin fields as null, which would crash a restore.
        assertNull(SettingsSnapshot.decode("""{"revision":3,"settings":{"enabled":true}}"""))
        assertNull(SettingsSnapshot.decode("""{"revision":3}"""))
    }

}
