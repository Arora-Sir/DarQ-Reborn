package com.kieronquinn.app.darq.ui.screens.settings.apppicker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.model.settings.AppPickerItem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.Locale

abstract class SettingsAppPickerViewModel : ViewModel() {

    abstract val loadState: Flow<LoadState>
    abstract val showSearchClearButton: Flow<Boolean>
    abstract val loadingProgress: Flow<Pair<Int, Int>?>

    abstract fun onPackageEnabledChanged(app: AppPickerItem.App)
    abstract fun setSearchTerm(searchTerm: String)
    abstract fun getSearchTerm(): String
    abstract fun setShowAllApps(showAllApps: Boolean)
    abstract fun getShowAllApps(): Boolean

    sealed class LoadState {
        object Loading : LoadState()
        data class Loaded(val apps: List<AppPickerItem>) : LoadState()
    }

}

class SettingsAppPickerViewModelImpl(
    context: Context,
    private val settings: DarqSharedPreferences
) : SettingsAppPickerViewModel() {

    private val packageManager by lazy {
        context.packageManager
    }

    private val _loadingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    override val loadingProgress = _loadingProgress.asStateFlow()

    private val allApps = MutableSharedFlow<List<InstalledApp>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val launchableApps = MutableSharedFlow<List<InstalledApp>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val allAppsLoading = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun loadAllAppsAsync() {
        if (allAppsLoading.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.IO) {
                // Wait for fragment transition to complete (300ms) before making intensive Binder calls
                delay(300)
                val rawApps = packageManager.getInstalledApplications(0).filter {
                    it.packageName != BuildConfig.APPLICATION_ID
                }
                val total = rawApps.size
                val loadedApps = mutableListOf<InstalledApp>()
                var count = 0
                _loadingProgress.emit(Pair(0, total))
                
                rawApps.chunked(25).forEach { batch ->
                    val deferred = batch.map { app ->
                        async {
                            InstalledApp(app.packageName, app.loadLabel(packageManager))
                        }
                    }
                    loadedApps.addAll(deferred.awaitAll())
                    count += batch.size
                    _loadingProgress.emit(Pair(count, total))
                }
                allApps.emit(loadedApps)
                _loadingProgress.emit(null)
            }
        }
    }

    private val launchableAppsLoading = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun loadLaunchableAppsAsync() {
        if (launchableAppsLoading.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.IO) {
                // Wait for fragment transition to complete (300ms) before making intensive Binder calls
                delay(300)
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                val rawInfos = resolveInfos.filter {
                    it.activityInfo.packageName != BuildConfig.APPLICATION_ID
                }
                val total = rawInfos.size
                val loadedApps = mutableListOf<InstalledApp>()
                var count = 0
                _loadingProgress.emit(Pair(0, total))
                
                rawInfos.chunked(25).forEach { batch ->
                    val deferred = batch.map { resolveInfo ->
                        async {
                            val packageName = resolveInfo.activityInfo.packageName
                            InstalledApp(packageName, resolveInfo.loadLabel(packageManager))
                        }
                    }
                    loadedApps.addAll(deferred.awaitAll())
                    count += batch.size
                    _loadingProgress.emit(Pair(count, total))
                }
                launchableApps.emit(loadedApps.distinctBy { it.packageName })
                _loadingProgress.emit(null)
            }
        }
    }

    private val selectedApps = MutableStateFlow<MutableList<String>?>(null).apply {
        viewModelScope.launch(Dispatchers.IO) {
            emit(settings.enabledApps.toMutableList())
        }
    }

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    override val loadState = _loadState.asStateFlow()

    private val searchTerm = MutableStateFlow("")

    override val showSearchClearButton: Flow<Boolean> = searchTerm.map { it.isNotEmpty() }

    private val showAllApps = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            showAllApps.collect {
                loadApps()
            }
        }
    }

    private suspend fun loadApps() {
        _loadState.emit(LoadState.Loading)
        val selected = selectedApps.filterNotNull().first()
        val search = searchTerm.value
        val rawList = if (showAllApps.value) {
            loadAllAppsAsync()
            allApps.first()
        } else {
            loadLaunchableAppsAsync()
            launchableApps.first()
        }

        val apps = withContext(Dispatchers.IO) {
            rawList.map {
                AppPickerItem.App(
                    it.packageName,
                    it.label,
                    selected.contains(it.packageName)
                )
            }
        }.sortedBy { it.label.toString().toLowerCase(Locale.getDefault()) }.run {
            if (search.isNotEmpty()) filter {
                it.label.toString().toLowerCase(Locale.getDefault()).contains(
                    search.toLowerCase(
                        Locale.getDefault()
                    )
                )
            }else this
        }

        val activeApps = apps.filter { it.enabled }
        val inactiveApps = apps.filter { !it.enabled }

        val listWithHeaders = mutableListOf<AppPickerItem>()
        if (activeApps.isNotEmpty()) {
            listWithHeaders.add(AppPickerItem.Header(com.kieronquinn.app.darq.R.string.app_picker_header_active))
            listWithHeaders.addAll(activeApps)
        }
        if (inactiveApps.isNotEmpty()) {
            listWithHeaders.add(AppPickerItem.Header(com.kieronquinn.app.darq.R.string.app_picker_header_all))
            listWithHeaders.addAll(inactiveApps)
        }

        _loadState.emit(LoadState.Loaded(listWithHeaders))
    }

    override fun onPackageEnabledChanged(app: AppPickerItem.App) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSelectedApps = selectedApps.value ?: return@launch
            if (app.enabled) {
                if (!currentSelectedApps.contains(app.packageName)) {
                    currentSelectedApps.add(app.packageName)
                }
            } else {
                if (currentSelectedApps.contains(app.packageName)) {
                    currentSelectedApps.remove(app.packageName)
                }
            }
            settings.enabledApps = currentSelectedApps.toTypedArray()
            loadApps()
        }
    }

    override fun setSearchTerm(searchTerm: String) {
        viewModelScope.launch {
            this@SettingsAppPickerViewModelImpl.searchTerm.emit(searchTerm)
            loadApps()
        }
    }

    override fun getSearchTerm(): String {
        return searchTerm.value
    }

    override fun setShowAllApps(showAllApps: Boolean) {
        viewModelScope.launch {
            this@SettingsAppPickerViewModelImpl.showAllApps.emit(showAllApps)
        }
    }

    override fun getShowAllApps(): Boolean {
        return showAllApps.value
    }

    private data class InstalledApp(val packageName: String, val label: CharSequence)

}