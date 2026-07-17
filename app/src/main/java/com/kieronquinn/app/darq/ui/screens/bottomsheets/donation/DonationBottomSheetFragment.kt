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
import com.kieronquinn.app.darq.utils.Links
import com.kieronquinn.app.darq.utils.openLink

class DonationBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetDonationBinding>(FragmentBottomSheetDonationBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            donationCancel.setOnClickListener {
                dismiss()
            }
            donationUpi.setOnClickListener {
                requireContext().openLink(Links.LINK_UPI)
                dismiss()
            }
            donationPaypal.setOnClickListener {
                requireContext().openLink(Links.LINK_DONATE)
                dismiss()
            }

            val accent = ColorStateList.valueOf(monet.getAccentColor(requireContext()))
            donationIcUpi.imageTintList = accent
            donationIcPaypal.imageTintList = accent
            donationCancel.setTextColor(accent)

            val cardBg = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bottom_sheet_card_background)
            donationUpi.backgroundTintList = ColorStateList.valueOf(cardBg)
            donationPaypal.backgroundTintList = ColorStateList.valueOf(cardBg)

            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val extraPadding = resources.getDimension(R.dimen.padding_8).toInt()
                v.updatePadding(bottom = bottomInset + extraPadding)
                insets
            }
        }
    }
}
