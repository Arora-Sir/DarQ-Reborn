package com.kieronquinn.app.darq.components.diagnostics

/**
 *  Log filtering and report sectioning, kept free of Android types so it runs in JVM tests.
 */
object DiagnosticsLog {

    // NOTE: A threadtime line looks like "10-23 12:00:01.123  1234  1234 W Tag: message".
    private val THREADTIME = Regex("""^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\s+\d+\s+\d+\s+([VDIWEF])\s+(.*?)\s*: """)
    private val BUFFER_MARKER = Regex("""^-+ beginning of \w+""")
    private val KEEP_LEVELS = setOf('W', 'E', 'F')

    /**
     *  Keeps DarQ's own tags at any level and warnings, errors and fatals from any tag, then the newest [maxLines].
     *  Lines that do not start with a log header, such as a stack trace continuation, follow the line before them.
     */
    fun filter(raw: String, maxLines: Int = 800): String {
        val kept = ArrayList<String>()
        var keepContinuation = false
        for (line in raw.lineSequence()) {
            if (line.isBlank() || BUFFER_MARKER.containsMatchIn(line)) continue
            val match = THREADTIME.find(line)
            if (match == null) {
                if (keepContinuation) kept.add(line)
                continue
            }
            val level = match.groupValues[1][0]
            val tag = match.groupValues[2]
            keepContinuation = isDarqTag(tag) || level in KEEP_LEVELS
            if (keepContinuation) kept.add(line)
        }
        return kept.takeLast(maxLines).joinToString("\n")
    }

    fun isDarqTag(tag: String) = tag.startsWith("darq", ignoreCase = true)

    /**
     *  A report split at its "## " headings into the parts the preview shows.
     */
    data class Sections(
        val summary: String,
        val lastCrash: String,
        val abnormalExit: String,
        val handledErrors: String,
        val log: String
    )

    fun sections(report: String): Sections {
        val parts = Regex("(?m)^## ").split(report)
        fun part(prefix: String) = parts.firstOrNull { it.startsWith(prefix) }
            ?.substringAfter('\n', "")?.trim().orEmpty()
        return Sections(
            summary = parts.firstOrNull().orEmpty().trim().lineSequence().filterNot { it.startsWith("# ") }.joinToString("\n").trim(),
            lastCrash = part("Last crash"),
            abnormalExit = part("Last abnormal exit"),
            handledErrors = part("Handled errors"),
            log = part("Log")
        )
    }

    private val NONE_VALUES = setOf("", "None", "None saved", "None recorded")

    fun isEmptySection(value: String) = value.trim() in NONE_VALUES

}
