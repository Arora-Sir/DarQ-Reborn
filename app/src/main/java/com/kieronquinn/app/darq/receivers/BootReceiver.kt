package com.kieronquinn.app.darq.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.service.background.DarqPersistentService
import com.kieronquinn.app.darq.service.boot.BootForegroundService
import org.koin.core.context.GlobalContext

class BootReceiver: BroadcastReceiver() {

    companion object {
        private val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        //An app update cancels DarQ's alarms and stops its services just like a reboot does,
        //so MY_PACKAGE_REPLACED takes the same path. Both broadcasts may start foreground services.
        if(context == null || intent?.action !in HANDLED_ACTIONS) return
        val settings = GlobalContext.get().get<DarqSharedPreferences>()
        android.util.Log.i("BootReceiver", "Starting services after ${intent?.action}")
        try {
            if (settings.persistentService) {
                context.startForegroundService(Intent(context, DarqPersistentService::class.java))
            } else {
                context.startForegroundService(Intent(context, BootForegroundService::class.java))
            }
        } catch (e: Exception) {
            android.util.Log.e("BootReceiver", "Failed to start service on boot", e)
        }
    }

}