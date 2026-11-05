package com.kieronquinn.app.darq.ui.screens.settings.xposed

import android.os.Bundle
import com.kieronquinn.app.darq.utils.Toasts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.xposed.XposedBridge
import com.kieronquinn.app.darq.databinding.FragmentSettingsXposedBinding
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.darq.ui.base.BackAvailable
import com.kieronquinn.app.darq.ui.screens.settings.BaseSettingsFragment
import com.kieronquinn.app.darq.ui.screens.settings.SettingsAdapter

class XposedSettingsFragment: BaseSettingsFragment<FragmentSettingsXposedBinding>(
    FragmentSettingsXposedBinding::inflate), AutoExpandOnRotate, BackAvailable {

    override val settingsItems by lazy {
        listOf(
            SettingsItem.SwitchSetting(
                R.drawable.ic_advanced_always_use_force_dark,
                getString(R.string.item_xposed_aggressive_dark_title),
                getString(R.string.item_xposed_aggressive_dark_content),
                settings::xposedAggressiveDark
            ),
            SettingsItem.SwitchSetting(
                R.drawable.ic_xposed_status_bar_invert,
                getString(R.string.item_xposed_invert_status_bar_fix_title),
                getString(R.string.item_xposed_invert_status_bar_fix_content),
                settings::xposedInvertStatus
            ),
            SettingsItem.Setting(
                R.drawable.ic_app_whitelist,
                getString(R.string.item_xposed_request_scope_title),
                getString(R.string.item_xposed_request_scope_content),
                visible = { XposedBridge.isActiveNow },
                tapAction = ::requestMissingScope
            ),
            SettingsItem.Setting(
                R.drawable.ic_about_small,
                getString(R.string.item_xposed_info_title),
                getText(R.string.item_xposed_info_content),
                centerIconVertically = false
            )
        ).toMutableList()
    }

    private fun requestMissingScope(){
        XposedBridge.requestScope(settings.enabledApps.toList()) { result ->
            val context = context ?: return@requestScope
            val message = when(result){
                is XposedBridge.ScopeResult.Approved -> getString(R.string.xposed_scope_approved_count, result.packages.size)
                is XposedBridge.ScopeResult.Failed -> getString(R.string.xposed_scope_failed, result.message)
                XposedBridge.ScopeResult.AlreadyInScope -> getString(R.string.xposed_scope_already)
                XposedBridge.ScopeResult.Unavailable -> getString(R.string.xposed_scope_unavailable)
            }
            Toasts.show(context, message, long = true)
        }
    }

    private val adapter by lazy {
        SettingsAdapter(requireContext(), settingsItems)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(binding.recyclerView, adapter)
        setupSnackbarPadding(binding.recyclerView)
    }

}