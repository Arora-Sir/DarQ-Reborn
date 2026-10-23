package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun hexParsingAcceptsSixAndThreeDigitFormsInAnyCase() {
        assertEquals(0xFF0072DE.toInt(), ColorMath.parseHex("#0072DE"))
        assertEquals(0xFF0072DE.toInt(), ColorMath.parseHex("0072de"))
        assertEquals(0xFFFFEE00.toInt(), ColorMath.parseHex("#fe0"))
        assertEquals(0xFF000000.toInt(), ColorMath.parseHex("  #000000 "))
    }

    @Test
    fun hexParsingRejectsBadInput() {
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#1234567"))
        assertNull(ColorMath.parseHex("#GG0000"))
        assertNull(ColorMath.parseHex("red"))
    }

    @Test
    fun hexOutputIsUppercaseWithoutAlpha() {
        assertEquals("#00C853", ColorMath.toHex(0xFF00C853.toInt()))
        assertEquals("#000001", ColorMath.toHex(0x80000001.toInt()))
    }

    @Test
    fun sanitizingKeepsHexDigitsAndPrefixesAHash() {
        assertEquals("#12AB34", ColorMath.sanitizeHexInput("color: #12ab34"))
        assertEquals("#", ColorMath.sanitizeHexInput(""))
        assertEquals("#ABCDEF", ColorMath.sanitizeHexInput("abcdefabc"))
    }

    @Test
    fun hsvConversionMatchesKnownColors() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToColor(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColorMath.hsvToColor(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hsvToColor(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.hsvToColor(200f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), ColorMath.hsvToColor(200f, 1f, 0f))
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToColor(360f, 1f, 1f))
    }

    @Test
    fun hsvRoundTripKeepsTheColor() {
        for (color in ColorMath.PALETTE) {
            val hsv = ColorMath.colorToHsv(color)
            assertEquals(color, ColorMath.hsvToColor(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test
    fun greyHasNoSaturation() {
        val hsv = ColorMath.colorToHsv(0xFF808080.toInt())
        assertEquals(0f, hsv[1], 0.0001f)
        assertEquals(0.502f, hsv[2], 0.001f)
    }

    @Test
    fun contrastRatiosFollowWcag() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0001)
        assertEquals(0xFF000000.toInt(), ColorMath.onColor(0xFFFFEB3B.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.onColor(0xFF673AB7.toInt()))
    }

    @Test
    fun paletteHasTwentyFourDistinctColorsIncludingTheSignatureAccents() {
        assertEquals(24, ColorMath.PALETTE.size)
        assertEquals(24, ColorMath.PALETTE.distinct().size)
        listOf(0xFF0072DE, 0xFFFF6D00, 0xFF00C853, 0xFF7C4DFF).forEach {
            assertTrue(ColorMath.PALETTE.contains(it.toInt()))
        }
    }

}
