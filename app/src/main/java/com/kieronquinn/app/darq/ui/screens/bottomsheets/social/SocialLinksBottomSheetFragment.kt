package com.kieronquinn.app.darq.ui.screens.bottomsheets.social

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetSocialLinksBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.openLink

class SocialLinksBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetSocialLinksBinding>(FragmentBottomSheetSocialLinksBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            socialLinksCancel.setOnClickListener {
                dismiss()
            }
            socialWebsite.setOnClickListener {
                requireContext().openLink("https://mohit-arora.me/")
                dismiss()
            }
            socialGithubProfile.setOnClickListener {
                requireContext().openLink("https://github.com/Arora-Sir")
                dismiss()
            }
            socialTwitter.setOnClickListener {
                requireContext().openLink("https://x.com/arorasir")
                dismiss()
            }
            socialInstagram.setOnClickListener {
                requireContext().openLink("https://www.instagram.com/arora.sir/?hl=en")
                dismiss()
            }
            socialReddit.setOnClickListener {
                requireContext().openLink("https://www.reddit.com/user/AroraSir/")
                dismiss()
            }

            val accent = ColorStateList.valueOf(monet.getAccentColor(requireContext()))
            socialIcWebsite.imageTintList = accent
            socialIcGithubProfile.imageTintList = accent
            socialIcTwitter.imageTintList = accent
            socialIcInstagram.imageTintList = accent
            socialIcReddit.imageTintList = accent
            socialLinksCancel.setTextColor(accent)

            val cardBg = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bottom_sheet_card_background)
            val backgroundTint = ColorStateList.valueOf(cardBg)
            socialWebsite.backgroundTintList = backgroundTint
            socialGithubProfile.backgroundTintList = backgroundTint
            socialTwitter.backgroundTintList = backgroundTint
            socialInstagram.backgroundTintList = backgroundTint
            socialReddit.backgroundTintList = backgroundTint

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val extraPadding = resources.getDimension(R.dimen.padding_8).toInt()
                v.updatePadding(bottom = bottomInset + extraPadding)
                insets
            }
        }
    }
}
