package com.kieronquinn.app.darq.components.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.util.Log
import com.kieronquinn.app.darq.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// NOTE: Saves crash reports on the device and builds a redacted diagnostic log. Nothing is ever sent without the user tapping a send option.
// NOTE: Java crashes are written by a wrapped uncaught exception handler, which then hands over to the previous handler so the process still dies normally.
// NOTE: Native crashes and ANRs leave no Java exception, so on Android 11 and later the system record of why the process last exited covers them.
object CrashReporter {

    private const val TAG = "DarqCrashReporter"
    private const val DIRECTORY = "diagnostics"
    private const val LAST_CRASH = "last-crash.txt"
    private const val HANDLED_ERRORS = "handled-errors.txt"
    private const val PREFS = "darq_diagnostics"
    private const val KEY_LAST_EXIT = "last_exit_ms"
    private const val MAX_HANDLED_CHARS = 32 * 1024
    private const val MAX_TRACE_CHARS = 64 * 1024
    private const val LOGCAT_LINES = 800
    private const val LOGCAT_READ_LINES = 6000

    //Kept readable in reports: DarQ itself, the frameworks it talks to, and the platform
    private val READABLE_PACKAGES = setOf(
        BuildConfig.APPLICATION_ID, "moe.shizuku.privileged.api", "af.shizuku.plus", "com.topjohnwu.magisk",
        "org.lsposed.manager", "android"
    )

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (t: Throwable) {
                //Reporting must never replace the original crash
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val text = buildString {
            appendLine("Crash at ${timestamp(System.currentTimeMillis())} on thread ${thread.name}")
            appendLine(stackTrace(throwable))
        }
        File(directory(context), LAST_CRASH).writeText(text)
    }

    /**
     *  Records an error that was caught and survived, such as a failed navigation, so it still reaches the next report.
     */
    fun recordHandled(context: Context, where: String, throwable: Throwable) {
        try {
            val file = File(directory(context), HANDLED_ERRORS)
            val existing = if (file.exists()) file.readText() else ""
            val entry = "${timestamp(System.currentTimeMillis())} $where\n${stackTrace(throwable).take(4096)}"
            file.writeText(DiagnosticsText.appendTrimmed(existing, entry, MAX_HANDLED_CHARS))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to record handled error", t)
        }
    }

    /**
     *  The crash or abnormal exit not yet offered to the user, or null.
     */
    fun pendingReport(context: Context): String? {
        val crash = File(directory(context), LAST_CRASH).takeIf { it.exists() }?.readText()
        if (crash != null) return crash
        return lastAbnormalExit(context, onlyUnseen = true)
    }

    /**
     *  Marks the pending crash as handled, whether the user sent it or not, so each crash is offered once.
     */
    fun clearPending(context: Context) {
        File(directory(context), LAST_CRASH).delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            latestExits(context).firstOrNull()?.let { markSeen(context, it.timestamp) }
        }
    }

    private fun lastAbnormalExit(context: Context, onlyUnseen: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val lastSeen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_EXIT, 0L)
        val exit = latestExits(context).firstOrNull { info ->
            //A Java crash already has a richer report from the handler, so only the newest-unseen native crash or ANR is offered on its own
            val relevant = info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || info.reason == ApplicationExitInfo.REASON_ANR ||
                (!onlyUnseen && info.reason == ApplicationExitInfo.REASON_CRASH)
            relevant && (!onlyUnseen || info.timestamp > lastSeen)
        } ?: return null
        return buildString {
            appendLine("Abnormal exit at ${timestamp(exit.timestamp)}: ${reasonName(exit.reason)} in ${exit.processName}")
            exit.description?.let { appendLine(it) }
            if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                try {
                    exit.traceInputStream?.bufferedReader()?.use { appendLine(it.readText().take(MAX_TRACE_CHARS)) }
                } catch (e: Exception) {
                    appendLine("ANR trace unavailable: ${e.message}")
                }
            }
        }
    }

    private fun latestExits(context: Context): List<ApplicationExitInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun markSeen(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_EXIT, timestamp).apply()
    }

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        else -> "reason $reason"
    }

    /**
     *  Builds the full redacted report: [header], the pending crash if any, handled errors, the last abnormal exit and DarQ's own log.
     */
    fun buildReport(context: Context, header: String): String {
        val crash = File(directory(context), LAST_CRASH).takeIf { it.exists() }?.readText()
        val handled = File(directory(context), HANDLED_ERRORS).takeIf { it.exists() }?.readText()
        val report = buildString {
            appendLine(header.trim())
            appendLine()
            appendLine("## Last crash")
            appendLine(crash?.trim() ?: "None saved")
            appendLine()
            appendLine("## Last abnormal exit")
            appendLine(lastAbnormalExit(context, onlyUnseen = false)?.trim() ?: "None recorded")
            appendLine()
            appendLine("## Handled errors")
            appendLine(handled?.trim()?.ifBlank { null } ?: "None")
            appendLine()
            appendLine("## Log (DarQ processes, last $LOGCAT_LINES lines)")
            append(readOwnLog())
        }
        return DiagnosticsText.redact(report, packagesToHash(context))
    }

    // NOTE: Every user-installed app is hashed, since the list of apps someone darkens or has installed is personal.
    // NOTE: System apps and the frameworks DarQ depends on stay readable because they help debugging and say nothing about the user.
    private fun packagesToHash(context: Context): Set<String> = try {
        context.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { it.packageName }
            .filterNot { it in READABLE_PACKAGES }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    // NOTE: An app can read the log lines of its own UID without any permission.
    // NOTE: Framework debug chatter fills most of the buffer, so a larger window is read and then filtered down to the useful lines.
    private fun readOwnLog(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_READ_LINES.toString(), "--uid=${Process.myUid()}")
            .redirectErrorStream(true).start()
        DiagnosticsLog.filter(process.inputStream.bufferedReader().use { it.readText() }, LOGCAT_LINES)
    } catch (e: Exception) {
        "Log unavailable: ${e.message}"
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))

}
