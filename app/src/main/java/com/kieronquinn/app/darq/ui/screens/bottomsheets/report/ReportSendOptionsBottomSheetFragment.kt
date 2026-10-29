package com.kieronquinn.app.darq.ui.screens.bottomsheets.report

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsDialogs
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsText
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetReportSendOptionsBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback

// NOTE: Bottom sheet displaying options for dispatching or exporting the diagnostics report.
class ReportSendOptionsBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetReportSendOptionsBinding>(
    FragmentBottomSheetReportSendOptionsBinding::inflate
) {

    companion object {
        private const val ARG_REPORT = "report"

        fun show(activity: AppCompatActivity, report: String) {
            ReportSendOptionsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REPORT, report)
                }
            }.show(activity.supportFragmentManager, "report_send_options")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val report = arguments?.getString(ARG_REPORT) ?: run { dismiss(); return }
        val options = resources.getStringArray(R.array.diagnostics_send_options)

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

            sendOptionsTitle.setTextColor(onSurface)
            sendOptionsCancel.backgroundTintList = cardTint
            sendOptionsCancel.setTextColor(onSurface)

            val cards = listOf(sendCardGithub, sendCardComment, sendCardSave, sendCardShare, sendCardCopy)
            cards.forEach {
                it.backgroundTintList = cardTint
                it.applyPressFeedback()
            }

            val iconTint = ColorStateList.valueOf(accent)
            listOf(sendIcGithub, sendIcComment, sendIcSave, sendIcShare, sendIcCopy).forEach {
                it.imageTintList = iconTint
            }

            listOf(sendOptionGithub, sendOptionComment, sendOptionSave, sendOptionShare, sendOptionCopy).forEach {
                it.setTextColor(onSurface)
            }

            listOf(sendSummaryGithub, sendSummaryComment, sendSummarySave, sendSummaryShare, sendSummaryCopy).forEach {
                it.setTextColor(secondaryText)
            }

            sendOptionGithub.text = options.getOrElse(0) { "" }
            sendOptionComment.text = options.getOrElse(1) { "" }
            sendOptionSave.text = options.getOrElse(2) { "" }
            sendOptionShare.text = options.getOrElse(3) { "" }
            sendOptionCopy.text = options.getOrElse(4) { "" }

            val act = activity as? AppCompatActivity

            sendCardGithub.setOnClickListener {
                dismiss()
                act?.let { DiagnosticsDialogs.openBugReport(it, report) }
            }
            sendCardComment.setOnClickListener {
                dismiss()
                act?.let {
                    DiagnosticsDialogs.copy(it, DiagnosticsText.fitForIssue(report), R.string.diagnostics_copied_comment)
                    DiagnosticsDialogs.openInBrowser(it, DiagnosticsText.ISSUES_URL)
                }
            }
            sendCardSave.setOnClickListener {
                dismiss()
                act?.let { DiagnosticsDialogs.saveToDownloads(it, report) }
            }
            sendCardShare.setOnClickListener {
                dismiss()
                act?.let { DiagnosticsDialogs.share(it, report) }
            }
            sendCardCopy.setOnClickListener {
                dismiss()
                act?.let { DiagnosticsDialogs.copy(it, report, R.string.diagnostics_copied) }
            }
            sendOptionsCancel.setOnClickListener {
                dismiss()
            }
        }
    }

}
