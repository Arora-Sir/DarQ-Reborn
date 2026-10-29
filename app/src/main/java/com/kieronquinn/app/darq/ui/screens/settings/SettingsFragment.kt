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
                tapAction = viewModel::onAppWhitelistClicked
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
                R.drawable.ic_update,
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

    // NOTE: The service binds a moment after the list appears, so the card shows a connecting state and polls before it reports a problem.
    override fun onResume() {
        super.onResume()
        statusJob?.cancel()
        statusJob = viewLifecycleOwner.lifecycleScope.launch {
            var waited = 0L
            while (isActive) {
                val connected = connection.connectedServiceType != DarqServiceConnectionProvider.ServiceType.UNKNOWN
                updateStatusCard(connecting = !connected && waited < STATUS_CONNECT_WAIT_MS)
                if (connected) break
                delay(STATUS_POLL_MS)
                waited += STATUS_POLL_MS
            }
        }
    }

    override fun onPause() {
        statusJob?.cancel()
        super.onPause()
    }

    // NOTE: The status is the second line of the main switch. It inherits the switch text color, so it can never flash white on the accent container.
    // NOTE: The label is only set again when its text changed, so coming back from another screen causes no redraw.
    private var statusLine: String? = null
    private var switchWarning: Int? = null

    private fun statusText(connecting: Boolean): String {
        val type = connection.connectedServiceType
        val connected = type != DarqServiceConnectionProvider.ServiceType.UNKNOWN
        if (!connected) {
            return getString(if (connecting) R.string.status_title_connecting else R.string.status_content_not_connected)
        }
        val count = settings.enabledApps.size
        val title = getString(if (type == DarqServiceConnectionProvider.ServiceType.ROOT) R.string.status_title_root else R.string.status_title_shizuku)
        //No apps yet is left out, the first list row already says to select some, and the line stays on one row
        val apps = if (count == 0) null else resources.getQuantityString(R.plurals.status_content_apps, count, count)
        val details = when {
            apps == null -> null
            XposedState.isActive -> getString(R.string.status_content_xposed, apps)
            else -> apps
        }
        return if (details == null) title else "$title  •  $details"
    }

    private fun updateStatusCard(connecting: Boolean) {
        val text = statusText(connecting)
        if (text == statusLine) return
        statusLine = text
        refreshMainSwitchLabel()
    }

    private fun refreshMainSwitchLabel() {
        val warning = switchWarning
        binding.switchMain.mainSwitchSwitch.text = if (warning != null) getMainSwitchLabel(getString(warning)) else getMainSwitchLabel(statusLine)
    }

    private fun setupMainSwitch() {
        binding.switchMain.run {
            mainSwitchSwitch.typeface = ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium)
            updateMainSwitch()
        }
        //The first frame already has the status, so there is no empty second line that then fills in
        statusLine = statusText(connecting = true)
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