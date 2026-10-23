package com.kieronquinn.app.darq.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 *  The only way DarQ shows a toast. A new message cancels the one still on screen, so toasts never queue up behind each other.
 *  Safe to call from any thread.
 */
object Toasts {

    private val handler = Handler(Looper.getMainLooper())
    private var current: Toast? = null

    fun show(context: Context, @StringRes message: Int, long: Boolean = false) {
        show(context, context.applicationContext.getString(message), long)
    }

    fun show(context: Context, message: CharSequence, long: Boolean = false) {
        val appContext = context.applicationContext
        val run = {
            current?.cancel()
            current = Toast.makeText(appContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).also { it.show() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else handler.post(run)
    }

}
