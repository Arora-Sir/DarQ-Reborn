package com.kieronquinn.app.darq.ui.screens.bottomsheets.report

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsDialogs
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsHeader
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsText
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetReportIntakeBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorMath
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import org.koin.android.ext.android.inject

// NOTE: Collects issue report details in an ergonomic Material 3 bottom sheet.
// NOTE: Tapping Continue proceeds to the structured report preview sheet with redacted diagnostics.
class ReportIntakeBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetReportIntakeBinding>(
    FragmentBottomSheetReportIntakeBinding::inflate
) {

    companion object {
        fun show(activity: AppCompatActivity) {
            ReportIntakeBottomSheetFragment().show(activity.supportFragmentManager, "report_intake")
        }
    }

    private val header by inject<DiagnosticsHeader>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = maxOf(navigationInsets.bottom, imeInsets.bottom)
            val extraPadding = resources.getDimension(R.dimen.padding_16).toInt()
            view.updatePadding(bottom = bottomPadding + extraPadding)
            insets
        }

        with(binding) {
            root.backgroundTintList = ColorStateList.valueOf(themeColors.getSheetColor(context))
            val accent = themeColors.getAccentColor(context)
            val cardBg = themeColors.getBackgroundColorSecondary(context)
            val cardTint = ColorStateList.valueOf(cardBg)
            val onSurface = themeColors.getOnSurfaceColor(context)
            val secondaryText = themeColors.getSecondaryTextColor(context)

            reportIntakeTitle.setTextColor(onSurface)
            reportCardLogOption.backgroundTintList = cardTint
            reportCardLogOption.applyPressFeedback()

            reportIssueCancel.backgroundTintList = cardTint
            reportIssueCancel.setTextColor(onSurface)
            reportIssueContinue.backgroundTintList = ColorStateList.valueOf(accent)
            reportIssueContinue.setTextColor(ColorMath.onColor(accent))

            val checkboxTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, secondaryText)
            )
            reportIssueIncludeLog.buttonTintList = checkboxTint
            reportIssueLogTitle.setTextColor(onSurface)
            reportIssueLogHint.setTextColor(secondaryText)

            reportIssueFirst.setTextColor(onSurface)
            reportIssueSecond.setTextColor(onSurface)
            reportIssueFirstLayout.boxBackgroundColor = cardBg
            reportIssueSecondLayout.boxBackgroundColor = cardBg

            val strokeColorState = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(accent, Color.TRANSPARENT)
            )
            reportIssueFirstLayout.setBoxStrokeColorStateList(strokeColorState)
            reportIssueSecondLayout.setBoxStrokeColorStateList(strokeColorState)
            reportIssueFirstLayout.defaultHintTextColor = ColorStateList.valueOf(secondaryText)
            reportIssueSecondLayout.defaultHintTextColor = ColorStateList.valueOf(secondaryText)

            val isBug = { reportIssueType.checkedButtonId == R.id.report_issue_bug }
            val update = {
                val bug = isBug()
                reportIssueFirstLayout.hint = getString(if (bug) R.string.report_issue_what_happened else R.string.report_issue_problem)
                reportIssueSecondLayout.hint = getString(if (bug) R.string.report_issue_steps else R.string.report_issue_solution)
                reportIssueIncludeLog.isEnabled = bug
                reportIssueLogTitle.isEnabled = bug
                reportIssueLogHint.isEnabled = bug
                reportCardLogOption.alpha = if (bug) 1.0f else 0.5f

                if (bug) {
                    reportIssueBug.backgroundTintList = ColorStateList.valueOf(accent)
                    reportIssueBug.setTextColor(ColorMath.onColor(accent))
                    reportIssueFeature.backgroundTintList = cardTint
                    reportIssueFeature.setTextColor(onSurface)
                } else {
                    reportIssueFeature.backgroundTintList = ColorStateList.valueOf(accent)
                    reportIssueFeature.setTextColor(ColorMath.onColor(accent))
                    reportIssueBug.backgroundTintList = cardTint
                    reportIssueBug.setTextColor(onSurface)
                }
            }

            reportIssueType.addOnButtonCheckedListener { _, _, isChecked ->
                if (isChecked) update()
            }
            update()

            reportCardLogOption.setOnClickListener {
                if (isBug()) {
                    reportIssueIncludeLog.isChecked = !reportIssueIncludeLog.isChecked
                }
            }

            reportIssueCancel.setOnClickListener { dismiss() }
            reportIssueContinue.setOnClickListener {
                val first = reportIssueFirst.text?.toString()?.takeIf { it.isNotBlank() }
                val second = reportIssueSecond.text?.toString()?.takeIf { it.isNotBlank() }
                val bug = isBug()
                val includeLog = reportIssueIncludeLog.isChecked
                val act = activity as? AppCompatActivity ?: return@setOnClickListener
                dismiss()
                when {
                    !bug -> DiagnosticsDialogs.openInBrowser(act, DiagnosticsText.featureRequestUrl(header.mode, first, second))
                    includeLog -> ReportBottomSheetFragment.show(act, description = first, steps = second)
                    else -> DiagnosticsDialogs.openInBrowser(
                        act,
                        DiagnosticsText.bugReportUrl(header.appVersion, header.android, header.device, header.mode, description = first, steps = second)
                    )
                }
            }
        }
    }

}
