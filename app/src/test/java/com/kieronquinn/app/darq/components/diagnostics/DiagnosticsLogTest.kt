package com.kieronquinn.app.darq.components.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsLogTest {

    private fun line(level: Char, tag: String, message: String) = "10-23 12:00:01.123  1234  1234 $level $tag: $message"

    @Test
    fun darqTagsAreKeptAtAnyLevel() {
        val raw = listOf(
            line('D', "DarqService", "bound"),
            line('I', "DarQSettings", "saved"),
            line('V', "darq-misc", "verbose")
        ).joinToString("\n")
        assertEquals(raw, DiagnosticsLog.filter(raw))
    }

    @Test
    fun frameworkChatterIsDroppedButWarningsAndErrorsSurvive() {
        val raw = listOf(
            line('D', "VRI[DarqActivity]", "draw"),
            line('D', "ViewRootImpl", "setView"),
            line('I', "Choreographer", "Skipped 3 frames"),
            line('D', "OpenGLRenderer", "debug"),
            line('W', "ViewRootImpl", "warning stays"),
            line('E', "AndroidRuntime", "FATAL EXCEPTION")
        ).joinToString("\n")
        val filtered = DiagnosticsLog.filter(raw)
        assertFalse(filtered.contains("setView"))
        assertFalse(filtered.contains("Skipped 3 frames"))
        assertTrue(filtered.contains("warning stays"))
        assertTrue(filtered.contains("FATAL EXCEPTION"))
    }

    @Test
    fun stackTraceContinuationFollowsItsHeaderLine() {
        val raw = listOf(
            line('E', "AndroidRuntime", "FATAL EXCEPTION"),
            "java.lang.IllegalStateException: boom",
            "\tat com.example.Foo.bar(Foo.kt:1)",
            line('D', "Choreographer", "dropped"),
            "\tat chatter.continuation(X.kt:2)"
        ).joinToString("\n")
        val filtered = DiagnosticsLog.filter(raw)
        assertTrue(filtered.contains("Foo.bar"))
        assertFalse(filtered.contains("chatter.continuation"))
    }

    @Test
    fun bufferMarkersAreDroppedAndNewestLinesWin() {
        val lines = (1..10).map { line('W', "Tag", "message $it") }
        val raw = "--------- beginning of main\n" + lines.joinToString("\n")
        val filtered = DiagnosticsLog.filter(raw, maxLines = 3)
        assertEquals(listOf("message 8", "message 9", "message 10"), filtered.lines().map { it.substringAfter(": ") })
    }

    @Test
    fun reportSplitsIntoPreviewSections() {
        val report = """
            # DarQ Reborn diagnostic report
            App: 3.2.0
            Mode: Shizuku

            ## Last crash
            None saved

            ## Last abnormal exit
            None recorded

            ## Handled errors
            None

            ## Log (DarQ processes, last 800 lines)
            line one
        """.trimIndent()
        val sections = DiagnosticsLog.sections(report)
        assertEquals("App: 3.2.0\nMode: Shizuku", sections.summary)
        assertTrue(DiagnosticsLog.isEmptySection(sections.lastCrash))
        assertTrue(DiagnosticsLog.isEmptySection(sections.abnormalExit))
        assertTrue(DiagnosticsLog.isEmptySection(sections.handledErrors))
        assertEquals("line one", sections.log)
    }

}
