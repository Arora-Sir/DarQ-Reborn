package com.kieronquinn.app.darq.ui.screens.bottomsheets.donation

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetDonationBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import com.kieronquinn.app.darq.utils.Links
import com.kieronquinn.app.darq.utils.SupportHelper
import com.kieronquinn.app.darq.utils.openLink

class DonationBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetDonationBinding>(FragmentBottomSheetDonationBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            donationCancel.setOnClickListener {
                dismiss()
            }
            donationUpi.setOnClickListener {
                SupportHelper.handleUpiPayment(requireContext())
                dismiss()
            }
            donationCopyUpi.setOnClickListener {
                SupportHelper.copyUpiId(requireContext())
                dismiss()
            }
            donationPaypal.setOnClickListener {
                requireContext().openLink(Links.LINK_DONATE)
                dismiss()
            }
            donationSponsors.setOnClickListener {
                requireContext().openLink(Links.LINK_SPONSORS)
                dismiss()
            }

            listOf(donationUpi, donationCopyUpi, donationPaypal, donationSponsors).forEach { it.applyPressFeedback() }
            val accent = ColorStateList.valueOf(themeColors.getAccentColor(requireContext()))
            donationIcUpi.imageTintList = accent
            donationIcCopyUpi.imageTintList = accent
            donationIcPaypal.imageTintList = accent
            donationIcSponsors.imageTintList = accent
            donationCancel.setTextColor(accent)

            val cardBg = themeColors.getBackgroundColorSecondary(requireContext())
            listOf(donationUpi, donationCopyUpi, donationPaypal, donationSponsors).forEach {
                it.backgroundTintList = ColorStateList.valueOf(cardBg)
            }

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val extraPadding = resources.getDimension(R.dimen.padding_8).toInt()
                v.updatePadding(bottom = bottomInset + extraPadding)
                insets
            }
        }
    }
}
