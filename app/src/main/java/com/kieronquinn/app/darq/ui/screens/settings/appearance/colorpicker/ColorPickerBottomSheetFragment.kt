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
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorPickerAdapter.Kind
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
        options = buildOptions()
        with(binding){
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(requireContext()))
            val picker = ColorPickerAdapter(requireContext(), options, ::isSelected, ::onPicked)
            adapter = picker
            colorPickerList.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
            colorPickerList.adapter = picker
            colorPickerList.background?.mutate()?.let { DrawableCompat.setTint(it, themeColors.getBackgroundColorSecondary(requireContext())) }
            val accent = themeColors.getAccentColor(requireContext())
            colorPickerCancel.setTextColor(accent)
            colorPickerCancel.setOnClickListener { dismiss() }
            colorPickerApply.setOnClickListener { apply() }
        }
        updatePreview()
    }

    // NOTE: Nine tiles in order, the wallpaper accent, the seven presets and one Custom tile that shows the current custom color and opens the picker.
    // NOTE: Nothing is repeated, so the custom picker never shows the presets again.
    private fun buildOptions(): List<AccentOption> {
        val context = requireContext()
        val result = ArrayList<AccentOption>()
        result += AccentOption(
            ThemeManager.ACCENT_WALLPAPER,
            ThemeManager.wallpaperPrimaryColor(context) ?: themeColors.getAccentColor(context),
            getString(R.string.appearance_accent_wallpaper), kind = Kind.WALLPAPER
        )
        val presetLabels = resources.getStringArray(R.array.appearance_accent_presets)
        ThemeManager.Preset.values().forEachIndexed { index, preset ->
            result += AccentOption(preset.key, preset.seed, presetLabels.getOrElse(index) { preset.key })
        }
        val stored = settings.monetColor
        result += AccentOption(
            ThemeManager.ACCENT_CUSTOM, stored.takeIf { it != UNSET_COLOR } ?: ColorMath.DEFAULT_CUSTOM,
            getString(R.string.appearance_accent_custom_pick), kind = Kind.CUSTOM
        )
        return result
    }

    private fun isSelected(option: AccentOption) = option.accent == draftAccent

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

    // NOTE: The title names the chosen color and the Apply button takes its color, so the sheet has no extra preview that looks tappable.
    // NOTE: Every preset passes the contrast tests, so the contrast line lives only in the custom picker where the color is free.
    private fun updatePreview() = with(binding) {
        val option = draftOption()
        val color = option?.color ?: themeColors.getAccentColor(requireContext())
        val name = option?.label ?: getString(R.string.appearance_accent_wallpaper)
        colorPickerTitle.text = getString(R.string.appearance_accent_title_chosen, name)
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
