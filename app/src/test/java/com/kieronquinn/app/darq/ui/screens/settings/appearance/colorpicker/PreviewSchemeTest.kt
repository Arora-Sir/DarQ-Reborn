package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSchemeTest {

    private val seeds = listOf(0xFF2196F3, 0xFF00897B, 0xFF388E3C, 0xFF7E57C2, 0xFFEF6C00, 0xFFD81B60, 0xFF808080, 0xFF00BCD4).map { it.toInt() }

    @Test
    fun lightSurfaceIsBrighterThanDarkSurfaceForEverySeed() {
        for (seed in seeds) {
            val light = PreviewScheme.build(seed, dark = false)
            val dark = PreviewScheme.build(seed, dark = true)
            assertTrue(ColorMath.luminance(light.surface) > ColorMath.luminance(dark.surface))
            assertNotEquals(light.primary, dark.primary)
        }
    }

    @Test
    fun previewTextStaysReadableOnItsRoles() {
        for (seed in seeds) {
            for (dark in listOf(false, true)) {
                val colors = PreviewScheme.build(seed, dark)
                assertTrue("on primary $seed $dark", ColorMath.contrast(colors.onPrimary, colors.primary) >= 4.5)
                assertTrue("on container $seed $dark", ColorMath.contrast(colors.onContainer, colors.container) >= 4.5)
                assertTrue("on surface $seed $dark", ColorMath.contrast(colors.onSurface, colors.surface) >= 7.0)
            }
        }
    }

}
