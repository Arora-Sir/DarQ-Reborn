package com.kieronquinn.app.darq.ui.screens.settings.appearance

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.ui.utils.LiftSlider
import com.kieronquinn.app.darq.components.theme.SettingsIcons
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import com.kieronquinn.app.darq.components.theme.PureBlackLift
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.ui.utils.showBlurred
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appearance_theme_title)
            .setSingleChoiceItems(themeLabels.toTypedArray(), settings.themeMode) { dialog, which ->
                dialog.dismiss()
                if(which == settings.themeMode) return@setSingleChoiceItems
                settings.themeMode = which
                //AppCompat rebuilds every activity whose night mode changes
                ThemeManager.applyNightMode(settings)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlurred(requireActivity())
    }

    private fun onAccentClicked() {
        lifecycleScope.launch {
            navigation.navigate(SettingsAppearanceFragmentDirections.actionSettingsAppearanceFragmentToColorPickerBottomSheetFragment())
        }
    }

    private fun liftLabel(step: Int) =
        if(step * 10 == PureBlackLift.DEFAULT) getString(R.string.appearance_pure_black_lift_level_recommended, step)
        else getString(R.string.appearance_pure_black_lift_level, step)

    // NOTE: The slider only moves the sample inside the dialog. Apply stores the step and rebuilds the screen, Cancel and Back change nothing.
    // NOTE: Reset moves the slider back to the recommended step and keeps the dialog open.
    private fun onLiftClicked() {
        val context = requireContext()
        val content = layoutInflater.inflate(R.layout.dialog_surface_lift, null)
        val slider = content.findViewById<LiftSlider>(R.id.surface_lift_slider)
        val value = content.findViewById<TextView>(R.id.surface_lift_value)
        val card = content.findViewById<View>(R.id.surface_lift_card)
        val field = content.findViewById<View>(R.id.surface_lift_field)
        val highest = content.findViewById<View>(R.id.surface_lift_highest)
        content.findViewById<ImageView>(R.id.surface_lift_icon).setImageDrawable(
            SettingsIcons.load(context, R.drawable.ic_appearance_black_round, settings.themeTintedIcons)
        )
        val start = PureBlackLift.stepOf(settings.themePureBlackLift)
        val radius = 12 * resources.displayMetrics.density
        fun tint(view: View, level: Int) {
            view.background = GradientDrawable().apply {
                setColor(Color.rgb(level, level, level))
                cornerRadius = radius
            }
        }
        fun show() {
            val step = slider.value
            val ladder = PureBlackLift.ladder(step * 10)
            tint(card, ladder.container)
            tint(field, ladder.high)
            tint(highest, ladder.highest)
            value.text = liftLabel(step)
            slider.contentDescription = getString(R.string.appearance_pure_black_lift_title) + ", " + liftLabel(step)
        }
        slider.setInitial(start)
        slider.onChange = { show() }
        show()
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(content)
            .setPositiveButton(R.string.appearance_accent_apply) { _, _ ->
                val chosen = slider.value
                if(chosen == start) return@setPositiveButton
                settings.themePureBlackLift = chosen * 10
                if(requireContext().isDarkTheme) rebuild() else refreshSwitchRows()
            }
            .setNeutralButton(R.string.appearance_pure_black_lift_reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .showBlurred(requireActivity())
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener { slider.setStep(PureBlackLift.DEFAULT / 10) }
    }

    private fun refreshSwitchRows() {
        view?.post { adapter.notifySwitchSettings() }
    }

    private fun rebuild() {
        view?.post { activity?.recreate() }
    }

}
