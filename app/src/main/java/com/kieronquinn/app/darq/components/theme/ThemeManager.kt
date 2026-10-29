package com.kieronquinn.app.darq.components.theme

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.utils.extensions.isDarkTheme
import kotlin.math.abs
import kotlin.math.min

// NOTE: Applies the app appearance: light, dark or system mode, an accent, and pure black on top of dark.
// NOTE: Wallpaper and custom accents use Material dynamic colors, which need Android 12.
// NOTE: Presets are generated overlays (values/theme_accents.xml), so they look the same on every Android version.
// NOTE: Below Android 12 a wallpaper or custom accent falls back to the preset closest in hue, which keeps Android 10 and 11 colorful.
object ThemeManager {

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    const val ACCENT_WALLPAPER = "wallpaper"
    const val ACCENT_CUSTOM = "custom"

    private const val UNSET_COLOR = Integer.MAX_VALUE

    // NOTE: The keys are stored in user settings, so they stay the same when a preset gets a new color or name.
    // NOTE: The seeds are the canonical family shared by every Mohit Arora app (blueprint accent-presets.json), so the colors match across apps.
    enum class Preset(val key: String, val seed: Int, @StyleRes val overlay: Int) {
        BLUE("blue", 0xFF2196F3.toInt(), R.style.ThemeOverlay_Darq_Accent_Blue),
        TEAL("teal", 0xFF00897B.toInt(), R.style.ThemeOverlay_Darq_Accent_Teal),
        GREEN("green", 0xFF388E3C.toInt(), R.style.ThemeOverlay_Darq_Accent_Green),
        PURPLE("purple", 0xFF7E57C2.toInt(), R.style.ThemeOverlay_Darq_Accent_Purple),
        ORANGE("orange", 0xFFEF6C00.toInt(), R.style.ThemeOverlay_Darq_Accent_Orange),
        ROSE("rose", 0xFFD81B60.toInt(), R.style.ThemeOverlay_Darq_Accent_Rose),
        AMBER("amber", 0xFFFFB300.toInt(), R.style.ThemeOverlay_Darq_Accent_Amber),
        MONO("mono", 0xFF808080.toInt(), R.style.ThemeOverlay_Darq_Accent_Mono);

        companion object {
            fun fromKey(key: String) = values().firstOrNull { it.key == key }
        }
    }

    sealed class Accent {
        data class Dynamic(val seed: Int?) : Accent()
        data class Overlay(val preset: Preset) : Accent()
    }

    val isDynamicAvailable: Boolean
        get() = DynamicColors.isDynamicColorAvailable()

    fun nightModeFor(mode: Int) = when (mode) {
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun applyNightMode(settings: DarqSharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(settings.themeMode))
    }

    /**
     *  Resolves the accent this device can show for the stored choice.
     *  [wallpaperColor] is only read below Android 12, where it picks the closest preset.
     */
    fun resolveAccent(accent: String, customColor: Int, dynamicAvailable: Boolean, wallpaperColor: () -> Int?): Accent {
        Preset.fromKey(accent)?.let { return Accent.Overlay(it) }
        val custom = customColor.takeIf { accent == ACCENT_CUSTOM && it != UNSET_COLOR }
        if (dynamicAvailable) {
            return Accent.Dynamic(custom)
        }
        val source = custom ?: wallpaperColor.invoke()
        return Accent.Overlay(source?.let { nearestPreset(it) } ?: Preset.BLUE)
    }

    /**
     *  Closest preset by hue. Low saturation colors map to Mono, since a hue taken from a grey is meaningless.
     */
    fun nearestPreset(color: Int): Preset {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < 0.15f) return Preset.MONO
        return Preset.values().filter { it != Preset.MONO }.minByOrNull {
            val presetHsv = FloatArray(3)
            Color.colorToHSV(it.seed, presetHsv)
            val distance = abs(presetHsv[0] - hsv[0])
            min(distance, 360f - distance)
        } ?: Preset.BLUE
    }

    fun wallpaperPrimaryColor(context: Context): Int? = try {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    } catch (e: Exception) {
        null
    }

    /**
     *  The colors offered as custom accents: the secondary and tertiary wallpaper colors.
     *  The primary color is left out because the Wallpaper accent already follows it.
     */
    fun wallpaperColors(context: Context): List<Int> = try {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let {
            val primary = it.primaryColor.toArgb()
            listOfNotNull(it.secondaryColor, it.tertiaryColor).map { color -> color.toArgb() }.distinct().filter { color -> color != primary }
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     *  A string describing everything this class applies, so an activity can tell that the appearance changed while it was in the background.
     */
    fun signature(settings: DarqSharedPreferences): String = with(settings) {
        "$themeAccent:$monetColor:$themePureBlack:$themeTintedIcons"
    }

    /**
     *  Applies the accent and pure black to [activity]. Call it before super.onCreate, after the night mode is set.
     *  @return the signature that was applied
     */
    fun apply(activity: Activity, settings: DarqSharedPreferences): String {
        val accent = resolveAccent(settings.themeAccent, settings.monetColor, isDynamicAvailable) {
            wallpaperPrimaryColor(activity)
        }
        when (accent) {
            is Accent.Overlay -> activity.theme.applyStyle(accent.preset.overlay, true)
            is Accent.Dynamic -> {
                //The blue overlay is the base in case the dynamic engine declines this device
                activity.theme.applyStyle(Preset.BLUE.overlay, true)
                val options = DynamicColorsOptions.Builder().apply {
                    accent.seed?.let { setContentBasedSource(it) }
                }.build()
                DynamicColors.applyToActivityIfAvailable(activity, options)
            }
        }
        if (settings.themePureBlack && activity.isDarkTheme) {
            activity.theme.applyStyle(R.style.ThemeOverlay_Darq_PureBlack, true)
        }
        return signature(settings)
    }

}
