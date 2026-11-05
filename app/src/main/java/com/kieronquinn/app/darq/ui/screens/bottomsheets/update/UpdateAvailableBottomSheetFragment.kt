package com.kieronquinn.app.darq.ui.screens.bottomsheets.update

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.util.Log
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetDialogFragment
import com.kieronquinn.app.darq.ui.screens.container.ContainerSharedViewModel
import com.kieronquinn.app.darq.utils.openLink
import com.kieronquinn.app.darq.utils.extensions.navGraphViewModel
import com.kieronquinn.app.darq.utils.LinkOpener
import com.kieronquinn.app.darq.utils.ObtainiumLink
import ru.noties.markwon.AbstractMarkwonPlugin
import ru.noties.markwon.Markwon
import ru.noties.markwon.MarkwonConfiguration

class UpdateAvailableBottomSheetFragment: BaseBottomSheetDialogFragment() {

    private val sharedViewModel by navGraphViewModel<ContainerSharedViewModel>(R.id.nav_graph_main)

    override val cancelable = true

    private val update by lazy {
        sharedViewModel.getAvailableUpdate()
    }

    override val title by lazy {
        getString(R.string.bottom_sheet_update_available_title)
    }

    override val content by lazy {
        val currentVer = com.kieronquinn.app.darq.BuildConfig.VERSION_NAME
        val newVer = update?.version ?: ""
        val versionInfo = "**Current Version:** $currentVer\n**New Version:** $newVer\n\n"
        //The one-tap Obtainium link sits under the changelog, so the three buttons stay Download, Close and View on GitHub
        versionInfo + (update?.changelog ?: "") + "\n\n[" + getString(R.string.bottom_sheet_update_obtainium) + "](" + ObtainiumLink.url() + ")"
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

    private var navigatingToDownload = false

    override fun onNegativeClicked(dialog: BottomSheetDialog) {
        Log.d("DarQUpdate", "UpdateAvailableBottomSheetFragment: onNegativeClicked, clearing update and closing sheet")
        sharedViewModel.clearUpdate()
        super.onNegativeClicked(dialog)
    }

    override fun onPositiveClicked(dialog: BottomSheetDialog) {
        Log.d("DarQUpdate", "UpdateAvailableBottomSheetFragment: onPositiveClicked, navigating to download bottom sheet")
        navigatingToDownload = true
        lifecycleScope.launchWhenResumed {
            navigation.navigate(UpdateAvailableBottomSheetFragmentDirections.actionUpdateAvailableBottomSheetFragmentToUpdateDownloadBottomSheetFragment())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("DarQUpdate", "UpdateAvailableBottomSheetFragment: onViewCreated. Title: $title, content is empty: ${content.isEmpty()}, updateObj: $update")
        super.onViewCreated(view, savedInstanceState)
        //Links in the changelog open through the safe link helper, never straight from a URLSpan
        val markwon = Markwon.builder(requireContext())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> LinkOpener.open(requireContext(), link) }
                }
            })
            .build()
        markwon.setMarkdown(binding.bottomSheetContent, content.toString())
    }

    override fun onNeutralClicked(dialog: BottomSheetDialog) {
        Log.d("DarQUpdate", "UpdateAvailableBottomSheetFragment: onNeutralClicked, opening releaseUrl = ${update?.releaseUrl}")
        update?.releaseUrl?.let {
            requireContext().openLink(it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        Log.d("DarQUpdate", "UpdateAvailableBottomSheetFragment: onDismiss, navigatingToDownload = $navigatingToDownload")
        super.onDismiss(dialog)
        if (!navigatingToDownload) {
            sharedViewModel.clearUpdate()
        }
    }

}