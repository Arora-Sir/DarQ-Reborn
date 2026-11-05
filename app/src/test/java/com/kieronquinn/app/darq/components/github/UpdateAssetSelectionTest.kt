package com.kieronquinn.app.darq.components.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// NOTE: The in-app updater once downloaded the DarQ Probe APK because GitHub listed it before the DarQ APK, so the choice is pinned by name here.
class UpdateAssetSelectionTest {

    private fun assets(vararg names: String) = names.map { name -> Asset().apply { this.name = name } }

    @Test
    fun darqApkWinsWhenTheProbeIsListedFirst() {
        val list = assets("DarQ-Probe.3.2.0.apk", "DarQ-Probe.3.2.0.apk.sha256", "DarQ-Reborn.3.2.0.apk", "DarQ-Reborn.3.2.0.apk.sha256")
        assertEquals("DarQ-Reborn.3.2.0.apk", UpdateChecker.pickApkAsset(list)?.name)
    }

    @Test
    fun checksumFilesAreNeverPicked() {
        val list = assets("DarQ-Reborn.3.2.0.apk.sha256", "DarQ-Reborn.3.2.0.apk")
        assertEquals("DarQ-Reborn.3.2.0.apk", UpdateChecker.pickApkAsset(list)?.name)
    }

    @Test
    fun anotherApkNameStillWorksWhenItIsNotAProbe() {
        assertEquals("DarQ_Release.apk", UpdateChecker.pickApkAsset(assets("DarQ_Release.apk"))?.name)
    }

    @Test
    fun aProbeAloneIsNeverInstalledAsTheUpdate() {
        assertNull(UpdateChecker.pickApkAsset(assets("Probe-for-DarQ.3.2.0.apk", "DarQ-Probe.3.2.0.apk")))
    }

    @Test
    fun noAssetsGivesNothing() {
        assertNull(UpdateChecker.pickApkAsset(null))
        assertNull(UpdateChecker.pickApkAsset(emptyList()))
    }

}
