package com.kieronquinn.app.darq.ui.screens.bottomsheets.report

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.diagnostics.CrashReporter
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsDialogs
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsHeader
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsLog
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetReportBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker.ColorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

// NOTE: The sheet builds the report itself, so it has a no-argument constructor and survives recreate() and process death.
// NOTE: Only the short text fields typed by the user travel in the arguments.
class ReportBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetReportBinding>(FragmentBottomSheetReportBinding::inflate) {

    companion object {
        private const val ARG_CLEAR_PENDING = "clear_pending"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_STEPS = "steps"
        private const val PREVIEW_CHARS = 12_000
        private val SUMMARY_KEYS = listOf("App:", "Android:", "Device:", "Mode:")
        private const val SCROLL_HEIGHT_FRACTION = 0.55f

        fun show(activity: AppCompatActivity, clearPending: Boolean = false, description: String? = null, steps: String? = null) {
            ReportBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_CLEAR_PENDING, clearPending)
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_STEPS, steps)
                }
            }.show(activity.supportFragmentManager, "report_sheet")
        }
    }

    private val header by inject<DiagnosticsHeader>()
    private var report: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val accent = themeColors.getAccentColor(requireContext())
            val cardTint = ColorStateList.valueOf(themeColors.getBackgroundColorSecondary(requireContext()))
            listOf(reportCardSummary, reportCardCrash, reportCardHandled, reportCardLog).forEach { it.backgroundTintList = cardTint }
            reportClose.imageTintList = ColorStateList.valueOf(themeColors.getOnSurfaceColor(requireContext()))
            reportToggleLog.setTextColor(accent)
            reportMore.setTextColor(accent)
            reportContinue.backgroundTintList = ColorStateList.valueOf(accent)
            reportContinue.setTextColor(ColorMath.onColor(accent))

            val maxHeight = (resources.displayMetrics.heightPixels * SCROLL_HEIGHT_FRACTION).toInt()
            (reportScroll.layoutParams as ConstraintLayout.LayoutParams).matchConstraintMaxHeight = maxHeight

            reportClose.setOnClickListener { dismiss() }
            reportToggleLog.setOnClickListener {
                val show = reportLog.visibility != View.VISIBLE
                reportLog.visibility = if (show) View.VISIBLE else View.GONE
                reportToggleLog.setText(if (show) R.string.diagnostics_report_hide_log else R.string.diagnostics_report_show_log)
            }
            reportContinue.setOnClickListener {
                val activity = activity as? AppCompatActivity ?: return@setOnClickListener
                val current = report ?: return@setOnClickListener
                dismiss()
                DiagnosticsDialogs.openBugReport(
                    activity, current, arguments?.getString(ARG_DESCRIPTION), arguments?.getString(ARG_STEPS)
                )
            }
            reportMore.setOnClickListener {
                val activity = activity as? AppCompatActivity ?: return@setOnClickListener
                val current = report ?: return@setOnClickListener
                dismiss()
                DiagnosticsDialogs.showSendOptions(activity, current)
            }

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                v.updatePadding(bottom = bottomInset + resources.getDimension(R.dimen.padding_8).toInt())
                insets
            }
        }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val built = withContext(Dispatchers.IO) { CrashReporter.buildReport(context, header.build()) }
            if (arguments?.getBoolean(ARG_CLEAR_PENDING) == true) CrashReporter.clearPending(context)
            report = built
            bind(built)
        }
    }

    private fun bind(built: String) = with(binding) {
        val sections = DiagnosticsLog.sections(built)
        val none = getString(R.string.diagnostics_report_none)
        //The summary card shows the four facts a maintainer reads first, the rest of the header sits with the log
        val summaryLines = sections.summary.lines()
        val headline = summaryLines.filter { line -> SUMMARY_KEYS.any { line.startsWith(it) } }
            .map { it.substringBefore(", build ") }
        reportSummary.text = headline.joinToString("\n").ifEmpty { sections.summary }
        val details = summaryLines.filterNot { line -> SUMMARY_KEYS.any { line.startsWith(it) } }.joinToString("\n")
        //A native crash or ANR has no Java trace, so the system exit record fills the crash card
        val crash = listOf(sections.lastCrash, sections.abnormalExit).filterNot { DiagnosticsLog.isEmptySection(it) }.joinToString("\n\n")
        reportCrash.text = crash.ifEmpty { none }
        reportHandled.text = sections.handledErrors.takeUnless { DiagnosticsLog.isEmptySection(it) } ?: none
        val log = sections.log
        //A full log is around 100 KB, which is slow to lay out in one view on older phones, so the preview keeps the newest part and says how much more is sent
        val shownLog = if (log.length > PREVIEW_CHARS) {
            getString(R.string.diagnostics_preview_trimmed, log.length - PREVIEW_CHARS) + "\n\n" + log.takeLast(PREVIEW_CHARS)
        } else log
        reportLog.text = if (details.isBlank()) shownLog else details + "\n\n" + shownLog
        reportLoading.visibility = View.GONE
        reportContent.visibility = View.VISIBLE
        reportContinue.isEnabled = true
        reportMore.isEnabled = true
    }

}
