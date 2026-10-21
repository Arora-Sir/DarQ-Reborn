package com.kieronquinn.app.darq.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 *  Crashes the app on the main thread with the message passed in the "message" extra.
 *  Guarded by the DUMP permission, so only adb shell (or the system) can send it.
 */
class TestCrashReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "DarQ test crash"
        Handler(Looper.getMainLooper()).post { throw IllegalStateException(message) }
    }

}
