package com.kieronquinn.app.darq.ui.screens.settings.apppicker

import android.content.res.ColorStateList
import com.kieronquinn.app.darq.utils.Toasts
import com.kieronquinn.app.darq.components.xposed.XposedBridge
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentAppPickerBinding
import com.kieronquinn.app.darq.model.settings.AppPickerItem
import com.kieronquinn.app.darq.ui.base.BackAvailable
import com.kieronquinn.app.darq.ui.base.BoundFragment
import com.kieronquinn.app.darq.ui.base.ProvidesOverflow
import com.kieronquinn.app.darq.ui.screens.container.ContainerSharedViewModel
import com.kieronquinn.app.darq.ui.utils.TransitionUtils
import com.kieronquinn.app.darq.utils.extensions.applyThemeToFastScroller
import com.kieronquinn.app.darq.utils.extensions.expandAppBar
import com.kieronquinn.app.darq.utils.extensions.navGraphViewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel


class SettingsAppPickerFragment :
    BoundFragment<FragmentAppPickerBinding>(FragmentAppPickerBinding::inflate), BackAvailable, ProvidesOverflow {

    private val viewModel by viewModel<SettingsAppPickerViewModel>()
    private val sharedViewModel by navGraphViewModel<ContainerSharedViewModel>(R.id.nav_graph_main)

    private val adapter by lazy {
        SettingsAppPickerAdapter(
            requireContext(),
            emptyList<AppPickerItem>().toMutableList(),
            this::onPackageEnabledChanged
        )
    }

    private val searchTextWatcher = object : TextWatcher {

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            viewModel.setSearchTerm(s?.toString() ?: return)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun afterTextChanged(s: Editable?) {}

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = TransitionUtils.getMaterialSharedAxis(requireContext(), true)
        enterTransition = TransitionUtils.getMaterialSharedAxis(requireContext(), true)
        returnTransition = TransitionUtils.getMaterialSharedAxis(requireContext(), false)
        reenterTransition = TransitionUtils.getMaterialSharedAxis(requireContext(), false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        handleLoadState(viewModel.loadState.value)
        setupViewModel()
        setupSearch()
        setupSnackbarPadding()
    }

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SettingsAppPickerFragment.adapter
            ViewCompat.setOnApplyWindowInsetsListener(this){ view, insets ->
                val requiredInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
                updatePadding(left = requiredInsets.left, right = requiredInsets.right, bottom = requiredInsets.bottom)
                insets
            }
            applyThemeToFastScroller()
        }
    }

    private fun setupViewModel() {
        lifecycleScope.launchWhenResumed {
            launch {
                viewModel.loadState.debounce(50).collect {
                    handleLoadState(it)
                }
            }
            launch {
                viewModel.loadingProgress.debounce(100).collect { progress ->
                    if (progress != null && progress.second > 0) {
                        binding.appPickerLoadingText.text = getString(R.string.loading_apps_progress, progress.first, progress.second)
                    } else {
                        binding.appPickerLoadingText.text = getString(R.string.loading_apps)
                    }
                }
            }
        }
    }

    private fun handleLoadState(loadState: SettingsAppPickerViewModel.LoadState) {
        setLoadingState(
            loadState is SettingsAppPickerViewModel.LoadState.Loading,
            (loadState is SettingsAppPickerViewModel.LoadState.Loaded && loadState.apps.isEmpty())
        )
        if (loadState is SettingsAppPickerViewModel.LoadState.Loaded) {
            adapter.setItems(loadState.apps)
            //An empty result with a search term offers a way back to the full list
            binding.appPickerEmptyClear.isVisible = loadState.apps.isEmpty() && viewModel.getSearchTerm().isNotEmpty()
        }
    }

    private fun setLoadingState(loading: Boolean, isEmpty: Boolean) {
        binding.recyclerView.isVisible = !loading && !isEmpty
        binding.appPickerLoading.isVisible = loading && !isEmpty
        binding.appPickerEmpty.isVisible = isEmpty
    }

    private fun setupSearch() {
        with(binding.appPickerSearch) {
            val background = themeColors.getBackgroundColor(requireContext())
            val secondaryBackground = themeColors.getBackgroundColorSecondary(requireContext()) ?: background
            root.setBackgroundColor(background)
            searchBox.backgroundTintList = ColorStateList.valueOf(secondaryBackground)
            searchBox.text.run {
                clear()
                append(viewModel.getSearchTerm())
            }
            searchBox.setOnClickListener {
                expandAppBar()
            }
            lifecycleScope.launchWhenResumed {
                viewModel.showSearchClearButton.collect {
                    searchClear.isVisible = it
                }
            }
            searchClear.setOnClickListener {
                searchBox.text.clear()
            }
        }
        binding.appPickerEmptyClear.setOnClickListener {
            binding.appPickerSearch.searchBox.text.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.appPickerSearch.searchBox.addTextChangedListener(searchTextWatcher)
    }

    override fun onPause() {
        super.onPause()
        binding.appPickerSearch.searchBox.removeTextChangedListener(searchTextWatcher)
    }

    private fun onPackageEnabledChanged(appPickerItem: AppPickerItem.App){
        viewModel.onPackageEnabledChanged(appPickerItem)
        sharedViewModel.queueIPCSync(appPickerItem.toIPCSetting())
        //On LSPosed 2.x and Vector the app can be added to the module scope directly, so users do not have to do it in the manager
        if(appPickerItem.enabled && XposedBridge.isActiveNow){
            XposedBridge.requestScope(listOf(appPickerItem.packageName)) { result ->
                val context = context ?: return@requestScope
                when(result){
                    is XposedBridge.ScopeResult.Approved -> Toasts.show(context, getString(R.string.xposed_scope_approved, appPickerItem.label))
                    is XposedBridge.ScopeResult.Failed -> Toasts.show(context, getString(R.string.xposed_scope_failed, result.message), long = true)
                    else -> {}
                }
            }
        }
    }

    private fun setupSnackbarPadding(){
        lifecycleScope.launchWhenResumed {
            sharedViewModel.showSnackbar.collect {
                if(it){
                    adapter.addSnackbarPadding()
                }else{
                    adapter.removeSnackbarPadding()
                }
            }
        }
    }

    override fun inflateMenu(menuInflater: MenuInflater, menu: Menu) {
        menuInflater.inflate(R.menu.menu_app_picker, menu)
        menu.findItem(R.id.menu_app_picker_show_non_launchable_apps).run {
            isChecked = viewModel.getShowAllApps()
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if(menuItem.itemId != R.id.menu_app_picker_show_non_launchable_apps) return false
        toggleLaunchableApps(menuItem)
        return true
    }

    private fun toggleLaunchableApps(menuItem: MenuItem){
        val newState = !menuItem.isChecked
        menuItem.isChecked = newState
        viewModel.setShowAllApps(newState)
    }

}