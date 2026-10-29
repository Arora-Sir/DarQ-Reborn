package com.kieronquinn.app.darq.ui.screens.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.*
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.R
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsDialogs
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider
import org.koin.android.ext.android.inject
import com.kieronquinn.app.darq.databinding.FragmentSettingsBinding
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.components.xposed.XposedBridge
import com.kieronquinn.app.darq.components.xposed.XposedState
import com.kieronquinn.app.darq.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.darq.ui.base.ProvidesOverflow
import com.kieronquinn.app.darq.ui.screens.container.ContainerFragment
import com.kieronquinn.app.darq.utils.ObtainiumLink
import com.kieronquinn.app.darq.utils.openLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val STATUS_POLL_MS = 600L
private const val STATUS_CONNECT_WAIT_MS = 9_000L

class SettingsFragment : BaseSettingsFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate), AutoExpandOnRotate, ProvidesOverflow {

    private val viewModel by viewModel<SettingsViewModel>()
    private val connection by inject<DarqServiceConnectionProvider>()
    override val settingsItems by lazy {
        listOf(
            SettingsItem.Setting(
                R.drawable.ic_app_whitelist_round,
                getString(R.string.item_whitelist_title),
                getString(R.string.item_whitelist_content),
                tapAction = viewModel::onAppWhitelistClicked,
                contentProvider = ::whitelistContent
            ),
            SettingsItem.Header(getString(R.string.item_header_options)),
            SettingsItem.SwitchSetting(
                R.drawable.ic_oxygen_round,
                getString(R.string.item_oxygen_force_title),
                getString(R.string.item_oxygen_force_content),
                settings::oxygenForceDark,
                visible = { viewModel.isOxygenForceDarkSupported() }
            ),
            SettingsItem.Setting(
                R.drawable.ic_day_night_auto_round,
                getString(R.string.item_day_night_auto_title),
                getString(R.string.item_day_night_auto_content),
                tapAction = viewModel::onAutoDarkOptionsClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_advanced_round,
                getString(R.string.item_advanced_options_title),
                getString(R.string.item_advanced_options_content),
                tapAction = viewModel::onAdvancedOptionsClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_appearance_round,
                getString(R.string.item_appearance_title),
                getString(R.string.item_appearance_content),
                tapAction = viewModel::onAppearanceClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_xposed_round,
                getString(R.string.item_xposed_title),
                getString(R.string.item_xposed_content),
                visible = { XposedState.isActive },
                tapAction = viewModel::onXposedClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_restore_round,
                getString(R.string.item_backup_restore_title),
                getString(R.string.item_backup_restore_content),
                tapAction = viewModel::onBackupRestoreClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_developer_options_round,
                getString(R.string.item_developer_options_title),
                visible = { settings.developerOptions },
                tapAction = viewModel::onDeveloperOptionsClicked
            ),
            SettingsItem.Header(getString(R.string.item_header_about)),
            SettingsItem.Setting(
                R.drawable.ic_faq_round,
                getString(R.string.item_about_faq_title),
                tapAction = viewModel::onFaqClicked
            ),
            SettingsItem.Setting(
                R.drawable.ic_update,
                getString(R.string.item_check_updates_manual_title),
                getString(R.string.item_check_updates_manual_content),
                tapAction = {
                    viewModel.onCheckForUpdatesClicked(requireContext(), sharedViewModel)
                }
            ),
            SettingsItem.Setting(
                R.drawable.ic_obtainium,
                getString(R.string.item_obtainium_title),
                getString(R.string.item_obtainium_content),
                tapAction = { requireContext().openLink(ObtainiumLink.url()) }
            ),
            SettingsItem.Setting(
                R.drawable.ic_report_issue_round,
                getString(R.string.item_report_issue_title),
                getString(R.string.item_report_issue_content),
                tapAction = { (activity as? AppCompatActivity)?.let { DiagnosticsDialogs.showReportIssue(it) } }
            ),
            SettingsItem.Setting(
                R.drawable.ic_support_heart,
                getString(R.string.chip_donate),
                getString(R.string.item_support_content),
                tapAction = { findNavController().navigate(R.id.action_global_donationBottomSheetFragment) }
            ),
            SettingsItem.AboutSetting(
                R.drawable.ic_about_round,
                getString(R.string.item_about_title, BuildConfig.VERSION_NAME),
                getString(R.string.item_about_content),
                tripleTapAction = viewModel::onAboutTripleTapped
            )
        ).toMutableList()
    }

    private val adapter by lazy {
        SettingsAdapter(requireContext(), settingsItems)
    }

    private val googleSans by lazy {
        ResourcesCompat.getFont(requireContext(), R.font.google_sans_text)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMainSwitch()
        setupRecyclerView(binding.recyclerView, adapter)
        setupSnackbarPadding(binding.recyclerView)
        setupAutoDarkTheme()
        setupDeveloperOptions()
        setupXposedState()
        setupRestoreListener()
    }

    private var statusJob: Job? = null

    // NOTE: The service binds a moment after the list appears, so the status polls before it reports a problem.
    // NOTE: Red appears only after the wait, a slow bind shows the last known mode on a grey dot instead.
    override fun onResume() {
        super.onResume()
        statusJob?.cancel()
        statusJob = viewLifecycleOwner.lifecycleScope.launch {
            var waited = 0L
            while (isActive) {
                val connected = connection.connectedServiceType != DarqServiceConnectionProvider.ServiceType.UNKNOWN
                publishStatus(connecting = !connected && waited < STATUS_CONNECT_WAIT_MS)
                if (connected) break
                delay(STATUS_POLL_MS)
                waited += STATUS_POLL_MS
            }
        }
        refreshWhitelistRow()
    }

    override fun onPause() {
        statusJob?.cancel()
        super.onPause()
    }

    private var switchWarning: Int? = null
    private var lastAppCount = -1

    // NOTE: The status goes to the title bar of the container as a dot and one word. The count of darkened apps lives in the first row.
    private fun publishStatus(connecting: Boolean) {
        val container = parentFragment?.parentFragment as? ContainerFragment ?: return
        val type = connection.connectedServiceType
        val connected = type != DarqServiceConnectionProvider.ServiceType.UNKNOWN
        if (connected) {
            val mode = if (type == DarqServiceConnectionProvider.ServiceType.ROOT) "root" else "shizuku"
            settings.lastServiceMode = mode
            settings.lastXposed = XposedState.isActive
            container.setServiceStatus(ContainerFragment.ServiceStatus(modeLabel(mode, XposedState.isActive), ContainerFragment.StatusLevel.OK))
            return
        }
        if (connecting) {
            val last = settings.lastServiceMode
            val label = if (last.isEmpty()) getString(R.string.status_label_connecting) else modeLabel(last, settings.lastXposed)
            container.setServiceStatus(ContainerFragment.ServiceStatus(label, ContainerFragment.StatusLevel.PENDING))
        } else {
            container.setServiceStatus(
                ContainerFragment.ServiceStatus(getString(R.string.status_label_not_connected), ContainerFragment.StatusLevel.ERROR),
                onClick = { viewModel.onFaqClicked() }
            )
        }
    }

    private fun modeLabel(mode: String, xposed: Boolean): String {
        val name = getString(if (mode == "root") R.string.status_label_root else R.string.status_label_shizuku)
        return if (xposed) "$name · ${getString(R.string.status_label_xposed)}" else name
    }

    private fun whitelistContent(): CharSequence {
        val count = settings.enabledApps.size
        return if (count == 0) getString(R.string.item_whitelist_content) else resources.getQuantityString(R.plurals.status_content_apps, count, count)
    }

    private fun refreshWhitelistRow() {
        val count = settings.enabledApps.size
        if (count == lastAppCount) return
        lastAppCount = count
        adapter.notifyItemChanged(0)
    }

    private fun refreshMainSwitchLabel() {
        val warning = switchWarning
        binding.switchMain.mainSwitchSwitch.text = getMainSwitchLabel(warning?.let { getString(it) })
    }

    private fun setupMainSwitch() {
        binding.switchMain.run {
            mainSwitchSwitch.typeface = ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium)
            updateMainSwitch()
        }
        //The first frame already has the status, drawn from the last known mode, so nothing pops in afterwards
        publishStatus(connecting = true)
        lastAppCount = settings.enabledApps.size
        refreshMainSwitchLabel()
        lifecycleScope.launch {
            sharedViewModel.switchWarning.collect {
                val update = {
                    switchWarning = it
                    refreshMainSwitchLabel()
                }
                if(!isAdded){
                    lifecycleScope.launchWhenResumed {
                        update.invoke()
                    }
                }else{
                    update.invoke()
                }
            }
        }
    }

    private fun updateMainSwitch() = with(binding.switchMain) {
        mainSwitchSwitch.setOnCheckedChangeListener(null)
        mainSwitchSwitch.isChecked = settings.enabled
        mainSwitchSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.enabled = isChecked
        }
    }

    //Fragment-level cache to prevent flickering
    private var isAutoDarkChecked: Boolean? = null
    private fun setupAutoDarkTheme(){
        lifecycleScope.launchWhenResumed {
            sharedViewModel.autoDarkTheme.collect {
                if(isAutoDarkChecked == it) return@collect
                isAutoDarkChecked = it
                adapter.notifyItemChanged(2)
            }
        }
    }

    private fun setupDeveloperOptions(){
        lifecycleScope.launchWhenResumed {
            viewModel.developerOptionsVisible.collect {
                adapter.notifyDataSetChanged()
            }
        }
    }

    //The LibXposed service binds asynchronously, so the Xposed entry appears once LSPosed or Vector delivers it
    private fun setupXposedState(){
        lifecycleScope.launchWhenResumed {
            XposedBridge.isActive.collect {
                publishStatus(connecting = connection.connectedServiceType == DarqServiceConnectionProvider.ServiceType.UNKNOWN)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun getMainSwitchLabel(subtitle: CharSequence?): CharSequence {
        return if(subtitle == null){
            getString(R.string.item_switch_main_title)
        }else{
            val sizeSpan = RelativeSizeSpan(0.75f)
            val fontSpan = TypefaceSpan(googleSans ?: Typeface.DEFAULT)
            val secondLine = SpannableString(subtitle).apply {
                setSpan(sizeSpan, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                setSpan(fontSpan, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
            SpannableStringBuilder().apply {
                appendLine(getString(R.string.item_switch_main_title))
                append(secondLine)
            }
        }
    }

    private fun setupRestoreListener(){
        lifecycleScope.launchWhenResumed {
            sharedViewModel.restoreBus.collect {
                adapter.notifySwitchSettings()
                updateMainSwitch()
            }
        }
    }

    override fun inflateMenu(menuInflater: MenuInflater, menu: Menu) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when(menuItem.itemId){
            R.id.menu_open_source_libraries -> {
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.menu_open_source_libraries))
                viewModel.onOssLicencesClicked()
            }
        }
        return true
    }

}