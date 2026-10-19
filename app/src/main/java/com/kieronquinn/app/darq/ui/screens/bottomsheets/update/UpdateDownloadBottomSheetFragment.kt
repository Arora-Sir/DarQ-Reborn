package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetUpdateDownloadBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.ui.screens.container.ContainerSharedViewModel
import com.kieronquinn.app.darq.utils.extensions.navGraphViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class UpdateDownloadBottomSheetFragment: BaseBottomSheetFragment<FragmentBottomSheetUpdateDownloadBinding>(FragmentBottomSheetUpdateDownloadBinding::inflate) {

    override fun onDismiss(dialog: DialogInterface) {
        Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: onDismiss - clearing update state")
        super.onDismiss(dialog)
        sharedViewModel.clearUpdate()
    }

    private val updateViewModel by viewModel<UpdateDownloadBottomSheetViewModel>()

    private val sharedViewModel by navGraphViewModel<ContainerSharedViewModel>(R.id.nav_graph_main)

    override val cancelable = false

    private val update by lazy {
        sharedViewModel.getAvailableUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: onViewCreated. Available update: $update")
        super.onViewCreated(view, savedInstanceState)
        binding.fragmentUpdateDownloadCancel.setOnClickListener {
            Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: Cancel button clicked, cancelling download")
            updateViewModel.cancelDownload(requireContext())
            dismiss()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root){ view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraPadding = resources.getDimension(R.dimen.padding_8).toInt()
            view.updatePadding(bottom = bottomInset + extraPadding)
            insets
        }
        val accentColor = themeColors.getAccentColor(requireContext())
        binding.fragmentUpdateDownloadCancel.setTextColor(accentColor)
        binding.fragmentUpdateDownloadInstall.setTextColor(accentColor)
        viewLifecycleOwner.lifecycleScope.launch {
            updateViewModel.downloadState.collect { state ->
                Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: State update received = $state")
                when(state){
                    is UpdateDownloadBottomSheetViewModel.State.Downloading -> {
                        if(state.progress > 0) {
                            binding.fragmentUpdateDownloadProgress.isIndeterminate = false
                            binding.fragmentUpdateDownloadProgress.progress = state.progress
                        }
                    }
                    is UpdateDownloadBottomSheetViewModel.State.Done -> {
                        Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: Download Done. FileUri: ${state.fileUri}")
                        binding.fragmentUpdateDownloadTitle.text = getString(R.string.download_complete)
                        binding.fragmentUpdateDownloadProgress.isIndeterminate = false
                        binding.fragmentUpdateDownloadProgress.progress = 100

                        binding.fragmentUpdateDownloadCancel.text = getString(R.string.close)
                        binding.fragmentUpdateDownloadCancel.setOnClickListener {
                            Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: Close button clicked after download complete")
                            sharedViewModel.clearUpdate()
                            dismiss()
                        }

                        binding.fragmentUpdateDownloadInstall.visibility = View.VISIBLE
                        binding.fragmentUpdateDownloadInstall.setOnClickListener {
                            Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: Install button clicked, calling openPackageInstaller")
                            updateViewModel.openPackageInstaller(requireContext(), state.fileUri)
                            sharedViewModel.clearUpdate()
                            dismiss()
                        }
                    }
                    is UpdateDownloadBottomSheetViewModel.State.Failed -> {
                        val message = state.errorMsg?.let { "Download failed: $it" } ?: getString(R.string.bs_update_download_failed)
                        Log.e("DarQUpdate", "UpdateDownloadBottomSheetFragment: Download Failed, showing Toast: $message")
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        dismiss()
                    }
                    UpdateDownloadBottomSheetViewModel.State.Idle -> {
                        Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: State is Idle")
                    }
                }
            }
        }
        update?.let {
            Log.d("DarQUpdate", "UpdateDownloadBottomSheetFragment: Starting download for update: ${it.name}, assetUrl: ${it.assetUrl}")
            updateViewModel.startDownload(requireContext(), it)
        } ?: run {
            Log.e("DarQUpdate", "UpdateDownloadBottomSheetFragment: Update is null inside onViewCreated, dismissing bottom sheet")
            dismiss()
        }
    }

}