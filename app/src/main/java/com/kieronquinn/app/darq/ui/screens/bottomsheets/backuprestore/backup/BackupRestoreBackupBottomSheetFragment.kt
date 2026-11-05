package com.kieronquinn.app.darq.ui.screens.bottomsheets.backuprestore.backup

import android.os.Bundle
import com.kieronquinn.app.darq.utils.Toasts
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetBackupBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class BackupRestoreBackupBottomSheetFragment: BaseBottomSheetFragment<FragmentBottomSheetBackupBinding>(FragmentBottomSheetBackupBinding::inflate) {

    private val viewModel by viewModel<BackupRestoreBackupBottomSheetViewModel>()
    private val arguments by navArgs<BackupRestoreBackupBottomSheetFragmentArgs>()

    override val cancelable = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding){
            ViewCompat.setOnApplyWindowInsetsListener(root){ view, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val extraPadding = resources.getDimension(R.dimen.padding_8).toInt()
                view.updatePadding(bottom = bottomInset + extraPadding)
                insets
            }
        }
        lifecycleScope.launchWhenResumed {
            launch {
                viewModel.state.collect {
                    if (it is BackupRestoreBackupBottomSheetViewModel.State.Complete) {
                        handleComplete(it.result)
                    }
                }
            }
            launch {
                viewModel.setOutputUri(arguments.uri)
            }
        }
    }

    private fun handleComplete(result: BackupRestoreBackupBottomSheetViewModel.Result){
        when(result){
            BackupRestoreBackupBottomSheetViewModel.Result.SUCCESS -> {
                Toasts.show(requireContext(), R.string.item_backup_restore_backup_success, long = true)
            }
            BackupRestoreBackupBottomSheetViewModel.Result.FAILED -> {
                Toasts.show(requireContext(), R.string.item_backup_restore_backup_failed, long = true)
            }
        }
    }

}