package com.kieronquinn.app.darq.ui.screens.bottomsheets.credits

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetCreditsBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import com.kieronquinn.app.darq.utils.Links
import com.kieronquinn.app.darq.utils.Toasts
import com.kieronquinn.app.darq.utils.openLink

// NOTE: Credits honor the upstream author first, then the projects DarQ runs on, then everyone who contributed on GitHub.
class CreditsBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetCreditsBinding>(FragmentBottomSheetCreditsBinding::inflate) {

    companion object {
        private const val SHIZUKU_URL = "https://github.com/RikkaApps/Shizuku"
        private const val LSPOSED_URL = "https://github.com/LSPosed/LSPosed"
        private const val LIBSU_URL = "https://github.com/topjohnwu/libsu"
        private const val CONTRIBUTORS_URL = "https://github.com/Arora-Sir/DarQ-Reborn/graphs/contributors"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val context = requireContext()
            val accent = ColorStateList.valueOf(themeColors.getAccentColor(context))
            listOf(creditsUpstream, creditsAuthor, creditsShizuku, creditsXposed, creditsLibsu, creditsContributors, creditsLicences).forEach { it.applyPressFeedback() }
            val cardTint = ColorStateList.valueOf(themeColors.getBackgroundColorSecondary(context))
            listOf(creditsIcUpstream, creditsIcAuthor, creditsIcShizuku, creditsIcXposed, creditsIcLibsu, creditsIcContributors, creditsIcLicences)
                .forEach { it.imageTintList = accent }
            listOf(creditsUpstream, creditsAuthor, creditsShizuku, creditsXposed, creditsLibsu, creditsContributors, creditsLicences)
                .forEach { it.backgroundTintList = cardTint }
            creditsCancel.setTextColor(accent)
            creditsCancel.setOnClickListener { dismiss() }

            creditsUpstream.setOnClickListener { open(Links.LINK_UPSTREAM) }
            creditsAuthor.setOnClickListener { open(Links.LINK_UPSTREAM_AUTHOR) }
            creditsShizuku.setOnClickListener { open(SHIZUKU_URL) }
            creditsXposed.setOnClickListener { open(LSPOSED_URL) }
            creditsLibsu.setOnClickListener { open(LIBSU_URL) }
            creditsContributors.setOnClickListener { open(CONTRIBUTORS_URL) }
            creditsLicences.setOnClickListener {
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.menu_open_source_libraries))
                try {
                    startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                } catch (e: Exception) {
                    Toasts.show(context, R.string.link_no_browser)
                }
                dismiss()
            }

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                v.updatePadding(bottom = bottomInset + resources.getDimension(R.dimen.padding_8).toInt())
                insets
            }
        }
    }

    private fun open(url: String) {
        requireContext().openLink(url)
        dismiss()
    }

}
