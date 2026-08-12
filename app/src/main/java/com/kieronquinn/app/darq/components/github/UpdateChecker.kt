package com.kieronquinn.app.darq.components.github

import android.content.Context
import android.os.Parcelable
import android.util.Log
import com.kieronquinn.app.darq.BuildConfig
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import retrofit2.http.GET
import java.io.File

class UpdateChecker(private val settings: DarqSharedPreferences) {

    // GitHub API requires a User-Agent header: requests without one are rejected with 403.
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "DarQ-Reborn/Android")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    companion object {
        private const val GITHUB_REPO = "Arora-Sir/DarQ-Reborn"
        private const val BASE_URL = "https://api.github.com/repos/$GITHUB_REPO/"
        private const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    }

    private fun isNewerVersion(remoteTag: String, localTag: String): Boolean {
        fun parseVersion(tag: String): Pair<List<Int>, String?> {
            val clean = tag.replace("v", "").trim()
            val parts = clean.split("-", limit = 2)
            val mainStr = parts[0]
            val preRelease = if (parts.size > 1) parts[1] else null

            val mainNumbers = mainStr.split(".").mapNotNull { it.toIntOrNull() }
            return Pair(mainNumbers, preRelease)
        }

        val (remoteNums, remotePre) = parseVersion(remoteTag)
        val (localNums, localPre) = parseVersion(localTag)

        val maxLength = maxOf(remoteNums.size, localNums.size)
        for (i in 0 until maxLength) {
            val r = remoteNums.getOrElse(i) { 0 }
            val l = localNums.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        // If main versions are equal, a pre-release version is older than a stable version.
        if (remotePre != null && localPre == null) {
            return false
        }
        if (remotePre == null && localPre != null) {
            return true
        }
        if (remotePre != null && localPre != null) {
            return remotePre > localPre
        }

        return false
    }

    fun getLatestRelease() = callbackFlow {
        Log.d("DarQUpdate", "getLatestRelease: Checking for update started")
        withContext(Dispatchers.IO){
            val release = getLatestReleaseResponse(settings.checkForPrereleases)
            Log.d("DarQUpdate", "getLatestRelease: Fetched release response: $release")
            release?.let { gitHubReleaseResponse ->
                val currentTag = gitHubReleaseResponse.tagName
                Log.d("DarQUpdate", "getLatestRelease: Current remote tag = $currentTag, Local tag = ${BuildConfig.TAG_NAME}")
                if (currentTag != null) {
                    if (isNewerVersion(currentTag, BuildConfig.TAG_NAME)) {
                        Log.d("DarQUpdate", "getLatestRelease: Remote tag is newer, looking for apk asset")
                        val assets = gitHubReleaseResponse.assets
                        Log.d("DarQUpdate", "getLatestRelease: Total assets found: ${assets?.size ?: 0}")
                        assets?.forEach {
                            Log.d("DarQUpdate", "getLatestRelease: Found asset option: name=${it.name}, url=${it.browserDownloadUrl}")
                        }
                        val asset =
                            gitHubReleaseResponse.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                        if (asset == null) {
                            Log.e("DarQUpdate", "getLatestRelease: No asset ending in .apk found")
                            this@callbackFlow.trySend(null).isSuccess
                            return@let
                        }
                        val releaseUrl =
                            asset.browserDownloadUrl?.replace("/download/", "/tag/")?.let {
                                it.substring(0, it.lastIndexOf("/"))
                            }
                        val name = gitHubReleaseResponse.name ?: run {
                            Log.e("DarQUpdate", "getLatestRelease: Release name is null")
                            this@callbackFlow.trySend(null).isSuccess
                            return@let
                        }
                        val body = gitHubReleaseResponse.body ?: run {
                            Log.e("DarQUpdate", "getLatestRelease: Release body is null")
                            this@callbackFlow.trySend(null).isSuccess
                            return@let
                        }
                        val publishedAt = gitHubReleaseResponse.publishedAt ?: run {
                            Log.e("DarQUpdate", "getLatestRelease: Release publishedAt is null")
                            this@callbackFlow.trySend(null).isSuccess
                            return@let
                        }
                        // Construct a unique filename for the version (e.g. DarQ_2.2.9.apk)
                        val uniqueAssetName = "DarQ_${currentTag}.apk"
                        val updateObj = Update(
                            name,
                            body,
                            publishedAt,
                            asset.browserDownloadUrl ?: RELEASES_URL,
                            uniqueAssetName,
                            releaseUrl ?: RELEASES_URL,
                            currentTag
                        )
                        Log.d("DarQUpdate", "getLatestRelease: Emitting update: $updateObj")
                        this@callbackFlow.trySend(updateObj).isSuccess
                    } else {
                        Log.d("DarQUpdate", "getLatestRelease: Remote tag is not newer than local tag. No update needed.")
                        this@callbackFlow.trySend(null).isSuccess
                    }
                } else {
                    Log.e("DarQUpdate", "getLatestRelease: Remote tag name is null")
                    this@callbackFlow.trySend(null).isSuccess
                }
            } ?: run {
                Log.e("DarQUpdate", "getLatestRelease: Release response is null")
                this@callbackFlow.trySend(null).isSuccess
            }
        }
        awaitClose { Log.d("DarQUpdate", "getLatestRelease: callbackFlow closed") }
    }

    fun deleteStaleCache(context: Context, currentAssetName: String) {
        val folder = File(context.cacheDir, "updates")
        Log.d("DarQUpdate", "deleteStaleCache: cache folder path = ${folder.absolutePath}, keeping currentAssetName = $currentAssetName")
        folder.listFiles()?.forEach { file ->
            if (file.name != currentAssetName) {
                val deleted = file.delete()
                Log.d("DarQUpdate", "deleteStaleCache: Deleted stale file ${file.name}: $deleted")
            } else {
                Log.d("DarQUpdate", "deleteStaleCache: Keeping active file ${file.name}")
            }
        }
    }

    private fun getLatestReleaseResponse(checkPrereleases: Boolean): GitHubReleaseResponse? {
        val service: GitHubService = retrofit.create(GitHubService::class.java)
        Log.d("DarQUpdate", "getLatestReleaseResponse: Making GitHub API call to $BASE_URL with checkPrereleases=$checkPrereleases")
        runCatching {
            if (checkPrereleases) {
                service.getReleases().execute().body()?.firstOrNull { it.draft != true }
            } else {
                service.getLatestRelease().execute().body()
            }
        }.onSuccess {
            Log.d("DarQUpdate", "getLatestReleaseResponse: API call success, body: $it")
            return it
        }.onFailure {
            Log.e("DarQUpdate", "getLatestReleaseResponse: API call failed", it)
            return null
        }
        return null
    }

    interface GitHubService {
        @GET("releases/latest")
        fun getLatestRelease(): Call<GitHubReleaseResponse>

        @GET("releases")
        fun getReleases(): Call<List<GitHubReleaseResponse>>
    }

    @Parcelize
    data class Update(val name: String, val changelog: String, val timestamp: String, val assetUrl: String, val assetName: String, val releaseUrl: String, val version: String): Parcelable

}