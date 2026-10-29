package com.kieronquinn.app.darq.ui.screens.settings.appearance

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.ThemeManager
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetThemeChooserBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import org.koin.android.ext.android.inject

// NOTE: Offers theme selection (System, Light, Dark) as a modern bottom sheet.
// NOTE: Tapping any option commits the choice and triggers AppCompat night mode re-theming.
class ThemeChooserBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetThemeChooserBinding>(
    FragmentBottomSheetThemeChooserBinding::inflate
) {

    private val settings by inject<DarqSharedPreferences>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val extraPadding = resources.getDimension(R.dimen.padding_16).toInt()
            view.updatePadding(bottom = navigationInsets.bottom + extraPadding)
            insets
        }

        with(binding) {
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(context))
            val accent = themeColors.getAccentColor(context)
            val cardBg = themeColors.getBackgroundColorSecondary(context)
            val cardTint = ColorStateList.valueOf(cardBg)
            val onSurface = themeColors.getOnSurfaceColor(context)
            val secondaryText = themeColors.getSecondaryTextColor(context)

            themeChooserTitle.setTextColor(onSurface)
            themeChooserCancel.backgroundTintList = cardTint
            themeChooserCancel.setTextColor(onSurface)

            val cards = listOf(themeCardSystem, themeCardLight, themeCardDark)
            cards.forEach {
                it.backgroundTintList = cardTint
                it.applyPressFeedback()
            }

            val radioTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, secondaryText)
            )
            listOf(themeRadioSystem, themeRadioLight, themeRadioDark).forEach {
                it.buttonTintList = radioTint
            }

            themeIcSystem.imageTintList = ColorStateList.valueOf(if (settings.themeMode == 0) accent else secondaryText)
            themeIcLight.imageTintList = ColorStateList.valueOf(if (settings.themeMode == 1) accent else secondaryText)
            themeIcDark.imageTintList = ColorStateList.valueOf(if (settings.themeMode == 2) accent else secondaryText)

            themeRadioSystem.isChecked = (settings.themeMode == 0)
            themeRadioLight.isChecked = (settings.themeMode == 1)
            themeRadioDark.isChecked = (settings.themeMode == 2)

            themeTitleSystem.setTextColor(onSurface)
            themeTitleLight.setTextColor(onSurface)
            themeTitleDark.setTextColor(onSurface)

            themeSummarySystem.setTextColor(secondaryText)
            themeSummaryLight.setTextColor(secondaryText)
            themeSummaryDark.setTextColor(secondaryText)

            fun select(mode: Int) {
                dismiss()
                if (mode != settings.themeMode) {
                    settings.themeMode = mode
                    ThemeManager.applyNightMode(settings)
                }
            }

            themeCardSystem.setOnClickListener { select(0) }
            themeCardLight.setOnClickListener { select(1) }
            themeCardDark.setOnClickListener { select(2) }
            themeChooserCancel.setOnClickListener { dismiss() }
        }
    }

}
