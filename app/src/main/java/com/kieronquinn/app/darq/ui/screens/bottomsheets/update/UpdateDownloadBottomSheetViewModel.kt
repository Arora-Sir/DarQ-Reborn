package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.BuildConfig
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

    private var _downloadState = MutableStateFlow<State>(State.Idle)
    override val downloadState = _downloadState.asStateFlow()

    private val okHttpClient by lazy { OkHttpClient() }

    override fun startDownload(context: Context, update: UpdateChecker.Update) {
        if (_downloadState.value is State.Idle) {
            val downloadFolder = File(context.cacheDir, "updates")
            val existingFile = File(downloadFolder, update.assetName)
            if (existingFile.exists() && existingFile.length() > 0) {
                // File already downloaded — skip straight to install
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
            withContext(Dispatchers.IO) {
                try {
                    val downloadFolder = File(context.cacheDir, "updates").also { it.mkdirs() }
                    val outputFile = File(downloadFolder, fileName)

                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        _downloadState.emit(State.Failed)
                        return@withContext
                    }

                    val body = response.body() ?: run {
                        _downloadState.emit(State.Failed)
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

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
                                }
                            }
                        }
                    }

                    val outputUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        outputFile
                    )
                    _downloadState.emit(State.Done(outputUri))

                } catch (e: Exception) {
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
        // OkHttp: simply reset state — the coroutine will finish silently
        viewModelScope.launch {
            _downloadState.emit(State.Idle)
        }
    }
}
