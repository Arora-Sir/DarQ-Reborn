package com.kieronquinn.app.darq.ui.screens.settings.appearance

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.darq.components.theme.PureBlackLift
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.navigation.Navigation
import com.kieronquinn.app.darq.components.theme.ThemeManager
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorMath
import com.kieronquinn.app.darq.databinding.FragmentSettingsDeveloperOptionsBinding
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.darq.ui.base.BackAvailable
import com.kieronquinn.app.darq.ui.screens.settings.BaseSettingsFragment
import com.kieronquinn.app.darq.ui.screens.settings.SettingsAdapter
import com.kieronquinn.app.darq.utils.extensions.isDarkTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SettingsAppearanceFragment: BaseSettingsFragment<FragmentSettingsDeveloperOptionsBinding>(FragmentSettingsDeveloperOptionsBinding::inflate), BackAvailable,
    AutoExpandOnRotate {

    private val navigation by inject<Navigation>()

    private val themeLabels by lazy {
        listOf(R.string.appearance_theme_system, R.string.appearance_theme_light, R.string.appearance_theme_dark).map { getString(it) }
    }

    override val settingsItems by lazy {
        listOf<SettingsItem>(
            SettingsItem.Setting(
                R.drawable.ic_appearance_theme_round,
                getString(R.string.appearance_theme_title),
                themeLabels.getOrElse(settings.themeMode) { themeLabels[0] },
                tapAction = this::onThemeClicked
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_appearance_black_round,
                getString(R.string.appearance_pure_black_title),
                getString(R.string.appearance_pure_black_content),
                getValue = { settings.themePureBlack },
                setValue = {
                    settings.themePureBlack = it
                    //Pure black only exists in dark mode, so a light screen has nothing to rebuild
                    if(requireContext().isDarkTheme) rebuild() else refreshSwitchRows()
                },
                //With the switch on, the subtitle shows the surface lift and a tap on the row, not on the switch, opens its dialog
                contentProvider = {
                    if(settings.themePureBlack) getString(R.string.appearance_pure_black_lift_summary, liftLabel(PureBlackLift.stepOf(settings.themePureBlackLift)))
                    else getString(R.string.appearance_pure_black_content)
                },
                rowTapAction = {
                    if(settings.themePureBlack){
                        onLiftClicked()
                        true
                    }else false
                }
            ),
            SettingsItem.Setting(
                R.drawable.ic_appearance_accent_round,
                getString(R.string.appearance_accent_title),
                accentSummary(),
                tapAction = this::onAccentClicked
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_appearance_icons_round,
                getString(R.string.appearance_tinted_icons_title),
                getString(R.string.appearance_tinted_icons_content),
                getValue = { settings.themeTintedIcons },
                setValue = {
                    settings.themeTintedIcons = it
                    rebuild()
                }
            )
        ).toMutableList()
    }

    private val adapter by lazy {
        SettingsAdapter(requireContext(), settingsItems)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(binding.recyclerView, adapter)
        setupSnackbarPadding(binding.recyclerView)
    }

    private fun accentSummary(): String {
        val accent = settings.themeAccent
        val presetLabels = resources.getStringArray(R.array.appearance_accent_presets)
        val label = ThemeManager.Preset.fromKey(accent)?.let { presetLabels.getOrElse(it.ordinal) { accent } }
            ?: if(accent == ThemeManager.ACCENT_CUSTOM) customSummary()
            else getString(R.string.appearance_accent_wallpaper)
        val isPreset = ThemeManager.Preset.fromKey(accent) != null
        return if(!isPreset && !ThemeManager.isDynamicAvailable){
            "$label. ${getString(R.string.appearance_accent_fallback)}"
        }else label
    }

    //A color picked from the wallpaper keeps its old label, one made in the custom picker shows its hex value
    private fun customSummary(): String {
        val color = settings.monetColor
        val fromWallpaper = ThemeManager.isDynamicAvailable && color in ThemeManager.wallpaperColors(requireContext())
        return if(fromWallpaper || color == Integer.MAX_VALUE) getString(R.string.appearance_accent_custom_summary)
        else getString(R.string.appearance_accent_custom_value, ColorMath.toHex(color))
    }

    private fun onThemeClicked() {
        ThemeChooserBottomSheetFragment().show(parentFragmentManager, "theme_chooser")
    }

    private fun onAccentClicked() {
        lifecycleScope.launch {
            navigation.navigate(SettingsAppearanceFragmentDirections.actionSettingsAppearanceFragmentToColorPickerBottomSheetFragment())
        }
    }

    private fun liftLabel(step: Int) =
        if(step * 10 == PureBlackLift.DEFAULT) getString(R.string.appearance_pure_black_lift_level_recommended, step)
        else getString(R.string.appearance_pure_black_lift_level, step)

    private fun onLiftClicked() {
        SurfaceLiftBottomSheetFragment().show(parentFragmentManager, "surface_lift")
    }

    private fun refreshSwitchRows() {
        view?.post { adapter.notifySwitchSettings() }
    }

    private fun rebuild() {
        view?.post { activity?.recreate() }
    }

}
