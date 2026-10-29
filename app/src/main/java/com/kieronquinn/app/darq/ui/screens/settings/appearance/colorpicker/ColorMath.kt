package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  Color helpers for the custom accent picker, free of Android types so they run in JVM tests and copy to other apps unchanged.
 *  Colors are opaque ARGB ints. Hue is 0 to 360, saturation and value are 0 to 1.
 */
object ColorMath {

    //The seed the custom picker starts from and returns to on Reset, the same in every Mohit Arora app
    val DEFAULT_CUSTOM: Int = 0xFF00BCD4.toInt()

    private val HEX = Regex("^#?([0-9a-fA-F]{6})$")
    private val HEX_SHORT = Regex("^#?([0-9a-fA-F]{3})$")

    /**
     *  Accepts #RRGGBB, RRGGBB, #RGB and RGB in any case. Returns null for anything else.
     */
    fun parseHex(input: String): Int? {
        val text = input.trim()
        HEX.matchEntire(text)?.let { return 0xFF000000.toInt() or it.groupValues[1].toInt(16) }
        HEX_SHORT.matchEntire(text)?.let {
            val expanded = it.groupValues[1].map { c -> "$c$c" }.joinToString("")
            return 0xFF000000.toInt() or expanded.toInt(16)
        }
        return null
    }

    fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    /**
     *  Keeps only characters a hex field can use, so pasted text such as "color: #12ab34" still works while typing.
     */
    fun sanitizeHexInput(input: String): String {
        //Pasted text such as "color: #12ab34" keeps only what follows the hash, so the letters of the word do not count as digits
        val source = if ('#' in input) input.substringAfterLast('#') else input
        val digits = source.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
        return "#$digits"
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return 0xFF000000.toInt() or
            ((r + m) * 255f).roundToInt().coerceIn(0, 255).shl(16) or
            ((g + m) * 255f).roundToInt().coerceIn(0, 255).shl(8) or
            ((b + m) * 255f).roundToInt().coerceIn(0, 255)
    }

    fun colorToHsv(color: Int): FloatArray {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (maxC == 0f) 0f else delta / maxC
        return floatArrayOf(hue, saturation, maxC)
    }

    /**
     *  WCAG contrast ratio between two opaque colors, from 1 to 21.
     */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color shr 16 and 0xFF) + 0.7152 * channel(color shr 8 and 0xFF) + 0.0722 * channel(color and 0xFF)
    }

    /**
     *  Black or white, whichever reads better on [background].
     */
    fun onColor(background: Int): Int =
        if (contrast(background, 0xFF000000.toInt()) >= contrast(background, 0xFFFFFFFF.toInt())) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

}
