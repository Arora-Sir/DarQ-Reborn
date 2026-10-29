package com.kieronquinn.app.darq.ui.screens.container

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuInflater
import android.view.View
import android.view.animation.Animation
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.navigation.Navigation
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.databinding.FragmentContainerBinding
import com.kieronquinn.app.darq.model.darq.DarqConstants
import com.kieronquinn.app.darq.providers.DarqServiceConnectionProvider
import com.kieronquinn.app.darq.service.autodark.DarqAutoDarkForegroundService
import com.kieronquinn.app.darq.ui.activities.DarqActivity
import com.kieronquinn.app.darq.ui.base.*
import com.kieronquinn.app.darq.utils.extensions.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

private const val STATE_TITLE = "container_title"
private const val STATE_BACK = "container_back"
private const val STATE_STATUS = "container_status_visible"

class ContainerFragment: BoundFragment<FragmentContainerBinding>(FragmentContainerBinding::inflate) {

    internal val settings by inject<DarqSharedPreferences>()

    private val navHostFragment by lazy {
        childFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    }

    private val navController by lazy {
        navHostFragment.navController
    }

    private val sharedViewModel by lazy {
        navGraphViewModel<ContainerSharedViewModel>(R.id.nav_graph_main, navController).value
    }

    private val navigation by inject<Navigation>()

    private val fragmentExpandedState = HashMap<String, Boolean>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackground()
        setupInsets()
        setupToolbar(savedInstanceState)
        setupFragmentListener()
        setupNavigation()
        setupLoading()
        setupSnackbar()
        setupSettingsListener()
        setupAutoDarkService()
        setupUpdateChecker()
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            lifecycleScope.launch {
                navigation.navigateBack()
            }
        }
    }

    private fun setupBackground(){
        val backgroundColor = themeColors.getBackgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)
        binding.appBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    // NOTE: The app bar is sized with the status bar height from the very first layout, so the title never jumps down when the insets arrive.
    private fun applyInitialAppBarSize(){
        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android").let {
            if(it > 0) resources.getDimensionPixelSize(it) else 0
        }
        val appBarHeight = resources.getDimension(R.dimen.app_bar_height)
        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            height = (appBarHeight + statusBarHeight).roundToInt()
        }
        getToolbarHeight()?.let { toolbarHeight ->
            binding.toolbar.updateLayoutParams<CollapsingToolbarLayout.LayoutParams> {
                height = toolbarHeight + statusBarHeight
            }
            binding.toolbar.updatePadding(top = statusBarHeight)
        }
    }

    private fun setupInsets(){
        applyInitialAppBarSize()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(left = navigationInsets.left, right = navigationInsets.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.collapsingToolbar){ view, insets ->
            view.updateLayoutParams<AppBarLayout.LayoutParams> {
                val appBarHeight = view.context.resources.getDimension(R.dimen.app_bar_height)
                height = (appBarHeight + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top).roundToInt()
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar){ view, insets ->
            getToolbarHeight()?.let {
                val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val overflowPadding = resources.getDimension(R.dimen.padding_8)
                val topInset = statusInsets.top
                view.updateLayoutParams<CollapsingToolbarLayout.LayoutParams> {
                    height = it + topInset
                }
                view.updatePadding(left = statusInsets.left, top = topInset, right = statusInsets.right + overflowPadding.toInt())
            }
            insets
        }
    }

    private fun getToolbarHeight(): Int? {
        val typedValue = TypedValue()
        return if (requireContext().theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        }else null
    }

    // NOTE: A recreate (Pure black, accent) restores the title that was on screen, so the app name never shows for a few frames.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(view != null) {
            outState.putString(STATE_TITLE, binding.toolbar.title?.toString())
            outState.putBoolean(STATE_BACK, binding.toolbar.navigationIcon != null)
            outState.putBoolean(STATE_STATUS, statusVisible)
        }
    }

    private fun setupToolbar(savedInstanceState: Bundle?){
        with(binding){
            toolbar.title = savedInstanceState?.getString(STATE_TITLE) ?: getString(R.string.app_name)
            statusVisible = savedInstanceState?.getBoolean(STATE_STATUS, true) ?: true
            if(savedInstanceState?.getBoolean(STATE_BACK) == true) {
                toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_back)
            }
            //Always start expanded and without animation, the top fragment then restores its own saved state, so the large title is there from the first frame
            appBar.setExpanded(!requireContext().isLandscape, false)
            appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val currentDestClass = (navController.currentDestination as? androidx.navigation.fragment.FragmentNavigator.Destination)?.className
                if (currentDestClass != null) {
                    fragmentExpandedState[currentDestClass] = (verticalOffset == 0)
                }
            })
            toolbar.setNavigationOnClickListener {
                lifecycleScope.launchWhenResumed {
                    navigation.navigateBack()
                }
            }
            collapsingToolbar.setBackgroundColor(themeColors.getBackgroundColor(requireContext()))
            collapsingToolbar.setContentScrimColor(themeColors.getBackgroundColorSecondary(requireContext()))
            collapsingToolbar.setExpandedTitleTypeface(ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium))
            collapsingToolbar.setCollapsedTitleTypeface(ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium))
            toolbar.overflowIcon?.setTint(themeColors.getOnSurfaceColor(requireContext()))
        }
    }

    private fun setupFragmentListener(){
        navHostFragment.childFragmentManager.addOnBackStackChangedListener {
            getTopFragment()?.let {
                onTopFragmentChanged(it)
            }
        }
        binding.navHostFragment.post {
            onTopFragmentChanged(getTopFragment() ?: return@post)
        }
    }

    private fun setupNavigation() = lifecycleScope.launchWhenResumed {
        navigation.navigationBus.collect {
            handleNavigationEvent(it)
        }
    }

    private fun handleNavigationEvent(navigationEvent: Navigation.NavigationEvent) {
        when (navigationEvent) {
            is Navigation.NavigationEvent.Directions -> navController.navigateSafely(navigationEvent.directions)
            is Navigation.NavigationEvent.Id -> navController.navigateSafely(navigationEvent.id)
            is Navigation.NavigationEvent.Back -> if(!navController.navigateUp()) activity?.finish()
            is Navigation.NavigationEvent.PopupTo -> navController.popBackStack(
                navigationEvent.id,
                navigationEvent.popInclusive
            )
        }
        if(activity?.isFinishing != true) {
            val isDialog = navController.currentDestination?.navigatorName == "dialog"
            val isBack = navigationEvent is Navigation.NavigationEvent.Back || navigationEvent is Navigation.NavigationEvent.PopupTo
            if (!isDialog && !isBack) {
                binding.appBar.setExpanded(true, false)
            }
            activity?.hideKeyboard()
        }
    }

    private fun setupSnackbar(){
        ViewCompat.setOnApplyWindowInsetsListener(binding.snackbarContainer){ view, insets ->
            val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomInsets)
            insets
        }
        val background = themeColors.getBackgroundColorSecondary(requireContext())
        binding.snackbar.backgroundTintList = ColorStateList.valueOf(background)
        lifecycleScope.launchWhenResumed {
            launch {
                sharedViewModel.showSnackbar.collect {
                    setSnackbarVisibility(it)
                }
            }
            launch {
                sharedViewModel.syncState.debounce(DarqConstants.IPC_SYNC_TIMEOUT_LONG).collect {
                    setSnackbarState(it)
                }
            }
        }
    }

    private fun setupLoading() = lifecycleScope.launchWhenResumed {
        //Debounce by 100ms to prevent the 'flash' of the loading screen when resuming and the service is already connected
        sharedViewModel.loadingState.debounce(DarqConstants.IPC_SYNC_TIMEOUT).collect {
            handleServiceState(it)
        }
    }

    private suspend fun handleServiceState(serviceState: ContainerSharedViewModel.ServiceState) {
        binding.loadingFragment.isVisible = serviceState is ContainerSharedViewModel.ServiceState.Loading
        binding.navHostFragment.isVisible = serviceState is ContainerSharedViewModel.ServiceState.Loaded
        if(serviceState is ContainerSharedViewModel.ServiceState.Error){
            when(serviceState.error){
                DarqServiceConnectionProvider.ServiceFailureReason.SHIZUKU_PERMISSION_REQUIRED -> {
                    if(!requestShizukuPermission()){
                        //Shizuku not running
                        showShizukuNotRunningSheet()
                    }
                }
                DarqServiceConnectionProvider.ServiceFailureReason.SHIZUKU_NOT_INSTALLED -> {
                    showShizukuNotInstalledSheet()
                }
                DarqServiceConnectionProvider.ServiceFailureReason.SHIZUKU_NOT_STARTED -> {
                    showShizukuNotRunningSheet()
                }
                DarqServiceConnectionProvider.ServiceFailureReason.TIMEOUT -> {
                    showTimeoutSheet()
                }
            }
        }
    }

    private fun onTopFragmentChanged(topFragment: Fragment){
        statusVisible = topFragment is com.kieronquinn.app.darq.ui.screens.settings.SettingsFragment
        applyStatus()
        val backIcon = if(topFragment is BackAvailable){
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_back)
        } else null
        if(topFragment is ProvidesOverflow){
            setupMenu(topFragment)
        }else{
            setupMenu(null)
        }
        binding.toolbar.navigationIcon = backIcon
        navController.currentDestination?.label?.let {
            if(it.isBlank()) return@let
            binding.collapsingToolbar.title = it
            binding.toolbar.title = it
        }
        val isExpanded = fragmentExpandedState[topFragment::class.java.name] ?: (topFragment is AutoExpandOnRotate)
        binding.appBar.setExpanded(isExpanded, false)
    }

    enum class StatusLevel { PENDING, OK, ERROR }

    data class ServiceStatus(val label: String, val level: StatusLevel)

    private var serviceStatus: ServiceStatus? = null
    private var statusOnClick: (() -> Unit)? = null
    private var statusVisible = true
    private var shownDotColor: Int? = null

    // NOTE: The home screen reports the service status here. It shows as a dot and one word at the end of the title bar, and only on the home screen.
    // NOTE: The first call draws without animation, later changes fade the dot color in 150 ms and move nothing else.
    fun setServiceStatus(status: ServiceStatus?, onClick: (() -> Unit)? = null) {
        serviceStatus = status
        statusOnClick = onClick
        if(view != null) applyStatus()
    }

    private fun dotColor(level: StatusLevel): Int = when(level){
        StatusLevel.OK -> ContextCompat.getColor(requireContext(), R.color.status_dot_ok)
        StatusLevel.ERROR -> com.google.android.material.color.MaterialColors.getColor(binding.statusDot, com.google.android.material.R.attr.colorError)
        StatusLevel.PENDING -> com.google.android.material.color.MaterialColors.getColor(binding.statusDot, com.google.android.material.R.attr.colorOutline)
    }

    // NOTE: With a large system font the word would squeeze the title, so it moves under the title as the toolbar subtitle.
    private fun applyStatus() = with(binding) {
        val status = serviceStatus
        if(!statusVisible || status == null){
            statusContainer.isVisible = false
            toolbar.subtitle = null
            return@with
        }
        val color = dotColor(status.level)
        val largeFont = resources.configuration.fontScale > 1.15f
        statusContainer.contentDescription = getString(R.string.status_description, status.label)
        if(largeFont){
            statusContainer.isVisible = false
            toolbar.subtitle = android.text.SpannableString("● ${status.label}").apply {
                setSpan(android.text.style.ForegroundColorSpan(color), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }else{
            toolbar.subtitle = null
            statusContainer.isVisible = true
            statusLabel.text = status.label
            statusLabel.setTextColor(
                if(status.level == StatusLevel.ERROR) color else com.google.android.material.color.MaterialColors.getColor(statusLabel, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            val dot = statusDot.background.mutate()
            val from = shownDotColor
            if(from == null || from == color){
                dot.setTint(color)
            }else{
                android.animation.ValueAnimator.ofArgb(from, color).apply {
                    duration = 150
                    addUpdateListener { dot.setTint(it.animatedValue as Int) }
                }.start()
            }
            shownDotColor = color
            val onClick = statusOnClick
            if(status.level == StatusLevel.ERROR && onClick != null){
                statusContainer.setOnClickListener { onClick.invoke() }
                statusContainer.isClickable = true
            }else{
                statusContainer.setOnClickListener(null)
                statusContainer.isClickable = false
            }
        }
    }

    private fun setupMenu(menuProvider: ProvidesOverflow?){
        val menu = binding.toolbar.menu
        val menuInflater = MenuInflater(requireContext())
        menu.clear()
        menuProvider?.inflateMenu(menuInflater, menu)
        binding.toolbar.setOnMenuItemClickListener {
            menuProvider?.onMenuItemSelected(it) ?: false
        }
    }

    private fun getTopFragment(): Fragment? {
        if(!navHostFragment.isAdded) return null
        return navHostFragment.childFragmentManager.fragments.firstOrNull()
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.loadService()
        handlePendingNotificationNavigation()
    }

    /**
     *  Checked on every onResume (not just once via the async Navigation bus) so this reliably
     *  fires whether the app was cold-started or brought back from the background via
     *  singleTask's onNewIntent - a one-shot SharedFlow collector registered only in
     *  onViewCreated can race with the fragment not yet being RESUMED when the notification is
     *  tapped while backgrounded.
     */
    private fun handlePendingNotificationNavigation() {
        val activityIntent = requireActivity().intent ?: return
        if (activityIntent.getBooleanExtra(DarqActivity.EXTRA_OPEN_ADVANCED_SETTINGS, false) != true) return
        activityIntent.removeExtra(DarqActivity.EXTRA_OPEN_ADVANCED_SETTINGS)
        navController.navigateSafely(R.id.action_global_settingsAdvancedFragment)
    }

    private var snackbarAnimation: Animation? = null

    private fun setSnackbarVisibility(visible: Boolean){
        //A rebuilt screen starts with the snackbar gone, and sliding it out from there flashes it on screen for a moment
        if(!visible && !binding.snackbarContainer.isVisible && snackbarAnimation == null) return
        snackbarAnimation?.cancel()
        snackbarAnimation = if(visible) binding.snackbarContainer.slideIn {  }
        else binding.snackbarContainer.slideOut {  }
    }

    private fun setSnackbarState(syncState: ContainerSharedViewModel.SyncState){
        when(syncState){
            is ContainerSharedViewModel.SyncState.Syncing -> {
                binding.snackbarRoot.snackbarText.text = getString(R.string.snackbar_syncing_text)
                binding.snackbarRoot.snackbarAction.isVisible = false
                binding.snackbarRoot.snackbarProgress.isVisible = true
            }
            is ContainerSharedViewModel.SyncState.SyncComplete -> {
                binding.snackbarRoot.snackbarProgress.isVisible = false
                if(syncState.success){
                    val packageChange = syncState.lastSyncedSetting?.packageChange
                    if(packageChange != null){
                        val appLabel = try {
                            val pm = requireContext().packageManager
                            val appInfo = pm.getApplicationInfo(packageChange.packageName, 0)
                            appInfo.loadLabel(pm).toString()
                        } catch (e: Exception) {
                            packageChange.packageName
                        }
                        val message = if(packageChange.enabled){
                            getString(R.string.snackbar_syncing_complete_app_enabled, appLabel)
                        } else {
                            getString(R.string.snackbar_syncing_complete_app_disabled, appLabel)
                        }
                        binding.snackbarRoot.snackbarText.text = message
                    } else {
                        binding.snackbarRoot.snackbarText.text = getString(R.string.snackbar_syncing_complete_text)
                    }
                    binding.snackbarRoot.snackbarAction.isVisible = false
                }else{
                    binding.snackbarRoot.snackbarText.text = getString(R.string.snackbar_syncing_failed_text)
                    binding.snackbarRoot.snackbarAction.text = getString(R.string.snackbar_syncing_failed_button)
                    binding.snackbarRoot.snackbarAction.isVisible = true
                }
            }
            //NotSyncing leaves the snackbar as it is
            else -> {}
        }
    }

    private fun setupSettingsListener(){
        lifecycleScope.launchWhenResumed {
            settings.changed.collect {
                sharedViewModel.queueIPCSync(it)
            }
        }
    }

    private fun setupAutoDarkService(){
        lifecycleScope.launchWhenResumed {
            sharedViewModel.autoDarkServiceStartBus.collect {
                try {
                    requireContext().startForegroundService(Intent(requireContext(), DarqAutoDarkForegroundService::class.java).apply {
                        putExtra(DarqAutoDarkForegroundService.KEY_JUST_RESCHEDULE, true)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("ContainerFragment", "Failed to start auto dark service", e)
                }
            }
        }
    }

    fun expandAppBar(){
        binding.appBar.setExpanded(true, true)
    }

    private fun requestShizukuPermission(): Boolean {
        return try {
            Shizuku.requestPermission(0)
            true
        }catch (e: IllegalStateException){
            //Shizuku failed to respond = not running
            false
        }
    }

    private fun setupUpdateChecker(){
        lifecycleScope.launchWhenResumed {
            sharedViewModel.update.collect {
                if(it != null){
                    sharedViewModel.clearUpdateTrigger()
                    navigation.navigate(R.id.action_global_updateAvailableBottomSheetFragment)
                }
            }
        }
    }

    private fun showShizukuNotRunningSheet() = lifecycleScope.launchWhenResumed {
        navigation.navigate(R.id.action_global_shizkuNotRunningBottomSheetFragment)
    }

    private fun showTimeoutSheet() = lifecycleScope.launchWhenResumed {
        navigation.navigate(R.id.action_global_serviceTimeoutBottomSheetFragment)
    }

    private fun showShizukuNotInstalledSheet() = lifecycleScope.launchWhenResumed {
        navigation.navigate(R.id.action_global_shizukuNotInstalledBottomSheetFragment)
    }

}