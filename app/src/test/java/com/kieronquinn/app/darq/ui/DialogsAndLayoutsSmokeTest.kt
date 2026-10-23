package com.kieronquinn.app.darq.ui

import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.theme.ThemeManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// NOTE: Opens every dialog kind and inflates every layout added for the report, picker, support and credits screens under each app style.
// NOTE: Measuring the decor view creates every row and runs the casts, which a plain show() does not.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DialogsAndLayoutsSmokeTest {

    private val layouts = listOf(
        R.layout.dialog_report_issue,
        R.layout.fragment_bottom_sheet_report,
        R.layout.fragment_bottom_sheet_custom_color,
        R.layout.fragment_bottom_sheet_credits,
        R.layout.fragment_bottom_sheet_donation,
        R.layout.fragment_bottom_sheet_social_links,
        R.layout.fragment_bottom_sheet_color_picker,
        R.layout.item_palette_swatch,
        R.layout.item_color_picker,
        R.layout.item_setting_about
    )

    private fun styles(): List<Pair<String, Context>> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val result = ArrayList<Pair<String, Context>>()
        for (preset in ThemeManager.Preset.values()) {
            result += preset.key to ContextThemeWrapper(base, R.style.AppTheme).apply { theme.applyStyle(preset.overlay, true) }
            result += "${preset.key}+black" to ContextThemeWrapper(base, R.style.AppTheme).apply {
                theme.applyStyle(preset.overlay, true)
                theme.applyStyle(R.style.ThemeOverlay_Darq_PureBlack, true)
            }
        }
        return result
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertTrue(view.measuredHeight > 0)
    }

    @Test
    fun everyDialogKindOpensInEveryStyle() {
        for ((name, context) in styles()) {
            val material = MaterialAlertDialogBuilder(context)
                .setTitle("Title $name").setMessage("Message")
                .setPositiveButton("Yes", null).setNegativeButton("No", null).setNeutralButton("Maybe", null).create()
            material.show()
            measure(material.window!!.decorView)
            material.dismiss()

            val appCompat = androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Title").setMessage("Message").setPositiveButton("Yes", null).create()
            appCompat.show()
            measure(appCompat.window!!.decorView)
            appCompat.dismiss()

            val framework = AlertDialog.Builder(context).setTitle("Title").setMessage("Message").setPositiveButton("Yes", null).create()
            framework.show()
            measure(framework.window!!.decorView)
            framework.dismiss()
        }
    }

    @Test
    fun everyNewLayoutInflatesInEveryStyle() {
        for ((name, context) in styles()) {
            for (layout in layouts) {
                val view = LayoutInflater.from(context).inflate(layout, null)
                assertNotNull("$name layout $layout", view)
                measure(view)
            }
        }
    }

}
