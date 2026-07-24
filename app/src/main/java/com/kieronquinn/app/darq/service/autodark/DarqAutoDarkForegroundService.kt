package com.kieronquinn.app.darq.service.autodark

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.model.location.LatLng
import com.kieronquinn.app.darq.model.settings.IPCSetting
import com.kieronquinn.app.darq.model.xposed.XposedSelfHooks
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider
import com.kieronquinn.app.darq.ui.activities.DarqActivity
import com.kieronquinn.app.darq.utils.AutoDarkUtils
import com.kieronquinn.app.darq.utils.TimeZoneUtils
import com.kieronquinn.app.darq.work.DarqSunriseSunsetWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.shredzone.commons.suncalc.SunTimes
import java.util.*
import java.util.concurrent.TimeUnit

class DarqAutoDarkForegroundService: LifecycleService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_AUTO_DARK = "channel_auto_dark"
        private const val NOTIFICATION_ID_AUTO_DARK = 1003
        const val KEY_ENABLE_DARK = "enable_dark"
        const val KEY_JUST_RESCHEDULE = "just_reschedule"
        private const val REQUEST_CODE_CUSTOM_START = 2001
        private const val REQUEST_CODE_CUSTOM_END = 2002
        private const val REQUEST_CODE_SUNSET = 2003
        private const val REQUEST_CODE_SUNRISE = 2004
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val alarmManager by lazy {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private val launchIntent by lazy {
        Intent(this, DarqActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private val workManager by lazy {
        WorkManager.getInstance(this)
    }

    private val serviceProvider by inject<DarqServiceConnectionProvider>()
    private val settings by inject<DarqSharedPreferences>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_AUTO_DARK,
                getString(R.string.notification_channel_boot_auto_dark_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                this.description = getString(R.string.notification_channel_boot_auto_dark_description)
            })

        val justReschedule = intent?.getBooleanExtra(KEY_JUST_RESCHEDULE, false) ?: false
        val notificationContent = if(justReschedule){
            R.string.auto_dark_foreground_notification_content_changes
        }else{
            R.string.auto_dark_foreground_notification_content
        }

        val notification: Notification =
            Notification.Builder(this, NOTIFICATION_CHANNEL_AUTO_DARK)
                .setContentTitle(getText(R.string.auto_dark_foreground_notification_title))
                .setContentText(getText(notificationContent))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(launchIntent)
                .setTicker(getText(notificationContent))
                .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_AUTO_DARK,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_AUTO_DARK, notification)
        }

        lifecycleScope.launchWhenCreated {
            when (settings.autoDarkScheduleMode) {
                0 -> {
                    // Off mode: cancel any scheduled jobs & alarms, then restore pre-auto-dark state
                    cancelAllScheduledAlarms()
                    workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNSET)
                    workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNRISE)
                    restorePreAutoDarkState()
                }
                2 -> {
                    // Custom Schedule Mode: always evaluate state for current time
                    val isDark = AutoDarkUtils.isCustomScheduleDark(settings.autoDarkStartTime, settings.autoDarkEndTime)
                    applySchedulePeriod(isDark)
                    cancelAndScheduleCustomWork()
                }
                else -> {
                    // Sunset/Sunrise Mode (mode 1)
                    if(!justReschedule || intent?.hasExtra(KEY_ENABLE_DARK) == true) {
                        val enableDark = intent?.getBooleanExtra(KEY_ENABLE_DARK, false) ?: false
                        applySchedulePeriod(enableDark)
                    }
                    val nextTriggerTimes = getNextTriggerTimes()
                    if(nextTriggerTimes != null) {
                        cancelAndScheduleWork(nextTriggerTimes)
                    }
                }
            }
            stopForeground(true)
            stopSelf()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     *  Applies the Auto Dark schedule period state (dark or light) to the DarQ service.
     *  Critically, this writes ONLY to settings.autoDarkManagedEnabled - it NEVER touches
     *  settings.enabled (the user's master DarQ toggle). This prevents the schedule from
     *  corrupting the user's manual DarQ on/off state.
     *
     *  For System & DarQ mode (autoDarkTargetMode == 0): also saves the system night mode before
     *  first activation so it can be restored when Auto Dark is disabled.
     */
    private suspend fun applySchedulePeriod(isDark: Boolean) = withContext(Dispatchers.IO) {
        val serviceResult = serviceProvider.getService()
        val isServiceConnected = serviceResult is DarqServiceConnectionProvider.ServiceResult.Success

        if (settings.autoDarkTargetMode == 0) {
            if (isServiceConnected) {
                val svc = (serviceResult as DarqServiceConnectionProvider.ServiceResult.Success).service
                // Save system night mode BEFORE Auto Dark first takes control
                if (!settings.autoDarkHasManaged) {
                    settings.preAutoDarkSystemNightMode = svc.isNightModeActive()
                    settings.autoDarkHasManaged = true
                }
                svc.setNightMode(isDark)
            } else {
                showFailedNotification()
            }
        }

        // Write ONLY to autoDarkManagedEnabled - the user's settings.enabled is untouched
        settings.autoDarkManagedEnabled = isDark
        if (isServiceConnected) {
            val ipcService = (serviceResult as DarqServiceConnectionProvider.ServiceResult.Success).service
            val isXposed = XposedSelfHooks.isXposedModuleEnabled()
            ipcService.notifySettingsChange(IPCSetting(
                autoDarkManagedEnabled = isDark,
                isXposedActive = isXposed
            ))
        } else if (settings.autoDarkTargetMode == 1) {
            showFailedNotification()
        }
    }

    /**
     *  Restores the pre-auto-dark state when Auto Dark Schedule is disabled by the user.
     *  - For System & DarQ mode: restores the system night mode to what it was before Auto Dark
     *    first activated.
     *  - Always: clears autoDarkManagedEnabled (sets to true = no longer blocking) and sends
     *    the IPC update so DarqService immediately re-enables force dark for the foreground app.
     */
    private suspend fun restorePreAutoDarkState() = withContext(Dispatchers.IO) {
        if (!settings.autoDarkHasManaged) return@withContext

        val serviceResult = serviceProvider.getService()
        val isServiceConnected = serviceResult is DarqServiceConnectionProvider.ServiceResult.Success

        if (isServiceConnected) {
            val svc = (serviceResult as DarqServiceConnectionProvider.ServiceResult.Success).service
            // Restore system night mode for System & DarQ mode
            if (settings.autoDarkTargetMode == 0) {
                svc.setNightMode(settings.preAutoDarkSystemNightMode)
            }
            // Clear the Auto Dark blocking flag so force dark resumes immediately
            settings.autoDarkManagedEnabled = true
            // Reset tracking flags
            settings.autoDarkHasManaged = false
            settings.preAutoDarkSystemNightMode = false
            // Notify DarqService to re-evaluate foreground app and restore force dark
            val isXposed = XposedSelfHooks.isXposedModuleEnabled()
            svc.notifySettingsChange(IPCSetting(
                autoDarkManagedEnabled = true,
                isXposedActive = isXposed
            ))
        } else {
            // Service not connected - still clear the pref so it doesn't block on next reconnect
            settings.autoDarkManagedEnabled = true
            settings.autoDarkHasManaged = false
            settings.preAutoDarkSystemNightMode = false
        }
    }

    private suspend fun getNextTriggerTimes(): SunTimes? = withContext(Dispatchers.IO){
        val latLng = if(settings.useLocation){
            val service = serviceProvider.getService()
            if(service is DarqServiceConnectionProvider.ServiceResult.Success){
                service.service.location ?: getTimezoneLocation() ?: return@withContext null
            }else getTimezoneLocation() ?: return@withContext null
        }else getTimezoneLocation() ?: return@withContext null
        SunTimes.compute().on(Calendar.getInstance()).at(latLng.latitude, latLng.longitude).execute()
    }

    /**
     *  Gets the user's country location using their timezone. This is nowhere near accurate for
     *  location use, but is good *enough* for sunrise / sunset
     */
    private fun getTimezoneLocation(): LatLng? {
        return TimeZoneUtils.getLatLngForTimezone(this, TimeZone.getDefault())
    }

    private fun cancelAllScheduledAlarms() {
        val intent = Intent(this, com.kieronquinn.app.darq.receivers.DarqAlarmReceiver::class.java)
        listOf(REQUEST_CODE_CUSTOM_START, REQUEST_CODE_CUSTOM_END, REQUEST_CODE_SUNSET, REQUEST_CODE_SUNRISE).forEach { code ->
            val pendingIntent = PendingIntent.getBroadcast(this, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun cancelAndScheduleCustomWork() {
        workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNSET)
        workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNRISE)

        val startIntent = Intent(this, com.kieronquinn.app.darq.receivers.DarqAlarmReceiver::class.java).apply {
            putExtra(KEY_ENABLE_DARK, true)
        }
        val startPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_CUSTOM_START,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, com.kieronquinn.app.darq.receivers.DarqAlarmReceiver::class.java).apply {
            putExtra(KEY_ENABLE_DARK, false)
        }
        val endPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_CUSTOM_END,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(startPendingIntent)
        alarmManager.cancel(endPendingIntent)

        if(settings.autoDarkTheme) {
            val sunsetDelay = com.kieronquinn.app.darq.utils.AutoDarkUtils.calculateNextDelayMillis(settings.autoDarkStartTime)
            val sunriseDelay = com.kieronquinn.app.darq.utils.AutoDarkUtils.calculateNextDelayMillis(settings.autoDarkEndTime)

            val startTriggerTime = System.currentTimeMillis() + sunsetDelay
            val endTriggerTime = System.currentTimeMillis() + sunriseDelay

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startTriggerTime, startPendingIntent)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTriggerTime, endPendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startTriggerTime, startPendingIntent)
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endTriggerTime, endPendingIntent)
            }

            val sunsetWork = OneTimeWorkRequestBuilder<DarqSunriseSunsetWork>()
                .setInitialDelay(sunsetDelay, TimeUnit.MILLISECONDS)
                .addTag(DarqSunriseSunsetWork.TAG_SUNSET).build()
            workManager.enqueue(sunsetWork)

            val sunriseWork = OneTimeWorkRequestBuilder<DarqSunriseSunsetWork>()
                .setInitialDelay(sunriseDelay, TimeUnit.MILLISECONDS)
                .addTag(DarqSunriseSunsetWork.TAG_SUNRISE).build()
            workManager.enqueue(sunriseWork)
        }
    }

    /**
     *  Schedules the sunrise/sunset work for a given set of SunTimes
     *  This gets called every sunrise/sunset, somewhat counterintuitively, but that way it
     *  will stay at least semi-up-to-date for location changes (ie. only one sunrise/sunset would
     *  be out if the user changes timezones)
     */
    private fun cancelAndScheduleWork(sunTimes: SunTimes){
        workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNSET)
        workManager.cancelAllWorkByTag(DarqSunriseSunsetWork.TAG_SUNRISE)

        val sunsetIntent = Intent(this, com.kieronquinn.app.darq.receivers.DarqAlarmReceiver::class.java).apply {
            putExtra(KEY_ENABLE_DARK, true)
        }
        val sunsetPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_SUNSET,
            sunsetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sunriseIntent = Intent(this, com.kieronquinn.app.darq.receivers.DarqAlarmReceiver::class.java).apply {
            putExtra(KEY_ENABLE_DARK, false)
        }
        val sunrisePendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_SUNRISE,
            sunriseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(sunsetPendingIntent)
        alarmManager.cancel(sunrisePendingIntent)

        if(settings.autoDarkTheme) {
            val now = System.currentTimeMillis()
            val sunriseTime = sunTimes.rise?.time
            val sunsetTime = sunTimes.set?.time

            if (sunriseTime != null && sunriseTime > now) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sunriseTime, sunrisePendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, sunriseTime, sunrisePendingIntent)
                }
                val sunriseDelay = sunriseTime - now
                val sunriseWork = OneTimeWorkRequestBuilder<DarqSunriseSunsetWork>()
                    .setInitialDelay(sunriseDelay, TimeUnit.MILLISECONDS)
                    .addTag(DarqSunriseSunsetWork.TAG_SUNRISE).build()
                workManager.enqueue(sunriseWork)
            }
            if (sunsetTime != null && sunsetTime > now) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sunsetTime, sunsetPendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, sunsetTime, sunsetPendingIntent)
                }
                val sunsetDelay = sunsetTime - now
                val sunsetWork = OneTimeWorkRequestBuilder<DarqSunriseSunsetWork>()
                    .setInitialDelay(sunsetDelay, TimeUnit.MILLISECONDS)
                    .addTag(DarqSunriseSunsetWork.TAG_SUNSET).build()
                workManager.enqueue(sunsetWork)
            }
        }
    }

    private fun showFailedNotification() {
        val notification: Notification =
            Notification.Builder(this, NOTIFICATION_CHANNEL_AUTO_DARK)
                .setContentTitle(getText(R.string.auto_dark_foreground_notification_failed_title))
                .setContentText(getText(R.string.auto_dark_foreground_notification_failed_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(launchIntent)
                .setAutoCancel(true)
                .setStyle(Notification.BigTextStyle().bigText(getString(R.string.auto_dark_foreground_notification_failed_content)))
                .setTicker(getText(R.string.auto_dark_foreground_notification_failed_content))
                .build()
        notificationManager.notify(NOTIFICATION_ID_AUTO_DARK, notification)
    }

}