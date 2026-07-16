package com.kieronquinn.app.darq.ui.screens.settings.advanced

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                getString(R.string.item_advanced_group_theme_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_advanced_always_use_force_dark,
                getString(R.string.item_always_use_force_dark_title),
                getString(R.string.item_always_use_force_dark_content),
                settings::alwaysForceDark
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_github,
                getString(R.string.item_check_for_updates_title),
                getString(R.string.item_check_for_updates_content),
                settings::checkForUpdates
            ),
            SettingsItem.Header(
                getString(R.string.item_advanced_group_background_title)
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_advanced_send_app_closes,
                getString(R.string.item_send_app_closes_title),
                getString(R.string.item_send_app_closes_content),
                settings::sendAppCloses
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_notification,
                getString(R.string.item_persistent_service_title),
                getString(R.string.item_persistent_service_content),
                settings::persistentService,
                tapAction = { isChecked ->
                    val intent = Intent(requireContext(), DarqPersistentService::class.java)
                    if (isChecked) {
                        requireContext().startForegroundService(intent)
                    } else {
                        requireContext().stopService(intent)
                    }
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_notification,
                getString(R.string.item_boot_wait_shizuku_title),
                getString(R.string.item_boot_wait_shizuku_content),
                settings::bootWaitShizuku
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

}