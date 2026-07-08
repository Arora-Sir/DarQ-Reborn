package com.kieronquinn.app.darq.receivers

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.R
import java.io.File

class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedDownloadId = sharedPrefs.getLong("update_download_id", -1L)
        val savedFilename = sharedPrefs.getString("update_download_filename", "") ?: ""

        if (downloadId == savedDownloadId && savedFilename.isNotEmpty()) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            var success = false
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true
                    }
                }
                cursor.close()
            }

            if (success) {
                // Clear the saved state
                sharedPrefs.edit()
                    .remove("update_download_id")
                    .remove("update_download_filename")
                    .apply()

                // Rebuild output file
                val downloadFolder = File(context.externalCacheDir, "updates")
                val downloadFile = File(downloadFolder, savedFilename)
                if (downloadFile.exists()) {
                    val outputUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", downloadFile)
                    
                    // Show Notification to install the update
                    showInstallNotification(context, outputUri)
                }
            }
        }
    }

    private fun showInstallNotification(context: Context, outputUri: Uri) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "update_downloads",
                "Update Downloads",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val installIntent = Intent(Intent.ACTION_VIEW, outputUri).apply {
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            setDataAndType(outputUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, installIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(context, "update_downloads")
            .setContentTitle("Update Downloaded")
            .setContentText("Tap to install the update")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1001, notification)
    }
}
