package com.kieronquinn.app.darq.ui.screens.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.github.UpdateChecker
import com.kieronquinn.app.darq.components.navigation.Navigation
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.ui.screens.container.ContainerSharedViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class SettingsViewModel: ViewModel() {

    abstract val developerOptionsVisible: Flow<Boolean>

    abstract fun onAdvancedOptionsClicked()
    abstract fun onAppearanceClicked()
    abstract fun onXposedClicked()
    abstract fun onBackupRestoreClicked()
    abstract fun onAppWhitelistClicked()
    abstract fun onDeveloperOptionsClicked()
    abstract fun onAutoDarkThemeCheckedChange(checked: Boolean, sharedViewModel: ContainerSharedViewModel): Boolean
    abstract fun onAutoDarkOptionsClicked()
    abstract fun onFaqClicked()
    abstract fun onAboutTripleTapped()
    abstract fun onOssLicencesClicked()
    abstract fun isOxygenForceDarkSupported(): Boolean
    abstract fun onCheckForUpdatesClicked(context: Context, sharedViewModel: ContainerSharedViewModel)

}

class SettingsViewModelImpl(
    private val navigation: Navigation,
    private val settings: DarqSharedPreferences,
    private val updateChecker: UpdateChecker
): SettingsViewModel() {

    private val _developerOptionsVisible = MutableStateFlow(settings.developerOptions)
    override val developerOptionsVisible = _developerOptionsVisible.asStateFlow()

    override fun onAdvancedOptionsClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsAdvancedFragment())
        }
    }

    override fun onAutoDarkOptionsClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsAutoDarkFragment())
        }
    }

    override fun onBackupRestoreClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToBackupRestoreBottomSheetFragment())
        }
    }

    override fun onXposedClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToXposedSettingsFragment())
        }
    }

    override fun onAppWhitelistClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsAppPickerFragment())
        }
    }

    override fun onAppearanceClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsAppearanceFragment())
        }
    }

    override fun onDeveloperOptionsClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsDeveloperOptionsFragment())
        }
    }

    override fun onAutoDarkThemeCheckedChange(checked: Boolean, sharedViewModel: ContainerSharedViewModel): Boolean {
        if(!checked){
            sharedViewModel.setAutoDarkThemeEnabled(false)
            return true
        }
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToLocationPermissionDialogFragment())
        }
        return false
    }

    override fun onFaqClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsFaqFragment())
        }
    }

    override fun onAboutTripleTapped() {
        viewModelScope.launch {
            val newValue = !settings.developerOptions
            settings.developerOptions = newValue
            _developerOptionsVisible.emit(newValue)
        }
    }

    override fun onOssLicencesClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToOssLicencesActivity())
        }
    }

    override fun isOxygenForceDarkSupported(): Boolean {
        return Build.MANUFACTURER == "OnePlus" && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    }

    override fun onCheckForUpdatesClicked(context: Context, sharedViewModel: ContainerSharedViewModel) {
        Log.d("DarQUpdate", "onCheckForUpdatesClicked: User clicked check for updates")
        viewModelScope.launch {
            Toast.makeText(context, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()
            try {
                Log.d("DarQUpdate", "onCheckForUpdatesClicked: Calling updateChecker.getLatestRelease().first()")
                val update = updateChecker.getLatestRelease().first()
                Log.d("DarQUpdate", "onCheckForUpdatesClicked: Result update = $update")
                if (update != null) {
                    Log.d("DarQUpdate", "onCheckForUpdatesClicked: Setting available update in sharedViewModel: $update")
                    sharedViewModel.setAvailableUpdate(update)
                } else {
                    Log.d("DarQUpdate", "onCheckForUpdatesClicked: No update found. App is up to date.")
                    Toast.makeText(context, R.string.up_to_date, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DarQUpdate", "onCheckForUpdatesClicked: Exception occurred while checking for updates", e)
                Toast.makeText(context, R.string.check_updates_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

}