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
    private val repository: InstalledAppsRepository,
    private val settings: DarqSharedPreferences
) : SettingsAppPickerViewModel() {

    //Progress only shows on the very first scan, later opens use the cached list
    override val loadingProgress = repository.scanProgress

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
        repository.refresh()
        viewModelScope.launch {
            //Reloads when the filter changes and when a background refresh brings a newer list
            combine(showAllApps, repository.apps.filterNotNull()) { _, _ -> }.collect {
                loadApps()
            }
        }
    }

    private suspend fun loadApps() {
        //Only the first scan without a cache shows the loading state, searches and toggles update the list in place
        if (repository.apps.value == null) _loadState.emit(LoadState.Loading)
        val selected = selectedApps.filterNotNull().first()
        val search = searchTerm.value
        val cached = repository.apps.filterNotNull().first()
        val showAll = showAllApps.value
        val query = search.lowercase(Locale.getDefault())
        //The repository keeps the list sorted, so a search only filters
        val apps = withContext(Dispatchers.Default) {
            cached.asSequence()
                .filter { showAll || it.launchable }
                .filter { query.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(query) }
                .map { AppPickerItem.App(it.packageName, it.label, selected.contains(it.packageName)) }
                .toList()
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
            //Letter headers help scanning a long list, and are left out while searching since the results are short
            if (query.isEmpty()) listWithHeaders.addAll(withLetterHeaders(inactiveApps)) else listWithHeaders.addAll(inactiveApps)
        }

        _loadState.emit(LoadState.Loaded(listWithHeaders))
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

    companion object {
        fun letterOf(label: CharSequence): String {
            val first = label.firstOrNull()?.uppercaseChar() ?: return "#"
            return if (first.isLetter()) first.toString() else "#"
        }
    }

}
