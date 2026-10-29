package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

// NOTE: Builds the same tonal scheme Android builds from a seed, so the Light and Dark preview cards show what the app will really look like.
// NOTE: The color utilities ship inside Material Components as a restricted API, which is acceptable here because only these six roles are read.
object PreviewScheme {

    data class Colors(
        val surface: Int,
        val onSurface: Int,
        val container: Int,
        val onContainer: Int,
        val primary: Int,
        val onPrimary: Int
    )

    @SuppressLint("RestrictedApi")
    fun build(seed: Int, dark: Boolean): Colors {
        val scheme = SchemeTonalSpot(Hct.fromInt(seed or 0xFF000000.toInt()), dark, 0.0)
        val roles = MaterialDynamicColors()
        return Colors(
            surface = roles.surface().getArgb(scheme),
            onSurface = roles.onSurface().getArgb(scheme),
            container = roles.primaryContainer().getArgb(scheme),
            onContainer = roles.onPrimaryContainer().getArgb(scheme),
            primary = roles.primary().getArgb(scheme),
            onPrimary = roles.onPrimary().getArgb(scheme)
        )
    }

}
