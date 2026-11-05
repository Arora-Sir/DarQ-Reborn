package com.kieronquinn.app.darq.components.diagnostics

import android.content.ClipData
import com.kieronquinn.app.darq.ui.utils.showBlurred
import com.kieronquinn.app.darq.utils.LinkOpener
import com.kieronquinn.app.darq.utils.Toasts
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.ui.screens.bottomsheets.report.ReportBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.bottomsheets.report.ReportIntakeBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.bottomsheets.report.ReportSendOptionsBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// NOTE: The report flow is: an optional crash prompt, then a preview of the exact redacted text, then a send option the user picks.
// NOTE: Every send option needs a tap, and the GitHub option only opens a prefilled form that the user submits in the browser.
object DiagnosticsDialogs : KoinComponent {

    private const val SHARE_DIRECTORY = "diagnostics-share"
    private const val SHARE_FILE = "darq-reborn-log.txt"

    private val header by inject<DiagnosticsHeader>()

    /**
     *  Offers the crash or abnormal exit from the previous run once. Dismissing it marks it as seen.
     */
    fun offerPendingReport(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { CrashReporter.pendingReport(activity) } ?: return@launch
            if (activity.isFinishing) return@launch
            MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_about)
                .setTitle(R.string.diagnostics_crash_title)
                .setMessage(R.string.diagnostics_crash_content)
                .setPositiveButton(R.string.diagnostics_crash_review) { _, _ -> ReportBottomSheetFragment.show(activity, clearPending = true) }
                .setNegativeButton(R.string.diagnostics_crash_not_now) { _, _ -> CrashReporter.clearPending(activity) }
                .setOnCancelListener { CrashReporter.clearPending(activity) }
                .showBlurred(activity)
        }
    }

    fun showSendOptions(activity: AppCompatActivity, report: String) {
        ReportSendOptionsBottomSheetFragment.show(activity, report)
    }

    // NOTE: A link cannot attach a file, so the form gets a short summary in its log field and the full log goes to the clipboard to paste under it.
    fun openBugReport(activity: AppCompatActivity, report: String, description: String? = null, steps: String? = null) {
        copy(activity, DiagnosticsText.fitForIssue(report), R.string.diagnostics_copied_form)
        val summary = DiagnosticsText.logSummary(report, activity.getString(R.string.diagnostics_summary_note))
        openInBrowser(activity, DiagnosticsText.bugReportUrl(header.appVersion, header.android, header.device, header.mode, summary, description, steps))
    }

    /**
     *  The single Report an issue entry: a bug or a feature request with the GitHub form fields typed in the app.
     *  Opens the intake bottom sheet.
     */
    fun showReportIssue(activity: AppCompatActivity) {
        ReportIntakeBottomSheetFragment.show(activity)
    }

    fun openInBrowser(context: Context, url: String) {
        LinkOpener.open(context, url)
    }

    internal fun copy(context: Context, text: String, toast: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostics_report_title), text))
        Toasts.show(context, toast, long = true)
    }

    private fun fileName() = "darq-reborn-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    //MediaStore Downloads needs no storage permission on Android 10 and later, which is DarQ's minimum
    internal fun saveToDownloads(activity: AppCompatActivity, report: String) {
        activity.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName())
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    }
                    val resolver = activity.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                    resolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            Toasts.show(activity, if (saved) R.string.diagnostics_saved else R.string.diagnostics_save_failed, long = true)
        }
    }

    internal fun share(activity: AppCompatActivity, report: String) {
        activity.lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val directory = File(activity.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
                val file = File(directory, SHARE_FILE).apply { writeText(report) }
                FileProvider.getUriForFile(activity, "${activity.packageName}.diagnostics", file)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.diagnostics_report_title))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.diagnostics_report_title)))
        }
    }

}
