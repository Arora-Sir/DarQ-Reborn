package com.kieronquinn.app.darq.ui.screens.settings.advanced

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentSettingsAdvancedBinding
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.service.background.DarqPersistentService
import com.kieronquinn.app.darq.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.darq.ui.base.BackAvailable
import com.kieronquinn.app.darq.ui.screens.settings.BaseSettingsFragment
import com.kieronquinn.app.darq.ui.screens.settings.SettingsAdapter
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively

class SettingsAdvancedFragment : BaseSettingsFragment<FragmentSettingsAdvancedBinding>(
    FragmentSettingsAdvancedBinding::inflate), AutoExpandOnRotate, BackAvailable {

    override val settingsItems: MutableList<SettingsItem> by lazy {
        listOf<SettingsItem>(
            SettingsItem.Header(
                getString(R.string.item_advanced_group_service_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_boot_wait,
                getString(R.string.item_boot_wait_shizuku_title),
                getString(R.string.item_boot_wait_shizuku_content),
                settings::bootWaitShizuku
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_notification,
                getString(R.string.item_persistent_service_title),
                getString(R.string.item_persistent_service_content),
                settings::persistentService,
                tapAction = { isChecked ->
                    if (!isChecked && settings.autoDarkScheduleMode == 3) {
                        // Confirmation is asynchronous (an AlertDialog), but tapAction's return
                        // value gates persistence synchronously (see SettingsAdapter.
                        // setupSettingSwitch) - so return false now to revert the switch and skip
                        // persisting, then apply the disable manually if/when the user confirms.
                        showDisablePersistentServiceWarning {
                            setPersistentServiceRunning(false)
                            binding.recyclerView.post {
                                adapter.notifyDataSetChanged()
                            }
                        }
                        false
                    } else {
                        setPersistentServiceRunning(isChecked)
                        binding.recyclerView.post {
                            adapter.notifyDataSetChanged()
                        }
                        true
                    }
                }
            ),
            SettingsItem.Setting(
                R.drawable.ic_developer_options_service_info,
                getString(R.string.item_persistent_notification_settings_title),
                getString(R.string.item_persistent_notification_settings_content),
                visible = { settings.persistentService },
                tapAction = {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, DarqPersistentService.NOTIFICATION_CHANNEL_PERSISTENT)
                    }
                    requireContext().startActivity(intent)
                }
            ),
            SettingsItem.Header(
                getString(R.string.item_advanced_group_updates_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_github,
                getString(R.string.item_check_for_updates_title),
                getString(R.string.item_check_for_updates_content),
                settings::checkForUpdates,
                tapAction = { isChecked ->
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_release_branch,
                getString(R.string.item_check_for_prereleases_title),
                getString(R.string.item_check_for_prereleases_content),
                settings::checkForPrereleases,
                visible = { settings.checkForUpdates }
            ),
            SettingsItem.Header(
                getString(R.string.item_advanced_group_theme_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_advanced_always_use_force_dark,
                getString(R.string.item_always_use_force_dark_title),
                getString(R.string.item_always_use_force_dark_content),
                settings::alwaysForceDark
            ),
            SettingsItem.Header(
                getString(R.string.item_advanced_group_behaviour_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_advanced_send_app_closes,
                getString(R.string.item_send_app_closes_title),
                getString(R.string.item_send_app_closes_content),
                settings::sendAppCloses
            )
        ).toMutableList()
    }

    private val adapter: SettingsAdapter by lazy {
        SettingsAdapter(requireContext(), settingsItems)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view?.applyMonetRecursively()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(binding.recyclerView, adapter)
        setupSnackbarPadding(binding.recyclerView)
    }

    private fun setPersistentServiceRunning(running: Boolean) {
        settings.persistentService = running
        val intent = Intent(requireContext(), DarqPersistentService::class.java)
        try {
            if (running) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().stopService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsAdvanced", "Failed to start/stop persistent service", e)
        }
    }

    private fun showDisablePersistentServiceWarning(onConfirmed: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_disable_persistent_service_title)
            .setMessage(R.string.dialog_disable_persistent_service_message)
            .setPositiveButton(R.string.dialog_disable_persistent_service_confirm) { _, _ -> onConfirmed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

}