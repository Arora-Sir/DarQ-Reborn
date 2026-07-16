package com.kieronquinn.app.darq.service.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider
import com.kieronquinn.app.darq.ui.activities.DarqActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.utils.extensions.isShizukuInstalled
import com.kieronquinn.app.darq.utils.extensions.Shizuku_awaitBinderReceived
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class DarqPersistentService : LifecycleService() {

    companion object {
        private const val TAG = "DarqPersistentService"
        const val NOTIFICATION_CHANNEL_PERSISTENT = "channel_persistent"
        private const val NOTIFICATION_ID_PERSISTENT = 1004
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val connectionProvider by inject<DarqServiceConnectionProvider>()
    private val settings by inject<DarqSharedPreferences>()
    
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received. Attempting to bind service...")
        lifecycleScope.launch {
            bindService()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder died.")
    }

    private val launchIntent by lazy {
        Intent(this, DarqActivity::class.java).let { notificationIntent ->
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            PendingIntent.getActivity(this, 0, notificationIntent, flags)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        if (!Shell.rootAccess() && isShizukuInstalled()) {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_PERSISTENT,
                    getString(R.string.persistent_foreground_service_notification_title),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.persistent_foreground_service_notification_content)
                }
            )
        }

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_PERSISTENT)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle(getString(R.string.persistent_foreground_service_notification_title))
            .setContentText(getString(R.string.persistent_foreground_service_notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(launchIntent)
            .setTicker(getString(R.string.persistent_foreground_service_notification_content))
            .build()

        startForeground(NOTIFICATION_ID_PERSISTENT, notification)

        lifecycleScope.launch {
            try {
                if (!Shell.rootAccess() && isShizukuInstalled() && settings.bootWaitShizuku) {
                    if (!Shizuku.pingBinder()) {
                        Log.d(TAG, "Shizuku binder is not ready, waiting for up to 3 minutes...")
                        withTimeoutOrNull(180000L) {
                            Shizuku_awaitBinderReceived()
                        }
                        Log.d(TAG, "Finished waiting for Shizuku binder. Ready: ${Shizuku.pingBinder()}")
                    }
                }
                bindService()
            } catch (e: Exception) {
                Log.e(TAG, "Error binding to service in background", e)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun bindService() {
        try {
            Log.d(TAG, "Binding to Shizuku/Root service to keep it alive...")
            val result = connectionProvider.getService()
            Log.d(TAG, "Service binding result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (!Shell.rootAccess() && isShizukuInstalled()) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
        super.onDestroy()
    }
}
