package com.kieronquinn.app.darq.res

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// NOTE: The strings added for 3.2.0 must exist in every shipped translation, so no new screen falls back to English by accident.
class TranslationCoverageTest {

    private val prefixes = listOf(
        "appearance_", "report_issue_", "diagnostics_", "support_", "credits_", "status_", "item_obtainium", "bottom_sheet_update_obtainium",
        "app_picker_clear_search", "safe_run_failed", "link_no_browser", "item_appearance_", "item_report_issue_"
    )

    private fun keys(path: String): Set<String> {
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return Regex("""<(?:string|string-array|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun ukrainianAndChineseCoverEveryNewKey() {
        val default = keys("src/main/res/values/strings.xml").filter { key -> prefixes.any { key.startsWith(it) } }
        assertTrue(default.size > 60)
        for (folder in listOf("values-uk", "values-zh-rCN")) {
            val translated = keys("src/main/res/$folder/strings.xml")
            val missing = default.filterNot { it in translated }
            assertTrue("$folder is missing $missing", missing.isEmpty())
        }
    }

}
