package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.github.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

abstract class UpdateDownloadBottomSheetViewModel : ViewModel() {

    abstract val downloadState: Flow<State>

    abstract fun startDownload(context: Context, update: UpdateChecker.Update)
    abstract fun cancelDownload(context: Context)
    abstract fun openPackageInstaller(context: Context, uri: Uri)

    sealed class State {
        object Idle : State()
        data class Downloading(val progress: Int) : State()
        data class Done(val fileUri: Uri) : State()
        data class Failed(val errorMsg: String? = null) : State()
    }
}

class UpdateDownloadBottomSheetViewModelImpl : UpdateDownloadBottomSheetViewModel() {

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "darq_update_download"
    }

    private val okHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private var _downloadState = MutableStateFlow<State>(State.Idle)
    override val downloadState = _downloadState.asStateFlow()

    // ── Notification helpers ──────────────────────────────────────────────

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                Log.d("DarQUpdate", "ensureNotificationChannel: Creating channel: $CHANNEL_ID")
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name) + " Updates",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            } else {
                Log.d("DarQUpdate", "ensureNotificationChannel: Channel $CHANNEL_ID already exists")
            }
        }
    }

    private fun showProgressNotification(context: Context, progress: Int) {
        Log.d("DarQUpdate", "showProgressNotification: progress = $progress")
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Downloading update… $progress%")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showSizeProgressNotification(context: Context, downloadedBytes: Long) {
        Log.d("DarQUpdate", "showSizeProgressNotification: downloadedBytes = $downloadedBytes")
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sizeMB = downloadedBytes / (1024.0 * 1024.0)
        val formattedSize = String.format("%.2f MB", sizeMB)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Downloading update… ($formattedSize)")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletedNotification(context: Context, uri: Uri) {
        Log.d("DarQUpdate", "showCompletedNotification: uri = $uri")
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "update_downloads_complete",
                "Update Complete",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val installIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, installIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(context, "update_downloads_complete")
            .setContentTitle("Update Downloaded")
            .setContentText("Tap to install the update")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        Log.d("DarQUpdate", "showCompletedNotification: Cancelling progress notification ID: $NOTIFICATION_ID, notifying 1001")
        nm.cancel(NOTIFICATION_ID)
        nm.notify(1001, notification)
    }

    private fun cancelNotification(context: Context) {
        Log.d("DarQUpdate", "cancelNotification: Cancelling notification ID: $NOTIFICATION_ID")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    // ── Download logic ────────────────────────────────────────────────────

    override fun startDownload(context: Context, update: UpdateChecker.Update) {
        Log.d("DarQUpdate", "startDownload: Triggered for update: $update")
        _downloadState.value = State.Idle
        val downloadFolder = File(context.cacheDir, "updates")
        val existingFile = File(downloadFolder, update.assetName)
        Log.d("DarQUpdate", "startDownload: File path = ${existingFile.absolutePath}, exists = ${existingFile.exists()}, length = ${existingFile.length()}")
        if (existingFile.exists() && existingFile.length() > 0) {
            Log.d("DarQUpdate", "startDownload: Cached update file found, bypassing network call")
            viewModelScope.launch {
                try {
                    val outputUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        existingFile
                    )
                    Log.d("DarQUpdate", "startDownload: FileProvider URI created: $outputUri")
                    showCompletedNotification(context, outputUri)
                    _downloadState.emit(State.Done(outputUri))
                } catch (e: Exception) {
                    Log.e("DarQUpdate", "startDownload: Error getting FileProvider URI for existing file", e)
                    _downloadState.emit(State.Failed("Cache error: " + e.message))
                }
            }
        } else {
            Log.d("DarQUpdate", "startDownload: No valid cached update file, performing download")
            downloadUpdate(context, update.assetUrl, update.assetName)
        }
    }

    private fun downloadUpdate(context: Context, url: String, fileName: String) {
        Log.d("DarQUpdate", "downloadUpdate: URL = $url, fileName = $fileName")
        viewModelScope.launch {
            _downloadState.emit(State.Downloading(0))
            showProgressNotification(context, 0)

            withContext(Dispatchers.IO) {
                val downloadFolder = File(context.cacheDir, "updates").also {
                    val created = it.mkdirs()
                    Log.d("DarQUpdate", "downloadUpdate: Cache updates folder: exists=${it.exists()} created=$created")
                }
                val outputFile = File(downloadFolder, fileName)
                try {
                    if (outputFile.exists()) {
                        val deleted = outputFile.delete()
                        Log.d("DarQUpdate", "downloadUpdate: Deleted old file: $deleted")
                    }

                    Log.d("DarQUpdate", "downloadUpdate: Preparing OkHttp request...")
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .build()
                    val response = okHttpClient.newCall(request).execute()
                    Log.d("DarQUpdate", "downloadUpdate: OkHttp execution complete. Code = ${response.code}, isSuccessful = ${response.isSuccessful}")

                    if (!response.isSuccessful) {
                        Log.e("DarQUpdate", "downloadUpdate: Network request failed with code: ${response.code}")
                        if (outputFile.exists()) outputFile.delete()
                        cancelNotification(context)
                        _downloadState.emit(State.Failed("HTTP Error: ${response.code}"))
                        return@withContext
                    }

                    val body = response.body ?: run {
                        Log.e("DarQUpdate", "downloadUpdate: Response body is null")
                        if (outputFile.exists()) outputFile.delete()
                        cancelNotification(context)
                        _downloadState.emit(State.Failed("Empty response body"))
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    Log.d("DarQUpdate", "downloadUpdate: totalBytes = $totalBytes")
                    var downloadedBytes = 0L
                    var lastNotifiedProgress = -1
                    var lastNotifiedBytes = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                    _downloadState.emit(State.Downloading(progress))
                                    if (progress >= lastNotifiedProgress + 5) {
                                        lastNotifiedProgress = progress
                                        showProgressNotification(context, progress)
                                    }
                                } else {
                                    _downloadState.emit(State.Downloading(0))
                                    if (downloadedBytes >= lastNotifiedBytes + 500 * 1024) {
                                        lastNotifiedBytes = downloadedBytes
                                        showSizeProgressNotification(context, downloadedBytes)
                                    }
                                }
                            }
                        }
                    }

                    Log.d("DarQUpdate", "downloadUpdate: Byte stream write complete. Total downloaded = $downloadedBytes bytes")

                    val outputUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        outputFile
                    )
                    Log.d("DarQUpdate", "downloadUpdate: FileProvider URI created = $outputUri")
                    showCompletedNotification(context, outputUri)
                    _downloadState.emit(State.Done(outputUri))

                } catch (e: Exception) {
                    Log.e("DarQUpdate", "downloadUpdate: Exception caught during update download", e)
                    try {
                        if (outputFile.exists()) {
                            val deleted = outputFile.delete()
                            Log.d("DarQUpdate", "downloadUpdate: Deleted partially downloaded file: $deleted")
                        }
                    } catch (ex: Exception) {
                        Log.e("DarQUpdate", "downloadUpdate: Error deleting failed file", ex)
                    }
                    cancelNotification(context)
                    _downloadState.emit(State.Failed(e.toString()))
                }
            }
        }
    }

    override fun openPackageInstaller(context: Context, uri: Uri) {
        Log.d("DarQUpdate", "openPackageInstaller: uri = $uri")
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Log.d("DarQUpdate", "openPackageInstaller: Starting activity with intent: $intent")
            context.startActivity(intent)
            Log.d("DarQUpdate", "openPackageInstaller: Activity started successfully")
        } catch (e: Exception) {
            Log.e("DarQUpdate", "openPackageInstaller: Failed to start package installer activity", e)
        }
    }

    override fun cancelDownload(context: Context) {
        Log.d("DarQUpdate", "cancelDownload: Cancel requested by user")
        cancelNotification(context)
        viewModelScope.launch {
            _downloadState.emit(State.Idle)
        }
    }
}

