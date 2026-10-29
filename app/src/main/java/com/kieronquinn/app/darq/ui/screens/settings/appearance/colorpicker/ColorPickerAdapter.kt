package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.ItemColorPickerBinding
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback

// NOTE: The palette follows the shared color UI standard of the Mohit Arora apps, equal tiles in rows of five with a name under every swatch.
// NOTE: The selected tile gets a tinted background and a small round tick badge on the corner of its swatch, never a disc over the color.
// NOTE: The wallpaper tile shows a wallpaper icon on a neutral circle and the custom tile shows a rainbow ring around the current custom color.
class ColorPickerAdapter(
    private val context: Context,
    private val options: List<AccentOption>,
    private val isSelected: (AccentOption) -> Boolean,
    private val onPicked: (AccentOption) -> Unit
): RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>() {

    enum class Kind { WALLPAPER, PRESET, CUSTOM }

    data class AccentOption(
        val accent: String,
        val color: Int,
        val label: String,
        val customColor: Int? = null,
        val kind: Kind = Kind.PRESET
    ) {
        //The custom tile opens the picker instead of previewing a color
        val isPicker get() = kind == Kind.CUSTOM
    }

    private val layoutInflater = LayoutInflater.from(context)
    private val density = context.resources.displayMetrics.density

    override fun getItemCount() = options.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemColorPickerBinding.inflate(layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.binding) {
        val option = options[position]
        val selected = isSelected(option)
        itemColorPickerLabel.text = option.label
        itemColorPickerTile.contentDescription = option.label
        itemColorPickerTile.isSelected = selected
        bindSwatch(this, option)
        itemColorPickerTile.background = if (selected) selectedBackground(option.color) else null
        //The badge color is a light version of the swatch, so it reads on the swatch and on the tinted tile
        itemColorPickerCheck.isVisible = selected
        itemColorPickerCheck.backgroundTintList = ColorStateList.valueOf(ColorUtils.blendARGB(option.color, Color.WHITE, 0.7f))
        itemColorPickerCheck.imageTintList = ColorStateList.valueOf(Color.BLACK)
        itemColorPickerTile.setOnClickListener { onPicked(option) }
        itemColorPickerTile.applyPressFeedback()
    }

    private fun bindSwatch(binding: ItemColorPickerBinding, option: AccentOption) = with(binding) {
        when (option.kind) {
            Kind.WALLPAPER -> {
                //A neutral circle with the wallpaper icon, since the real wallpaper color changes with the device
                itemColorPickerBackground.background = ContextCompat.getDrawable(context, R.drawable.monet_color_picker_circle_background)
                itemColorPickerBackground.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.GRAY))
                itemColorPickerIcon.isVisible = true
                itemColorPickerIcon.imageTintList = ColorStateList.valueOf(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.WHITE))
            }
            Kind.CUSTOM -> {
                itemColorPickerIcon.isVisible = false
                itemColorPickerBackground.backgroundTintList = null
                itemColorPickerBackground.background = rainbowRing(option.color)
            }
            Kind.PRESET -> {
                itemColorPickerIcon.isVisible = false
                itemColorPickerBackground.background = ContextCompat.getDrawable(context, R.drawable.monet_color_picker_circle_background)
                itemColorPickerBackground.backgroundTintList = ColorStateList.valueOf(option.color)
            }
        }
    }

    // NOTE: A sweep gradient ring with the current custom color inside it, so the tile advertises that it opens a picker.
    private fun rainbowRing(inner: Int): LayerDrawable {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        }
        val core = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(inner)
        }
        val inset = (6 * density).toInt()
        return LayerDrawable(arrayOf(ring, core)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    private fun selectedBackground(color: Int) =
        ContextCompat.getDrawable(context, R.drawable.color_tile_selected)!!.mutate().also {
            DrawableCompat.setTint(it, ColorUtils.setAlphaComponent(color, 64))
        }

    class ViewHolder(val binding: ItemColorPickerBinding) : RecyclerView.ViewHolder(binding.root)

}
