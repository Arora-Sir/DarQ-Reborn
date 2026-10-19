package com.kieronquinn.app.darq.components.theme

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.MaterialColors
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.theme.ThemeManager.Accent
import com.kieronquinn.app.darq.components.theme.ThemeManager.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeManagerTest {

    private val unset = Integer.MAX_VALUE
    private val noWallpaper: () -> Int? = { null }

    @Test
    fun presetKeysAlwaysUseTheirOverlay() {
        for (preset in Preset.values()) {
            assertEquals(Accent.Overlay(preset), ThemeManager.resolveAccent(preset.key, unset, true, noWallpaper))
            assertEquals(Accent.Overlay(preset), ThemeManager.resolveAccent(preset.key, unset, false, noWallpaper))
        }
    }

    @Test
    fun wallpaperUsesDynamicColorsWhenAvailable() {
        assertEquals(Accent.Dynamic(null), ThemeManager.resolveAccent(ThemeManager.ACCENT_WALLPAPER, unset, true, noWallpaper))
    }

    @Test
    fun wallpaperBelowAndroid12FallsBackToTheClosestPreset() {
        val green = Color.rgb(40, 160, 60)
        assertEquals(Accent.Overlay(Preset.GREEN), ThemeManager.resolveAccent(ThemeManager.ACCENT_WALLPAPER, unset, false) { green })
        assertEquals(Accent.Overlay(Preset.BLUE), ThemeManager.resolveAccent(ThemeManager.ACCENT_WALLPAPER, unset, false, noWallpaper))
    }

    @Test
    fun customColorSeedsDynamicColorsOrFallsBack() {
        val orange = Color.rgb(240, 110, 0)
        assertEquals(Accent.Dynamic(orange), ThemeManager.resolveAccent(ThemeManager.ACCENT_CUSTOM, orange, true, noWallpaper))
        assertEquals(Accent.Overlay(Preset.ORANGE), ThemeManager.resolveAccent(ThemeManager.ACCENT_CUSTOM, orange, false, noWallpaper))
        // A custom accent with no stored color behaves like the wallpaper accent.
        assertEquals(Accent.Dynamic(null), ThemeManager.resolveAccent(ThemeManager.ACCENT_CUSTOM, unset, true, noWallpaper))
    }

    @Test
    fun unknownAccentIsTreatedAsWallpaper() {
        assertEquals(Accent.Dynamic(null), ThemeManager.resolveAccent("removed-preset", unset, true, noWallpaper))
    }

    @Test
    fun greysMapToMonoAndHuesWrapAround() {
        assertEquals(Preset.MONO, ThemeManager.nearestPreset(Color.rgb(120, 120, 125)))
        // Hue 350 is closer to rose (about 340) than to orange, across the 0/360 boundary.
        assertEquals(Preset.ROSE, ThemeManager.nearestPreset(Color.HSVToColor(floatArrayOf(350f, 0.8f, 0.8f))))
    }

    @Test
    fun overlaysChangeThePrimaryColor() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val primaries = Preset.values().map { primaryOf(themed(base, it.overlay)) }
        assertEquals(primaries.size, primaries.distinct().size)
    }

    @Test
    @Config(qualifiers = "night")
    fun pureBlackTurnsSurfacesBlack() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val dark = themed(base, Preset.BLUE.overlay)
        assertNotEquals(Color.BLACK, surfaceOf(dark))
        dark.theme.applyStyle(R.style.ThemeOverlay_Darq_PureBlack, true)
        assertEquals(Color.BLACK, surfaceOf(dark))
    }

    private fun themed(base: Context, overlay: Int) = ContextThemeWrapper(base, R.style.AppTheme).apply {
        theme.applyStyle(overlay, true)
    }

    private fun primaryOf(context: Context) =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT)

    private fun surfaceOf(context: Context) =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT)

}
