package com.kieronquinn.app.darq.components.theme

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.kieronquinn.app.darq.R

// NOTE: Every settings icon is shown as a glyph on a 36dp circle, matching the round icons on the main screen.
// NOTE: Round icons (a layer-list of circle and glyph) are used as they are. Plain glyphs get a circle at runtime, colored from the classic palette by icon name so each icon keeps the same color.
// NOTE: Tinted icons recolor the circle and glyph of both kinds from the accent container colors.
object SettingsIcons {

    private val CLASSIC_CIRCLES = intArrayOf(
        R.color.icon_circle_0, R.color.icon_circle_1, R.color.icon_circle_2, R.color.icon_circle_3,
        R.color.icon_circle_4, R.color.icon_circle_5, R.color.icon_circle_7, R.color.icon_circle_8,
        R.color.icon_circle_9, R.color.icon_circle_12, R.color.icon_circle_14, R.color.icon_circle_15
    )

    fun load(context: Context, @DrawableRes icon: Int, tinted: Boolean): Drawable? {
        val drawable = ContextCompat.getDrawable(context, icon)?.mutate() ?: return null
        //The support heart sits in the same circle as every other icon, in the error container of the theme. Its glyph is a separate overlay so it can beat from its own center
        if (icon == R.drawable.ic_support_heart) return heartCircle(context)
        val round = (drawable as? LayerDrawable)?.takeIf { it.numberOfLayers >= 2 } ?: wrap(context, icon, drawable)
        if (tinted) {
            round.getDrawable(0).setTint(ThemeColors.getIconContainerColor(context))
            round.getDrawable(1).setTint(ThemeColors.getOnIconContainerColor(context))
        }
        return round
    }

    private fun heartCircle(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val circleSize = (36 * density).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorErrorContainer, Color.parseColor("#93000A")))
            setSize(circleSize, circleSize)
        }
    }

    /** The heart glyph of the Support row, tinted with the on-error-container role so it reads on the circle in light, dark and Pure black. */
    fun heartGlyph(context: Context): Drawable? {
        val glyph = ContextCompat.getDrawable(context, R.drawable.ic_support_heart)?.mutate() ?: return null
        glyph.setTint(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnErrorContainer, Color.parseColor("#FFDAD6")))
        return glyph
    }

    private fun wrap(context: Context, @DrawableRes icon: Int, glyph: Drawable): LayerDrawable {
        val density = context.resources.displayMetrics.density
        val circleSize = (36 * density).toInt()
        val glyphSize = (24 * density).toInt()
        val name = context.resources.getResourceEntryName(icon)
        val circleColor = CLASSIC_CIRCLES[Math.floorMod(name.hashCode(), CLASSIC_CIRCLES.size)]
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, circleColor))
            setSize(circleSize, circleSize)
        }
        return LayerDrawable(arrayOf(circle, glyph)).apply {
            setLayerSize(1, glyphSize, glyphSize)
            setLayerGravity(1, Gravity.CENTER)
        }
    }

}
