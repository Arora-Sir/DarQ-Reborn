package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kieronquinn.app.darq.databinding.ItemColorPickerBinding

class ColorPickerAdapter(
    context: Context,
    private val options: List<AccentOption>,
    private val isSelected: (AccentOption) -> Boolean,
    private val onPicked: (AccentOption) -> Unit
): RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>() {

    data class AccentOption(val accent: String, val color: Int, val label: String, val customColor: Int? = null)

    private val layoutInflater = LayoutInflater.from(context)

    override fun getItemCount() = options.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemColorPickerBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        with(holder.binding){
            itemColorPickerBackground.backgroundTintList = ColorStateList.valueOf(option.color)
            itemColorPickerBackground.contentDescription = option.label
            itemColorPickerBackground.tooltipText = option.label
            itemColorPickerCheck.isVisible = isSelected(option)
            //The check mark stays readable on both light and dark swatches
            val checkColor = if(ColorUtils.calculateLuminance(option.color) > 0.5) Color.BLACK else Color.WHITE
            itemColorPickerCheck.imageTintList = ColorStateList.valueOf(checkColor)
            itemColorPickerBackground.setOnClickListener {
                onPicked(option)
            }
        }
    }

    data class ViewHolder(val binding: ItemColorPickerBinding): RecyclerView.ViewHolder(binding.root)

}
