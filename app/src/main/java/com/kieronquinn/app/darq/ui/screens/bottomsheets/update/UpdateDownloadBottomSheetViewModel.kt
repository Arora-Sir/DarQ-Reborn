package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.github.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

abstract class UpdateDownloadBottomSheetViewModel : ViewModel() {

    abstract val downloadState: Flow<State>

    abstract fun startDownload(context: Context, update: UpdateChecker.Update)
    abstract fun cancelDownload(context: Context)
    abstract fun openPackageInstaller(context: Context, uri: Uri)

    sealed class State {
        object Idle : State()
        data class Downloading(val progress: Int) : State()
        data class Done(val fileUri: Uri) : State()
        object Failed : State()
    }
}

class UpdateDownloadBottomSheetViewModelImpl : UpdateDownloadBottomSheetViewModel() {

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "darq_update_download"
    }

    private var _downloadState = MutableStateFlow<State>(State.Idle)
    override val downloadState = _downloadState.asStateFlow()

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // ── Notification helpers ──────────────────────────────────────────────

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name) + " Updates",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun showProgressNotification(context: Context, progress: Int) {
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

    private fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    // ── Download logic ────────────────────────────────────────────────────

    override fun startDownload(context: Context, update: UpdateChecker.Update) {
        if (_downloadState.value is State.Idle) {
            val downloadFolder = File(context.cacheDir, "updates")
            val existingFile = File(downloadFolder, update.assetName)
            if (existingFile.exists() && existingFile.length() > 0) {
                viewModelScope.launch {
                    val outputUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        existingFile
                    )
                    _downloadState.emit(State.Done(outputUri))
                }
            } else {
                downloadUpdate(context, update.assetUrl, update.assetName)
            }
        }
    }

    private fun downloadUpdate(context: Context, url: String, fileName: String) {
        viewModelScope.launch {
            _downloadState.emit(State.Downloading(0))
            showProgressNotification(context, 0)

            withContext(Dispatchers.IO) {
                try {
                    val downloadFolder = File(context.cacheDir, "updates").also { it.mkdirs() }
                    val outputFile = File(downloadFolder, fileName)

                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        cancelNotification(context)
                        _downloadState.emit(State.Failed)
                        return@withContext
                    }

                    val body = response.body() ?: run {
                        cancelNotification(context)
                        _downloadState.emit(State.Failed)
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    var lastNotifiedProgress = -1
                    var lastNotifiedBytes = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
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

                    cancelNotification(context)
                    val outputUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        outputFile
                    )
                    _downloadState.emit(State.Done(outputUri))

                } catch (e: Exception) {
                    cancelNotification(context)
                    _downloadState.emit(State.Failed)
                }
            }
        }
    }

    override fun openPackageInstaller(context: Context, uri: Uri) {
        Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.also {
            context.startActivity(it)
        }
    }

    override fun cancelDownload(context: Context) {
        cancelNotification(context)
        viewModelScope.launch {
            _downloadState.emit(State.Idle)
        }
    }
}

