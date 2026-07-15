package com.kieronquinn.app.darq.components.github

import android.content.Context
import android.os.Parcelable
import com.kieronquinn.app.darq.BuildConfig
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.File

class UpdateChecker {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    companion object {
        private const val GITHUB_REPO = "Arora-Sir/DarQ"
        private const val BASE_URL = "https://api.github.com/repos/$GITHUB_REPO/"
        private const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    }

    private fun isNewerVersion(remoteTag: String, localTag: String): Boolean {
        val remote = remoteTag.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val local = localTag.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(remote.size, local.size)
        for (i in 0 until length) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun getLatestRelease() = callbackFlow {
        withContext(Dispatchers.IO){
            val release = getLatestReleaseResponse()
            release?.let { gitHubReleaseResponse ->
                val currentTag = gitHubReleaseResponse.tagName
                if (currentTag != null && isNewerVersion(currentTag, BuildConfig.TAG_NAME)) {
                    val asset =
                        gitHubReleaseResponse.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    if (asset == null) {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val releaseUrl =
                        asset.browserDownloadUrl?.replace("/download/", "/tag/")?.let {
                            it.substring(0, it.lastIndexOf("/"))
                        }
                    val name = gitHubReleaseResponse.name ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val body = gitHubReleaseResponse.body ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val publishedAt = gitHubReleaseResponse.publishedAt ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    // Construct a unique filename for the version (e.g. DarQ_2.2.9.apk)
                    val uniqueAssetName = "DarQ_${currentTag}.apk"
                    this@callbackFlow.trySend(
                        Update(
                            name,
                            body,
                            publishedAt,
                            asset.browserDownloadUrl ?: RELEASES_URL,
                            uniqueAssetName,
                            releaseUrl ?: RELEASES_URL
                        )
                    ).isSuccess
                } else {
                    this@callbackFlow.trySend(null).isSuccess
                }
            } ?: run {
                this@callbackFlow.trySend(null).isSuccess
            }
        }
        awaitClose {  }
    }

    fun deleteStaleCache(context: Context, currentAssetName: String) {
        val folder = File(context.cacheDir, "updates")
        folder.listFiles()?.forEach { file ->
            if (file.name != currentAssetName) {
                file.delete()
            }
        }
    }

    private fun getLatestReleaseResponse(): GitHubReleaseResponse? {
        val service: GitHubService = retrofit.create(GitHubService::class.java)
        runCatching {
            service.getLatestRelease().execute().body()
        }.onSuccess {
            return it
        }.onFailure {
            return null
        }
        return null
    }

    interface GitHubService {
        @GET("releases/latest")
        fun getLatestRelease(): Call<GitHubReleaseResponse>
    }

    @Parcelize
    data class Update(val name: String, val changelog: String, val timestamp: String, val assetUrl: String, val assetName: String, val releaseUrl: String): Parcelable

}