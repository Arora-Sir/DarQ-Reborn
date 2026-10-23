package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import com.kieronquinn.app.darq.databinding.ItemColorPickerBinding
import com.kieronquinn.app.darq.databinding.ItemColorPickerHeaderBinding

/**
 *  Swatches grouped under section headers. Headers span the whole row and every swatch shows its name below it.
 */
class ColorPickerAdapter(
    context: Context,
    private val rows: List<Row>,
    private val isSelected: (AccentOption) -> Boolean,
    private val onPicked: (AccentOption) -> Unit
): RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>() {

    data class AccentOption(val accent: String, val color: Int, val label: String, val customColor: Int? = null, val isPicker: Boolean = false)

    sealed class Row {
        data class Header(val title: String) : Row()
        data class Swatch(val option: AccentOption) : Row()
    }

    private val layoutInflater = LayoutInflater.from(context)

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) TYPE_HEADER else TYPE_SWATCH

    fun spanSizeLookup(spanCount: Int) = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = if (rows[position] is Row.Header) spanCount else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = if (viewType == TYPE_HEADER) {
        ViewHolder.Header(ItemColorPickerHeaderBinding.inflate(layoutInflater, parent, false))
    } else {
        ViewHolder.Swatch(ItemColorPickerBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as ViewHolder.Header).binding.itemColorPickerHeader.text = row.title
            is Row.Swatch -> bindSwatch((holder as ViewHolder.Swatch).binding, row.option)
        }
    }

    private fun bindSwatch(binding: ItemColorPickerBinding, option: AccentOption) = with(binding) {
        itemColorPickerBackground.backgroundTintList = ColorStateList.valueOf(option.color)
        itemColorPickerBackground.contentDescription = option.label
        itemColorPickerLabel.text = option.label
        //The picker swatch opens the full custom picker and shows a color dropper instead of a check
        itemColorPickerCheck.setImageResource(if (option.isPicker) R.drawable.ic_colorize else R.drawable.ic_check)
        itemColorPickerCheck.isVisible = option.isPicker || isSelected(option)
        //The check mark stays readable on both light and dark swatches
        val checkColor = if (ColorUtils.calculateLuminance(option.color) > 0.5) Color.BLACK else Color.WHITE
        itemColorPickerCheck.imageTintList = ColorStateList.valueOf(checkColor)
        itemColorPickerBackground.setOnClickListener { onPicked(option) }
        itemColorPickerBackground.applyPressFeedback()
    }

    sealed class ViewHolder(binding: ViewBinding): RecyclerView.ViewHolder(binding.root) {
        class Header(val binding: ItemColorPickerHeaderBinding): ViewHolder(binding)
        class Swatch(val binding: ItemColorPickerBinding): ViewHolder(binding)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SWATCH = 1
    }

}
