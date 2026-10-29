package com.kieronquinn.app.darq.ui.screens.settings.apppicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.components.apps.InstalledAppsRepository
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.model.settings.AppPickerItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

abstract class SettingsAppPickerViewModel : ViewModel() {

    abstract val loadState: StateFlow<LoadState>
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
    private val repository: InstalledAppsRepository,
    private val settings: DarqSharedPreferences
) : SettingsAppPickerViewModel() {

    //Progress only shows on the very first scan, later opens use the cached list
    override val loadingProgress = repository.scanProgress

    private val selectedApps = MutableStateFlow(settings.enabledApps.toMutableList())

    private val _loadState = MutableStateFlow<LoadState>(
        repository.apps.value?.let { cached ->
            LoadState.Loaded(buildItems(cached, selectedApps.value, "", false))
        } ?: LoadState.Loading
    )
    override val loadState = _loadState.asStateFlow()

    private val searchTerm = MutableStateFlow("")

    override val showSearchClearButton: Flow<Boolean> = searchTerm.map { it.isNotEmpty() }

    private val showAllApps = MutableStateFlow(false)

    init {
        repository.refresh()
        viewModelScope.launch {
            //Reloads when the filter changes and when a background refresh brings a newer list
            combine(showAllApps, repository.apps.filterNotNull()) { _, _ -> }.collect {
                loadApps()
            }
        }
    }

    private suspend fun loadApps() {
        if (repository.apps.value == null) _loadState.emit(LoadState.Loading)
        val selected = selectedApps.value
        val cached = repository.apps.value ?: repository.apps.filterNotNull().first()
        val search = searchTerm.value
        val showAll = showAllApps.value
        val items = withContext(Dispatchers.Default) {
            buildItems(cached, selected, search, showAll)
        }
        _loadState.emit(LoadState.Loaded(items))
    }

    private fun buildItems(
        cached: List<InstalledAppsRepository.App>,
        selected: List<String>,
        search: String,
        showAll: Boolean
    ): List<AppPickerItem> {
        val query = search.lowercase(Locale.getDefault())
        val apps = cached.asSequence()
            .filter { showAll || it.launchable }
            .filter { query.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(query) }
            .map { AppPickerItem.App(it.packageName, it.label, selected.contains(it.packageName)) }
            .toList()

        val activeApps = apps.filter { it.enabled }
        val inactiveApps = apps.filter { !it.enabled }

        val listWithHeaders = mutableListOf<AppPickerItem>()
        if (activeApps.isNotEmpty()) {
            listWithHeaders.add(AppPickerItem.Header(com.kieronquinn.app.darq.R.string.app_picker_header_active))
            listWithHeaders.addAll(activeApps)
        }
        if (inactiveApps.isNotEmpty()) {
            listWithHeaders.add(AppPickerItem.Header(com.kieronquinn.app.darq.R.string.app_picker_header_all))
            if (query.isEmpty()) listWithHeaders.addAll(withLetterHeaders(inactiveApps)) else listWithHeaders.addAll(inactiveApps)
        }
        return listWithHeaders
    }

    private fun withLetterHeaders(apps: List<AppPickerItem.App>): List<AppPickerItem> {
        val result = ArrayList<AppPickerItem>(apps.size + 27)
        var last: String? = null
        //Names that start with a digit or a symbol are grouped under one "#" header, which sorts first
        val sorted = apps.sortedWith(compareBy<AppPickerItem.App> { letterOf(it.label) != "#" }.thenBy { it.label.toString().lowercase(Locale.getDefault()) })
        for (app in sorted) {
            val letter = letterOf(app.label)
            if (letter != last) {
                result.add(AppPickerItem.Letter(letter))
                last = letter
            }
            result.add(app)
        }
        return result
    }

    override fun onPackageEnabledChanged(app: AppPickerItem.App) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSelectedApps = selectedApps.value
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

    companion object {
        fun letterOf(label: CharSequence): String {
            val first = label.firstOrNull()?.uppercaseChar() ?: return "#"
            return if (first.isLetter()) first.toString() else "#"
        }
    }

}
