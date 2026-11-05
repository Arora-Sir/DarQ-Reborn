package com.kieronquinn.app.darq.ui.screens.settings.appearance

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.PureBlackLift
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetSurfaceLiftBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorMath
import com.kieronquinn.app.darq.utils.extensions.isDarkTheme
import org.koin.android.ext.android.inject

// NOTE: A tap on Pure black row opens this bottom sheet.
// NOTE: Moving the slider updates the preview live. Cancel and Back leave the lift level unchanged.
class SurfaceLiftBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetSurfaceLiftBinding>(
    FragmentBottomSheetSurfaceLiftBinding::inflate
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

        val start = PureBlackLift.stepOf(settings.themePureBlackLift)
        val radius = 12 * resources.displayMetrics.density

        fun tint(target: View, level: Int) {
            target.background = GradientDrawable().apply {
                setColor(Color.rgb(level, level, level))
                cornerRadius = radius
            }
        }

        fun show() {
            with(binding) {
                val step = surfaceLiftSlider.value
                val ladder = PureBlackLift.ladder(step * 10)
                tint(surfaceLiftCard, ladder.container)
                tint(surfaceLiftField, ladder.high)
                tint(surfaceLiftHighest, ladder.highest)
                val label = liftLabel(step)
                surfaceLiftValue.text = label
                surfaceLiftSlider.contentDescription = getString(R.string.appearance_pure_black_lift_title) + ", " + label
            }
        }

        with(binding) {
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(context))
            val accent = themeColors.getAccentColor(context)
            val onSurface = themeColors.getOnSurfaceColor(context)
            val secondaryText = themeColors.getSecondaryTextColor(context)
            surfaceLiftTitle.setTextColor(onSurface)
            surfaceLiftHint.setTextColor(secondaryText)
            surfaceLiftValue.setTextColor(onSurface)
            surfaceLiftReset.setTextColor(accent)
            surfaceLiftCancel.setTextColor(accent)
            surfaceLiftApply.backgroundTintList = ColorStateList.valueOf(accent)
            surfaceLiftApply.setTextColor(ColorMath.onColor(accent))

            surfaceLiftSlider.setInitial(start)
            surfaceLiftSlider.onChange = { show() }
            show()

            surfaceLiftReset.setOnClickListener {
                surfaceLiftSlider.setStep(PureBlackLift.DEFAULT / 10)
            }
            surfaceLiftCancel.setOnClickListener {
                dismiss()
            }
            surfaceLiftApply.setOnClickListener {
                val chosen = surfaceLiftSlider.value
                dismiss()
                if (chosen != start) {
                    settings.themePureBlackLift = chosen * 10
                    if (requireContext().isDarkTheme) {
                        requireActivity().recreate()
                    }
                }
            }
        }
    }

    private fun liftLabel(step: Int) =
        if (step * 10 == PureBlackLift.DEFAULT) getString(R.string.appearance_pure_black_lift_level_recommended, step)
        else getString(R.string.appearance_pure_black_lift_level, step)

}
