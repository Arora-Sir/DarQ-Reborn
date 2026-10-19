package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.ThemeManager
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetColorPickerBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorPickerAdapter.AccentOption
import org.koin.android.ext.android.inject

class ColorPickerBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetColorPickerBinding>(FragmentBottomSheetColorPickerBinding::inflate) {

    companion object {
        private const val SPAN_COUNT = 5
    }

    private val settings by inject<DarqSharedPreferences>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view){ _, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val extraPadding = resources.getDimension(R.dimen.padding_16).toInt()
            view.updatePadding(left = navigationInsets.left, right = navigationInsets.right, bottom = navigationInsets.bottom + extraPadding)
            insets
        }
        val options = buildOptions()
        with(binding){
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(requireContext()))
            colorPickerList.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
            colorPickerList.adapter = ColorPickerAdapter(requireContext(), options, ::isSelected, ::onPicked)
            colorPickerSelected.text = options.firstOrNull(::isSelected)?.label
            colorPickerOk.setOnClickListener {
                dialog?.dismiss()
            }
            colorPickerOk.setTextColor(themeColors.getAccentColor(requireContext()))
        }
    }

    private fun buildOptions(): List<AccentOption> {
        val context = requireContext()
        val wallpaper = AccentOption(
            ThemeManager.ACCENT_WALLPAPER,
            ThemeManager.wallpaperPrimaryColor(context) ?: themeColors.getAccentColor(context),
            getString(R.string.appearance_accent_wallpaper)
        )
        val presetLabels = resources.getStringArray(R.array.appearance_accent_presets)
        val presets = ThemeManager.Preset.values().mapIndexed { index, preset ->
            AccentOption(preset.key, preset.seed, presetLabels.getOrElse(index) { preset.key })
        }
        //Custom accents are built by the dynamic color engine, so they are offered only where it exists (Android 12 and later)
        val custom = if(ThemeManager.isDynamicAvailable){
            ThemeManager.wallpaperColors(context).mapIndexed { index, color ->
                AccentOption(ThemeManager.ACCENT_CUSTOM, color, getString(R.string.appearance_accent_custom, index + 1), color)
            }
        } else emptyList()
        return listOf(wallpaper) + presets + custom
    }

    private fun isSelected(option: AccentOption): Boolean {
        if(option.accent != settings.themeAccent) return false
        return option.customColor == null || option.customColor == settings.monetColor
    }

    private fun onPicked(option: AccentOption) {
        if(isSelected(option)) return
        settings.themeAccent = option.accent
        option.customColor?.let { settings.monetColor = it }
        dialog?.dismiss()
        //The accent lives in the activity theme, so it shows only after the activity is rebuilt
        requireActivity().recreate()
    }

}
