package com.kieronquinn.app.darq.components.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.R
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
                .setPositiveButton(R.string.diagnostics_crash_review) { _, _ -> showReport(activity, clearPendingAfter = true) }
                .setNegativeButton(R.string.diagnostics_crash_not_now) { _, _ -> CrashReporter.clearPending(activity) }
                .setOnCancelListener { CrashReporter.clearPending(activity) }
                .show()
        }
    }

    /**
     *  Builds the report off the main thread and shows it, so the user sees exactly what would leave the device.
     */
    fun showReport(activity: AppCompatActivity, clearPendingAfter: Boolean = false) {
        activity.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { CrashReporter.buildReport(activity, header.build()) }
            if (clearPendingAfter) CrashReporter.clearPending(activity)
            if (activity.isFinishing) return@launch
            val padding = activity.resources.getDimensionPixelSize(R.dimen.padding_24)
            val text = TextView(activity).apply {
                this.text = report
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(padding, padding / 2, padding, 0)
            }
            val scroll = ScrollView(activity).apply {
                addView(text, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.diagnostics_report_title)
                .setView(scroll)
                .setPositiveButton(R.string.diagnostics_report_send) { _, _ -> showSendOptions(activity, report) }
                .setNegativeButton(R.string.diagnostics_report_close, null)
                .show()
        }
    }

    private fun showSendOptions(activity: AppCompatActivity, report: String) {
        val options = activity.resources.getStringArray(R.array.diagnostics_send_options)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.diagnostics_send_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openBugReport(activity, report)
                    1 -> {
                        copy(activity, DiagnosticsText.fitForIssue(report), R.string.diagnostics_copied_comment)
                        openInBrowser(activity, DiagnosticsText.ISSUES_URL)
                    }
                    2 -> saveToDownloads(activity, report)
                    3 -> share(activity, report)
                    else -> copy(activity, report, R.string.diagnostics_copied)
                }
            }
            .show()
    }

    private fun openBugReport(activity: AppCompatActivity, report: String) {
        copy(activity, DiagnosticsText.fitForIssue(report), R.string.diagnostics_copied_form)
        openInBrowser(activity, DiagnosticsText.bugReportUrl(header.appVersion, header.android, header.device, header.mode))
    }

    /**
     *  Opens the issue chooser for anything that is not a crash, such as a feature request.
     */
    fun openIssueChooser(context: Context) {
        openInBrowser(context, "${DiagnosticsText.ISSUES_URL}/new/choose")
    }

    // NOTE: The GitHub app cannot show issue forms and drops the prefilled fields, so the default browser is targeted directly.
    // NOTE: Without a default browser the plain VIEW intent lets the user choose.
    fun openInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val browser = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (browser != null && browser != "android") intent.setPackage(browser)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(intent.setPackage(null))
            } catch (e: Exception) {
                Toast.makeText(context, R.string.diagnostics_no_browser, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copy(context: Context, text: String, toast: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostics_report_title), text))
        Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
    }

    private fun fileName() = "darq-reborn-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    //MediaStore Downloads needs no storage permission on Android 10 and later, which is DarQ's minimum
    private fun saveToDownloads(activity: AppCompatActivity, report: String) {
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
            Toast.makeText(activity, if (saved) R.string.diagnostics_saved else R.string.diagnostics_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun share(activity: AppCompatActivity, report: String) {
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
