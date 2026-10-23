package com.kieronquinn.app.darq.components.theme

import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// NOTE: Reads the generated overlays, so a regenerated accent that breaks the contrast floor fails here.
// NOTE: Badge containers need 3 to 1 against the card and their text needs 4.5 to 1 (trademark blueprint, theme pillar).
class AccentContrastTest {

    private val styleRegex = Regex("""<style name="ThemeOverlay\.Darq\.Accent\.(\w+)"[^>]*>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)
    private val itemRegex = Regex("""<item name="([\w:]+)">#([0-9A-Fa-f]{6})</item>""")

    private fun load(path: String): Map<String, Map<String, Int>> {
        val xml = listOf(File(path), File("app/$path")).first { it.exists() }.readText()
        return styleRegex.findAll(xml).associate { style ->
            style.groupValues[1] to itemRegex.findAll(style.groupValues[2]).associate {
                it.groupValues[1] to (0xFF000000.toInt() or it.groupValues[2].toInt(16))
            }
        }
    }

    private fun check(name: String, roles: Map<String, Int>, surface: Int) {
        val label = "$name"
        fun role(key: String) = roles.getValue(key)
        assertTrue("$label primary on surface", ColorMath.contrast(role("colorPrimary"), surface) >= 3.0)
        assertTrue("$label text on primary", ColorMath.contrast(role("colorOnPrimary"), role("colorPrimary")) >= 4.5)
        assertTrue("$label text on primary container", ColorMath.contrast(role("colorOnPrimaryContainer"), role("colorPrimaryContainer")) >= 4.5)
        assertTrue("$label text on secondary container", ColorMath.contrast(role("colorOnSecondaryContainer"), role("colorSecondaryContainer")) >= 4.5)
        assertTrue("$label text on surface", ColorMath.contrast(role("colorOnSurface"), surface) >= 7.0)
    }

    @Test
    fun everyPresetKeepsTheContrastFloorInLightDarkAndPureBlack() {
        val light = load("src/main/res/values/theme_accents.xml")
        val night = load("src/main/res/values-night/theme_accents.xml")
        assertEquals(ThemeManager.Preset.values().size, light.size)
        assertEquals(light.keys, night.keys)
        for ((name, roles) in light) check("$name light", roles, roles.getValue("colorSurface"))
        for ((name, roles) in night) {
            check("$name dark", roles, roles.getValue("colorSurface"))
            check("$name pure black", roles, 0xFF000000.toInt())
        }
    }

    @Test
    fun signatureSeedsAreTheBlueprintColors() {
        assertEquals(0xFF0072DE.toInt(), ThemeManager.Preset.BLUE.seed)
        assertEquals(0xFFFF6D00.toInt(), ThemeManager.Preset.ORANGE.seed)
        assertEquals(0xFF00C853.toInt(), ThemeManager.Preset.GREEN.seed)
        assertEquals(0xFF7C4DFF.toInt(), ThemeManager.Preset.PURPLE.seed)
    }

    @Test
    fun storedPresetKeysFromEarlierReleasesStillResolve() {
        listOf("blue", "teal", "green", "purple", "orange", "rose", "mono").forEach {
            assertTrue("key $it", ThemeManager.Preset.fromKey(it) != null)
        }
    }

}
