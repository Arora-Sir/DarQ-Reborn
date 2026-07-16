package com.kieronquinn.app.darq.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.service.background.DarqPersistentService
import com.kieronquinn.app.darq.service.boot.BootForegroundService
import org.koin.core.context.GlobalContext

class TriggerReceiver: BroadcastReceiver() {

    companion object {
        private const val TAG = "TriggerReceiver"
        const val ACTION_START_SERVICE = "com.kieronquinn.app.darq.ACTION_START_SERVICE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_START_SERVICE) return
        Log.d(TAG, "Received trigger broadcast to start service")
        val settings = GlobalContext.get().get<DarqSharedPreferences>()
        if (settings.persistentService) {
            context.startForegroundService(Intent(context, DarqPersistentService::class.java))
        } else {
            context.startForegroundService(Intent(context, BootForegroundService::class.java))
        }
    }
}
