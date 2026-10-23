package com.kieronquinn.app.darq.utils

import android.content.Context
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.diagnostics.CrashReporter

// NOTE: Guards a user action at its dispatch point, so a failure shows a short message and reaches the next report instead of closing the app.
// NOTE: It cannot cover callbacks that run later, so it is a second layer behind the tests and never the main defence.
object SafeRun {

    fun run(context: Context, where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            CrashReporter.recordHandled(context.applicationContext, where, e)
            Toasts.show(context, R.string.safe_run_failed)
        }
    }

}
