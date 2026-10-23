package com.kieronquinn.app.darq.components.diagnostics

import java.net.URLEncoder
import java.security.MessageDigest

/**
 *  Text handling for diagnostic reports, kept free of Android types so it runs in JVM tests.
 */
object DiagnosticsText {

    const val REPO = "Arora-Sir/DarQ-Reborn"
    const val ISSUES_URL = "https://github.com/$REPO/issues"
    private const val NEW_BUG_URL = "https://github.com/$REPO/issues/new"
    const val BUG_TEMPLATE = "bug_report.yml"

    //Coordinates come from the sunrise and sunset schedule. Any decimal with four or more fraction digits is treated as one.
    private val COORDINATE = Regex("""(?<![\w.])-?\d{1,3}\.\d{4,}(?![\w.])""")
    private val CONTROL = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    /**
     *  Replaces each package in [packagesToHash] with a short stable hash, and removes coordinates and email addresses.
     *  The hash stays the same within a report and across reports, so one app can still be followed through a log without naming it.
     */
    fun redact(text: String, packagesToHash: Collection<String>): String {
        var result = text
        //Longest first, so a package that prefixes another is never replaced inside the longer name
        packagesToHash.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { pkg ->
            val pattern = Regex("""(?<![\w.])${Regex.escape(pkg)}(?![\w])""")
            result = pattern.replace(result, hashPackage(pkg))
        }
        //Raw log output can carry NUL and other control characters, which break XML tools and GitHub text fields
        result = CONTROL.replace(result, "")
        result = COORDINATE.replace(result, "[coordinate]")
        result = EMAIL.replace(result, "[email]")
        return result
    }

    fun hashPackage(pkg: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pkg.toByteArray(Charsets.UTF_8))
        return "app#" + digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private const val FEATURE_TEMPLATE = "feature_request.yml"
    private const val SUMMARY_CHARS = 1500

    /**
     *  The prefilled bug form. Short fields and a log summary go in the URL. The full log goes to the clipboard because URLs have a length limit.
     */
    fun bugReportUrl(appVersion: String, android: String, device: String, mode: String, logSummary: String? = null): String {
        val params = linkedMapOf(
            "template" to BUG_TEMPLATE,
            "app-version" to appVersion,
            "android" to android,
            "device" to device,
            "mode" to mode
        )
        if (logSummary != null) params["logs"] = logSummary
        return NEW_BUG_URL + "?" + params.entries.joinToString("&") { (key, value) -> "$key=" + encode(value) }
    }

    fun featureRequestUrl(mode: String): String =
        "$NEW_BUG_URL?template=$FEATURE_TEMPLATE&mode=" + encode(mode)

    /**
     *  The start of a report (header and last crash) for the form's log field, ending with a note that the full log is on the clipboard.
     */
    fun logSummary(report: String, note: String, maxChars: Int = SUMMARY_CHARS): String {
        val head = report.substringBefore("## Last abnormal exit").trim()
        val cut = if (head.length > maxChars) head.take(maxChars).substringBeforeLast('\n') + "\n..." else head
        return "$cut\n\n$note"
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     *  Keeps a report within the GitHub issue body limit (65536 characters) with room for the rest of the form.
     *  The start (header and crash) and the end (newest log lines) matter most, so the middle is cut.
     */
    fun fitForIssue(report: String, maxChars: Int = 60_000, headChars: Int = 20_000): String {
        if (report.length <= maxChars) return report
        val marker = "\n[... ${report.length - maxChars} characters trimmed ...]\n"
        val tailChars = maxChars - headChars - marker.length
        return report.take(headChars) + marker + report.takeLast(tailChars)
    }

    /**
     *  Appends [entry] and keeps only the newest whole entries within [maxChars], so the handled error log never grows without bound.
     */
    fun appendTrimmed(existing: String, entry: String, maxChars: Int, separator: String = "\n\n"): String {
        val entries = (if (existing.isBlank()) emptyList() else existing.split(separator)) + entry.trim()
        val kept = ArrayDeque<String>()
        var size = 0
        for (item in entries.asReversed()) {
            val cost = item.length + if (kept.isEmpty()) 0 else separator.length
            if (size + cost > maxChars && kept.isNotEmpty()) break
            kept.addFirst(item.take(maxChars))
            size += cost
        }
        return kept.joinToString(separator)
    }

}
