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
import com.kieronquinn.app.darq.service.autodark.DarqAutoDarkForegroundService
import com.kieronquinn.app.darq.service.autodark.SystemThemeListener
import com.kieronquinn.app.darq.ui.activities.DarqActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.utils.extensions.isDarkTheme
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

    private var systemThemeListener: SystemThemeListener? = null

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
        Intent(this, DarqActivity::class.java).apply {
            putExtra(DarqActivity.EXTRA_OPEN_ADVANCED_SETTINGS, true)
        }.let { notificationIntent ->
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
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
        refreshSystemThemeListener()
    }

    /**
     *  Registers/unregisters [SystemThemeListener] to match the current Auto Dark schedule mode.
     *  Idempotent - safe to call on every onCreate/onStartCommand, including repeated
     *  startForegroundService calls on an already-running service.
     */
    private fun refreshSystemThemeListener() {
        val shouldListen = settings.autoDarkScheduleMode == 3
        if (shouldListen && systemThemeListener == null) {
            val listener = SystemThemeListener(applicationContext) { isDark ->
                startForegroundService(Intent(this, DarqAutoDarkForegroundService::class.java).apply {
                    putExtra(DarqAutoDarkForegroundService.KEY_ENABLE_DARK, isDark)
                })
            }
            applicationContext.registerComponentCallbacks(listener)
            systemThemeListener = listener
            Log.d(TAG, "Registered SystemThemeListener")
        } else if (!shouldListen && systemThemeListener != null) {
            applicationContext.unregisterComponentCallbacks(systemThemeListener)
            systemThemeListener = null
            Log.d(TAG, "Unregistered SystemThemeListener")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshSystemThemeListener()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_PERSISTENT,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_PERSISTENT, notification)
        }

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
            if (result is DarqServiceConnectionProvider.ServiceResult.Success && settings.autoDarkTheme) {
                try {
                    val intent = Intent(this@DarqPersistentService, DarqAutoDarkForegroundService::class.java)
                        .putExtra(DarqAutoDarkForegroundService.KEY_JUST_RESCHEDULE, true)
                    if (settings.autoDarkScheduleMode == 3) {
                        // Re-sync immediately to whatever the system theme currently is, in case
                        // it changed while this process was dead and the live listener wasn't
                        // registered to observe it.
                        intent.putExtra(DarqAutoDarkForegroundService.KEY_ENABLE_DARK, applicationContext.isDarkTheme)
                    }
                    startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start auto dark foreground service from persistent service", e)
                }
            }
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
        systemThemeListener?.let { applicationContext.unregisterComponentCallbacks(it) }
        systemThemeListener = null
        super.onDestroy()
    }
}
