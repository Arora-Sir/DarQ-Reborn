package com.kieronquinn.app.darq.components.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import com.kieronquinn.app.darq.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

// NOTE: Keeps the installed app list for the app picker, so the picker shows it instantly instead of rescanning every time it opens.
// NOTE: The list lives in memory for the process and in files/app_list_cache.json across restarts.
// NOTE: Each refresh asks PackageManager.getChangedPackages for what changed since the stored sequence number and updates only those apps.
// NOTE: A reboot (sequence numbers restart), a language change (labels change) or a missing cache falls back to one full scan in the background.
class InstalledAppsRepository(private val context: Context) {

    data class App(val packageName: String, val label: String, val launchable: Boolean)

    companion object {
        private const val TAG = "DarqAppList"
        private const val CACHE_FILE = "app_list_cache.json"
        private const val CACHE_VERSION = 1
        private const val LABEL_BATCH = 25
    }

    private val packageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val cacheFile = AtomicFile(File(context.filesDir, CACHE_FILE))

    private val _apps = MutableStateFlow<List<App>?>(null)

    /**
     *  The cached list sorted by label, or null before the first load finishes.
     */
    val apps: StateFlow<List<App>?> = _apps.asStateFlow()

    private val _scanProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /**
     *  Progress of a full scan while nothing is cached yet. Incremental refreshes report no progress.
     */
    val scanProgress: StateFlow<Pair<Int, Int>?> = _scanProgress.asStateFlow()

    private var sequenceNumber = 0
    private var bootCount = -1
    private var locale = ""

    /**
     *  Shows the cached list right away (memory, then disk) and refreshes it in the background.
     */
    fun refresh() {
        scope.launch {
            refreshLock.withLock {
                if (_apps.value == null) readCache()?.let { _apps.value = sortApps(it) }
                try {
                    update()
                } catch (e: Exception) {
                    Log.e(TAG, "App list refresh failed", e)
                }
            }
        }
    }

    private suspend fun update() {
        val cached = _apps.value
        val currentBoot = currentBootCount()
        val currentLocale = Locale.getDefault().toLanguageTag()
        val changes = if (cached != null && currentBoot == bootCount && currentLocale == locale) {
            packageManager.getChangedPackages(sequenceNumber)
        } else null
        val updated = when {
            cached == null || (changes == null && (currentBoot != bootCount || currentLocale != locale)) -> fullScan(showProgress = cached == null)
            changes == null -> return
            else -> applyChanges(cached, changes.packageNames)
        }
        sequenceNumber = changes?.sequenceNumber ?: currentSequence()
        bootCount = currentBoot
        locale = currentLocale
        _apps.value = sortApps(updated)
        writeCache(updated)
    }

    private suspend fun fullScan(showProgress: Boolean): List<App> = kotlinx.coroutines.coroutineScope {
        val launchable = launchablePackages()
        val installed = packageManager.getInstalledApplications(0).filter { it.packageName != BuildConfig.APPLICATION_ID }
        val result = ArrayList<App>(installed.size)
        if (showProgress) _scanProgress.value = Pair(0, installed.size)
        installed.chunked(LABEL_BATCH).forEach { batch ->
            result += batch.map { info -> async { toApp(info, launchable) } }.awaitAll()
            if (showProgress) _scanProgress.value = Pair(result.size, installed.size)
        }
        _scanProgress.value = null
        result
    }

    private fun applyChanges(cached: List<App>, changed: List<String>): List<App> {
        if (changed.isEmpty()) return cached
        val byPackage = cached.associateBy { it.packageName }.toMutableMap()
        val launchable = launchablePackages()
        changed.filter { it != BuildConfig.APPLICATION_ID }.forEach { pkg ->
            val info = try {
                packageManager.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (info == null) byPackage.remove(pkg) else byPackage[pkg] = toApp(info, launchable)
        }
        return byPackage.values.toList()
    }

    private fun toApp(info: ApplicationInfo, launchable: Set<String>) =
        App(info.packageName, info.loadLabel(packageManager).toString(), info.packageName in launchable)

    private fun launchablePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun currentSequence(): Int = packageManager.getChangedPackages(0)?.sequenceNumber ?: 0

    private fun currentBootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun sortApps(apps: List<App>) = apps.sortedBy { it.label.lowercase(Locale.getDefault()) }

    private fun readCache(): List<App>? = try {
        if (!cacheFile.baseFile.exists()) null else {
            val json = JSONObject(String(cacheFile.readFully(), Charsets.UTF_8))
            if (json.optInt("version") != CACHE_VERSION) null else {
                sequenceNumber = json.optInt("sequence")
                bootCount = json.optInt("boot", -1)
                locale = json.optString("locale")
                val array = json.getJSONArray("apps")
                (0 until array.length()).map {
                    val app = array.getJSONObject(it)
                    App(app.getString("p"), app.getString("l"), app.optBoolean("x"))
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Ignoring unreadable app list cache", e)
        null
    }

    private fun writeCache(apps: List<App>) {
        val array = JSONArray()
        apps.forEach { array.put(JSONObject().put("p", it.packageName).put("l", it.label).put("x", it.launchable)) }
        val json = JSONObject().put("version", CACHE_VERSION).put("sequence", sequenceNumber).put("boot", bootCount)
            .put("locale", locale).put("apps", array)
        val stream = cacheFile.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            cacheFile.finishWrite(stream)
        } catch (e: Exception) {
            cacheFile.failWrite(stream)
        }
    }

}
