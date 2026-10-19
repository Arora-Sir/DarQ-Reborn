package com.kieronquinn.app.darq.components.theme

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors

/**
 *  Reads the current Material 3 color roles from a themed context.
 *  The context must come from the activity (or a view inside it), since only the activity theme carries the accent and pure black overlays.
 */
object ThemeColors {

    fun getAccentColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLUE)

    fun getBackgroundColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)

    fun getBackgroundColorSecondary(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY)

    fun getContainerColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY)

    fun getSheetColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.DKGRAY)

    fun getOnSurfaceColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)

    fun getIconContainerColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.DKGRAY)

    fun getOnIconContainerColor(context: Context): Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.WHITE)

}
