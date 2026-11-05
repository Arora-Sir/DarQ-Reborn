package com.kieronquinn.app.darq.components.theme

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kieronquinn.app.darq.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// NOTE: The language choice must reach every string without touching the OS app locale while the app is visible, so the wrapped context is what these tests check.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLanguageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearChoice() {
        context.getSharedPreferences("darq_language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun noChoiceLeavesTheContextUntouched() {
        assertSame(context, AppLanguage.wrap(context))
    }

    @Test
    fun chosenLanguageDecidesTheStringsOfTheWrappedContext() {
        val expected = mapOf("hi" to "भाषा", "uk" to "Мова", "zh-CN" to "语言", "en" to "Language")
        for ((tag, title) in expected) {
            AppLanguage.set(context, tag)
            assertEquals(title, AppLanguage.wrap(context).getString(R.string.appearance_language_title))
        }
    }

    @Test
    fun everyOfferedLanguageHasAName() {
        val names = context.resources.getStringArray(R.array.appearance_language_names)
        assertEquals(AppLanguage.TAGS.size, names.size)
    }

    @Test
    fun anEmptyOsLocaleNeverWipesTheChoice() {
        AppLanguage.set(context, "uk")
        AppLanguage.adoptSystemChoice(context)
        assertEquals("uk", AppLanguage.tag(context))
    }

}
