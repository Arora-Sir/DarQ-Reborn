package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorPickerAdapter.Row
import com.kieronquinn.app.darq.utils.SafeRun
import org.koin.android.ext.android.inject

// NOTE: A tap only previews an accent and the Apply button commits it, because changing the accent rebuilds the screen and a mis-tap should cost nothing.
// NOTE: Cancel, Back and a swipe down leave the saved accent untouched.
// NOTE: Names stay under every swatch because the sheet is roomy, and the chosen name is repeated in the title so it is never missing.
class ColorPickerBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetColorPickerBinding>(FragmentBottomSheetColorPickerBinding::inflate) {

    companion object {
        private const val SPAN_COUNT = 5
        private const val STATE_ACCENT = "accent"
        private const val STATE_CUSTOM = "custom"
        private const val LOW_CONTRAST = 3.0
        private const val UNSET_COLOR = Integer.MAX_VALUE
    }

    private val settings by inject<DarqSharedPreferences>()

    //The draft choice, committed only by Apply
    private var draftAccent = ""
    private var draftCustom: Int? = null
    private var adapter: ColorPickerAdapter? = null
    private var options = emptyList<AccentOption>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draftAccent = savedInstanceState?.getString(STATE_ACCENT) ?: settings.themeAccent
        draftCustom = if (savedInstanceState?.containsKey(STATE_CUSTOM) == true) savedInstanceState.getInt(STATE_CUSTOM)
        else settings.monetColor.takeIf { settings.themeAccent == ThemeManager.ACCENT_CUSTOM && it != UNSET_COLOR }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACCENT, draftAccent)
        draftCustom?.let { outState.putInt(STATE_CUSTOM, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view){ _, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val extraPadding = resources.getDimension(R.dimen.padding_16).toInt()
            view.updatePadding(left = navigationInsets.left, right = navigationInsets.right, bottom = navigationInsets.bottom + extraPadding)
            insets
        }
        val rows = buildRows()
        options = rows.filterIsInstance<Row.Swatch>().map { it.option }
        with(binding){
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(requireContext()))
            val picker = ColorPickerAdapter(requireContext(), rows, ::isSelected, ::onPicked)
            adapter = picker
            colorPickerList.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT).apply {
                spanSizeLookup = picker.spanSizeLookup(SPAN_COUNT)
            }
            colorPickerList.adapter = picker
            val accent = themeColors.getAccentColor(requireContext())
            colorPickerCancel.setTextColor(accent)
            colorPickerCancel.setOnClickListener { dismiss() }
            colorPickerApply.setOnClickListener { apply() }
        }
        updatePreview()
    }

    // NOTE: Labeled sections: the automatic wallpaper accent, the presets, and custom colors (wallpaper colors on Android 12 and later, the last custom color, and the picker).
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
        if (stored != UNSET_COLOR && stored !in wallpaperColors) {
            rows += Row.Swatch(AccentOption(ThemeManager.ACCENT_CUSTOM, stored, getString(R.string.appearance_accent_yours), stored))
        }
        rows += Row.Swatch(AccentOption(
            ThemeManager.ACCENT_CUSTOM, stored.takeIf { it != UNSET_COLOR } ?: ColorMath.DEFAULT_CUSTOM,
            getString(R.string.appearance_accent_custom_pick), isPicker = true
        ))
        return rows
    }

    private fun isSelected(option: AccentOption): Boolean {
        if (option.isPicker) return false
        if (option.accent != draftAccent) return false
        return option.customColor == null || option.customColor == draftCustom
    }

    private fun onPicked(option: AccentOption) = SafeRun.run(requireContext(), "accent pick") {
        if (option.isPicker) {
            dialog?.dismiss()
            CustomColorBottomSheetFragment().show(parentFragmentManager, "custom_color")
            return@run
        }
        draftAccent = option.accent
        draftCustom = option.customColor
        adapter?.notifyDataSetChanged()
        updatePreview()
    }

    private fun draftOption(): AccentOption? = options.firstOrNull { isSelected(it) }

    // NOTE: The preview pill shows the chosen color and its name, and the line under it states the contrast on black and on a card.
    private fun updatePreview() = with(binding) {
        val option = draftOption()
        val color = option?.color ?: themeColors.getAccentColor(requireContext())
        val name = option?.label ?: getString(R.string.appearance_accent_auto)
        colorPickerTitle.text = getString(R.string.appearance_accent_title_chosen, name)
        colorPickerPreview.text = name
        colorPickerPreview.background = ContextCompat.getDrawable(requireContext(), R.drawable.button_background_backup_restore)?.mutate()
            ?.also { DrawableCompat.setTint(it, color) }
        colorPickerPreview.setTextColor(ColorMath.onColor(color))
        val onBlack = ColorMath.contrast(color, Color.BLACK)
        val onCard = ColorMath.contrast(color, themeColors.getBackgroundColorSecondary(requireContext()))
        val note = if (onBlack < LOW_CONTRAST || onCard < LOW_CONTRAST) " " + getString(R.string.appearance_accent_low_contrast) else ""
        colorPickerContrast.text = getString(R.string.appearance_accent_contrast, onBlack, onCard) + note
        colorPickerApply.backgroundTintList = ColorStateList.valueOf(color)
        colorPickerApply.setTextColor(ColorMath.onColor(color))
        colorPickerApply.isEnabled = true
    }

    private fun apply() = SafeRun.run(requireContext(), "accent apply") {
        val changed = draftAccent != settings.themeAccent || (draftAccent == ThemeManager.ACCENT_CUSTOM && draftCustom != settings.monetColor)
        if (!changed) {
            dismiss()
            return@run
        }
        settings.themeAccent = draftAccent
        draftCustom?.let { settings.monetColor = it }
        dialog?.dismiss()
        //The accent lives in the activity theme, so it shows only after the activity is rebuilt
        requireActivity().recreate()
    }

}
