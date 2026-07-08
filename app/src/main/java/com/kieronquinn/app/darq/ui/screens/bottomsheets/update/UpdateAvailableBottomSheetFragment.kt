package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetDialogFragment
import com.kieronquinn.app.darq.ui.screens.container.ContainerSharedViewModel
import com.kieronquinn.app.darq.utils.openLink
import com.kieronquinn.app.darq.utils.extensions.navGraphViewModel
import ru.noties.markwon.Markwon

class UpdateAvailableBottomSheetFragment: BaseBottomSheetDialogFragment() {

    private val sharedViewModel by navGraphViewModel<ContainerSharedViewModel>(R.id.nav_graph_main)

    override val cancelable = false

    private val update by lazy {
        sharedViewModel.getAvailableUpdate()
    }

    override val title by lazy {
        getString(R.string.bottom_sheet_update_available_title)
    }

    override val content by lazy {
        update?.changelog ?: ""
    }

    override val positiveText by lazy {
        getString(R.string.bottom_sheet_update_available_positive)
    }

    override val negativeText by lazy {
        getString(R.string.bottom_sheet_update_available_negative)
    }

    override val neutralText by lazy {
        getString(R.string.bottom_sheet_update_available_neutral)
    }

    override fun onNegativeClicked(dialog: BottomSheetDialog) {
        sharedViewModel.clearUpdate()
        super.onNegativeClicked(dialog)
    }

    override fun onPositiveClicked(dialog: BottomSheetDialog) {
        lifecycleScope.launchWhenResumed {
            navigation.navigate(UpdateAvailableBottomSheetFragmentDirections.actionUpdateAvailableBottomSheetFragmentToUpdateDownloadBottomSheetFragment())
            dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val markwon = Markwon.create(requireContext())
        markwon.setMarkdown(binding.bottomSheetContent, content.toString())
    }

    override fun onNeutralClicked(dialog: BottomSheetDialog) {
        update?.releaseUrl?.let {
            requireContext().openLink(it)
        }
    }

}