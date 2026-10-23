package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.utils.SafeRun
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.ThemeManager
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetColorPickerBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorPickerAdapter.AccentOption
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorPickerAdapter.Row
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
        val rows = buildRows()
        with(binding){
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(requireContext()))
            val adapter = ColorPickerAdapter(requireContext(), rows, ::isSelected, ::onPicked)
            colorPickerList.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT).apply {
                spanSizeLookup = adapter.spanSizeLookup(SPAN_COUNT)
            }
            colorPickerList.adapter = adapter
            colorPickerOk.setOnClickListener {
                dialog?.dismiss()
            }
            colorPickerOk.setTextColor(themeColors.getAccentColor(requireContext()))
        }
    }

    // NOTE: Three labeled sections: the automatic wallpaper accent, the presets, and single wallpaper colors (Android 12 and later only).
    private fun buildRows(): List<Row> {
        val context = requireContext()
        val rows = ArrayList<Row>()
        rows += Row.Header(getString(R.string.appearance_accent_section_wallpaper))
        rows += Row.Swatch(AccentOption(
            ThemeManager.ACCENT_WALLPAPER,
            ThemeManager.wallpaperPrimaryColor(context) ?: themeColors.getAccentColor(context),
            getString(R.string.appearance_accent_auto)
        ))
        rows += Row.Header(getString(R.string.appearance_accent_section_presets))
        val presetLabels = resources.getStringArray(R.array.appearance_accent_presets)
        ThemeManager.Preset.values().forEachIndexed { index, preset ->
            rows += Row.Swatch(AccentOption(preset.key, preset.seed, presetLabels.getOrElse(index) { preset.key }))
        }
        //Single wallpaper colors come from the dynamic color engine, so they exist on Android 12 and later only
        rows += Row.Header(getString(R.string.appearance_accent_section_custom))
        val wallpaperColors = if (ThemeManager.isDynamicAvailable) ThemeManager.wallpaperColors(context) else emptyList()
        wallpaperColors.forEachIndexed { index, color ->
            rows += Row.Swatch(AccentOption(ThemeManager.ACCENT_CUSTOM, color, getString(R.string.appearance_accent_custom, index + 1), color))
        }
        //A color made in the custom picker stays selectable here, so it can be switched back to after trying a preset
        val stored = settings.monetColor
        if (stored != Integer.MAX_VALUE && stored !in wallpaperColors) {
            rows += Row.Swatch(AccentOption(ThemeManager.ACCENT_CUSTOM, stored, getString(R.string.appearance_accent_yours), stored))
        }
        rows += Row.Swatch(AccentOption(
            ThemeManager.ACCENT_CUSTOM, stored.takeIf { it != Integer.MAX_VALUE } ?: themeColors.getAccentColor(context),
            getString(R.string.appearance_accent_custom_pick), isPicker = true
        ))
        return rows
    }

    private fun isSelected(option: AccentOption): Boolean {
        if(option.isPicker) return false
        if(option.accent != settings.themeAccent) return false
        return option.customColor == null || option.customColor == settings.monetColor
    }

    private fun onPicked(option: AccentOption) = SafeRun.run(requireContext(), "accent pick") {
        if(option.isPicker) {
            dialog?.dismiss()
            CustomColorBottomSheetFragment().show(parentFragmentManager, "custom_color")
            return@run
        }
        if(isSelected(option)) return@run
        settings.themeAccent = option.accent
        option.customColor?.let { settings.monetColor = it }
        dialog?.dismiss()
        //The accent lives in the activity theme, so it shows only after the activity is rebuilt
        requireActivity().recreate()
    }

}
