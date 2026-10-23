package com.kieronquinn.app.darq.components.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextTest {

    @Test
    fun packagesAreHashedStablyAndClassNamesSurvive() {
        val log = "Enabled apps com.example.bank, com.example.bank.beta\nat com.kieronquinn.app.darq.Xposed.hook(Xposed.kt:12)"
        val redacted = DiagnosticsText.redact(log, listOf("com.example.bank", "com.example.bank.beta"))
        assertFalse(redacted.contains("com.example.bank"))
        assertTrue(redacted.contains(DiagnosticsText.hashPackage("com.example.bank")))
        assertTrue(redacted.contains(DiagnosticsText.hashPackage("com.example.bank.beta")))
        assertTrue(redacted.contains("com.kieronquinn.app.darq.Xposed.hook"))
        assertEquals(DiagnosticsText.hashPackage("a.b"), DiagnosticsText.hashPackage("a.b"))
    }

    @Test
    fun packageInsideALongerIdentifierIsLeftAlone() {
        val redacted = DiagnosticsText.redact("org.example.appx and org.example.app", listOf("org.example.app"))
        assertTrue(redacted.startsWith("org.example.appx and app#"))
    }

    @Test
    fun coordinatesAndEmailsAreRemovedButVersionsStay() {
        val redacted = DiagnosticsText.redact("loc 28.613939,77.209023 by someone@example.com on 3.2.0 api 36 ratio 0.5", emptyList())
        assertEquals("loc [coordinate],[coordinate] by [email] on 3.2.0 api 36 ratio 0.5", redacted)
    }

    @Test
    fun bugUrlPrefillsOnlyShortFields() {
        val url = DiagnosticsText.bugReportUrl("3.2.0 (330)", "Android 16 (API 36)", "Samsung SM-S928B", "Shizuku")
        assertEquals(
            "https://github.com/Arora-Sir/DarQ-Reborn/issues/new?template=bug_report.yml&app-version=3.2.0%20%28330%29" +
                "&android=Android%2016%20%28API%2036%29&device=Samsung%20SM-S928B&mode=Shizuku",
            url
        )
    }

    @Test
    fun handledLogKeepsNewestWholeEntries() {
        var log = ""
        for (i in 1..5) log = DiagnosticsText.appendTrimmed(log, "entry-$i " + "x".repeat(10), maxChars = 60)
        val entries = log.split("\n\n")
        assertEquals("entry-5", entries.last().substringBefore(' '))
        assertTrue(log.length <= 60)
        assertTrue(entries.all { it.startsWith("entry-") })
    }

    @Test
    fun longReportsKeepHeadAndNewestLinesWithinTheIssueLimit() {
        val report = "HEADER" + "m".repeat(100_000) + "NEWEST"
        val fitted = DiagnosticsText.fitForIssue(report)
        assertEquals(60_000, fitted.length)
        assertTrue(fitted.startsWith("HEADER"))
        assertTrue(fitted.endsWith("NEWEST"))
        assertTrue(fitted.contains("characters trimmed"))
        assertEquals("short", DiagnosticsText.fitForIssue("short"))
    }

    @Test
    fun summaryKeepsHeaderAndCrashAndEndsWithTheNote() {
        val report = "# Header\nApp: 3.2.0\n\n## Last crash\nboom\n\n## Last abnormal exit\nnone\n\n## Log\nline"
        val summary = DiagnosticsText.logSummary(report, "Full log on the clipboard")
        assertEquals("# Header\nApp: 3.2.0\n\n## Last crash\nboom\n\nFull log on the clipboard", summary)
        val long = DiagnosticsText.logSummary("x\n".repeat(2000), "note", maxChars = 100)
        assertTrue(long.length < 120)
        assertTrue(long.endsWith("...\n\nnote"))
    }

    @Test
    fun bugUrlCarriesTheSummaryAndFeatureUrlUsesItsTemplate() {
        val url = DiagnosticsText.bugReportUrl("v", "a", "d", "m", "line one\nline two")
        assertTrue(url.endsWith("&logs=line%20one%0Aline%20two"))
        assertEquals("https://github.com/Arora-Sir/DarQ-Reborn/issues/new?template=feature_request.yml&mode=Root", DiagnosticsText.featureRequestUrl("Root"))
    }

    @Test
    fun controlCharactersAreRemovedButLinesAndTabsStay() {
        assertEquals("a\tb\nc", DiagnosticsText.redact("a\tb\u0000\nc\u0007", emptyList()))
    }

    @Test
    fun oversizedSingleEntryIsCutNotDropped() {
        val log = DiagnosticsText.appendTrimmed("", "y".repeat(100), maxChars = 40)
        assertEquals(40, log.length)
    }

}
