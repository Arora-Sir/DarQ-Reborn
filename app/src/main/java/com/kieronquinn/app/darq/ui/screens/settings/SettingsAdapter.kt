package com.kieronquinn.app.darq.ui.screens.settings

import com.kieronquinn.app.darq.components.theme.ThemeColors
import com.kieronquinn.app.darq.components.theme.SettingsIcons
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.findNavController
import androidx.viewbinding.ViewBinding
import com.kieronquinn.app.darq.databinding.*
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.model.settings.SettingsItemType
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import android.content.res.ColorStateList
import android.view.View
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import android.content.DialogInterface
import com.kieronquinn.app.darq.utils.Links
import com.kieronquinn.app.darq.ui.utils.MultiTapDetector
import com.kieronquinn.app.darq.utils.LinkSpans
import com.kieronquinn.app.darq.utils.SafeRun
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import com.kieronquinn.app.darq.utils.openLink

class SettingsAdapter(context: Context, private var items: List<SettingsItem>): RecyclerView.Adapter<SettingsAdapter.ViewHolder>(), KoinComponent {

    private val settings by inject<DarqSharedPreferences>()
    private val resources = context.resources

    init {
        setHasStableIds(true)
    }

    private val layoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    private val themeColors = ThemeColors

    private val visibleItems
        get() = items.filter { it.visible.invoke() }

    override fun getItemCount(): Int = visibleItems.size

    override fun getItemViewType(position: Int): Int {
        return visibleItems[position].itemType.ordinal
    }

    override fun getItemId(position: Int): Long {
        return when(val item = visibleItems[position]){
            is SettingsItem.SwitchSetting -> item.title.hashCode().toLong()
            is SettingsItem.Setting -> item.title.hashCode().toLong()
            is SettingsItem.AboutSetting -> item.title.hashCode().toLong()
            is SettingsItem.Header -> item.title.hashCode().toLong()
            is SettingsItem.SnackbarPadding -> 0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(SettingsItemType.values()[viewType]){
            SettingsItemType.HEADER -> ViewHolder.Header(ItemHeaderBinding.inflate(layoutInflater, parent, false))
            SettingsItemType.SETTING -> ViewHolder.SettingsSetting(ItemSettingBinding.inflate(layoutInflater, parent, false).also { it.root.applyPressFeedback() })
            SettingsItemType.ABOUT_SETTING -> ViewHolder.SettingsAboutSetting(ItemSettingAboutBinding.inflate(layoutInflater, parent, false))
            SettingsItemType.SWITCH_SETTING -> ViewHolder.SettingsSwitchSetting(ItemSettingSwitchBinding.inflate(layoutInflater, parent, false).also { it.root.applyPressFeedback() })
            SettingsItemType.SNACKBAR_PADDING -> ViewHolder.SnackbarPadding(ItemSnackbarPaddingBinding.inflate(layoutInflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val visible = visibleItems
        val item = visible[holder.adapterPosition]
        when(holder){
            is ViewHolder.Header -> setupHeader(holder.binding, item as SettingsItem.Header)
            is ViewHolder.SettingsSetting -> setupSetting(holder.binding, item as SettingsItem.Setting)
            is ViewHolder.SettingsAboutSetting -> setupTripleTapActionSetting(holder.binding, item as SettingsItem.AboutSetting)
            is ViewHolder.SettingsSwitchSetting -> setupSettingSwitch(holder.binding, item as SettingsItem.SwitchSetting)
            is ViewHolder.SnackbarPadding -> {}
        }
        if(holder !is ViewHolder.Header && holder !is ViewHolder.SnackbarPadding){
            applyGroupShape(holder.binding.root, visible, holder.adapterPosition)
        }
    }

    private fun SettingsItem?.isGroupBreak() = this == null || this is SettingsItem.Header || this is SettingsItem.SnackbarPadding

    // NOTE: Rows between two headers form one rounded section, with large outer corners and small inner corners, as in the Android 16 settings app.
    // NOTE: The outline clips the row ripple to the same shape.
    private fun applyGroupShape(root: View, visible: List<SettingsItem>, position: Int) {
        val isFirst = visible.getOrNull(position - 1).isGroupBreak()
        val isLast = visible.getOrNull(position + 1).isGroupBreak()
        val outer = resources.getDimension(R.dimen.settings_group_corner_outer)
        val inner = resources.getDimension(R.dimen.settings_group_corner_inner)
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(if(isFirst) outer else inner)
            .setTopRightCornerSize(if(isFirst) outer else inner)
            .setBottomLeftCornerSize(if(isLast) outer else inner)
            .setBottomRightCornerSize(if(isLast) outer else inner)
            .build()
        root.background = MaterialShapeDrawable(shape).apply {
            fillColor = ColorStateList.valueOf(ThemeColors.getContainerColor(root.context))
        }
        root.clipToOutline = true
        root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val horizontal = resources.getDimensionPixelSize(R.dimen.padding_16)
            marginStart = horizontal
            marginEnd = horizontal
            topMargin = if(isFirst) 0 else resources.getDimensionPixelSize(R.dimen.settings_group_gap)
            bottomMargin = 0
        }
    }

    private fun setupHeader(binding: ItemHeaderBinding, item: SettingsItem.Header) = with(binding) {
        itemHeadingTitle.text = item.title
        itemHeadingTitle.setTextColor(themeColors.getAccentColor(itemHeadingTitle.context))
    }

    private fun setupSetting(binding: ItemSettingBinding, item: SettingsItem.Setting) = with(binding) {
        itemSettingTitle.text = item.title
        if(item.content.isNullOrEmpty()){
            itemSettingContent.isVisible = false
        }else{
            itemSettingContent.isVisible = true
            itemSettingContent.text = item.content
        }
        if(item.icon != 0) {
            itemSettingIcon.setImageDrawable(SettingsIcons.load(itemSettingIcon.context, item.icon, settings.themeTintedIcons))
        }else{
            itemSettingIcon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        item.tapAction?.let { action ->
            root.setOnClickListener {
                root.isPressed = false
                root.clearFocus()
                root.postDelayed({
                    SafeRun.run(root.context, "settings tap: ${item.title}") { action.invoke() }
                }, 150)
            }
        }
        if(!item.centerIconVertically){
            binding.root.gravity = Gravity.NO_GRAVITY
        }
    }

    private fun setupTripleTapActionSetting(binding: ItemSettingAboutBinding, item: SettingsItem.AboutSetting) = with(binding) {
        val secondaryBackground = themeColors.getBackgroundColorSecondary(root.context)
        val backgroundTint = android.content.res.ColorStateList.valueOf(secondaryBackground)
        chipSocials.chipBackgroundColor = backgroundTint
        chipDonate.chipBackgroundColor = backgroundTint
        chipCredits.chipBackgroundColor = backgroundTint

        itemSettingTitle.text = item.title
        if(item.content.isNullOrEmpty()){
            itemSettingContent.isVisible = false
        }else{
            itemSettingContent.isVisible = true
            //A URLSpan starts the link without a catch, so each one is swapped for a span that opens through the safe link helper
            itemSettingContent.text = LinkSpans.safe(root.context, android.text.Html.fromHtml(item.content.toString(), android.text.Html.FROM_HTML_MODE_LEGACY))
            itemSettingContent.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
        if(item.icon != 0) {
            itemSettingIcon.setImageDrawable(SettingsIcons.load(itemSettingIcon.context, item.icon, settings.themeTintedIcons))
        }else{
            itemSettingIcon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        item.tripleTapAction?.let { action ->
            MultiTapDetector(root){ tapCount, lastTap ->
                if(lastTap){
                    if(tapCount >= 3){
                        action.invoke()
                    }
                }
            }
        }
        binding.chipDonate.setOnClickListener { chip ->
            chip.isPressed = false
            chip.clearFocus()
            chip.postDelayed({
                try {
                    chip.findNavController().navigate(R.id.action_global_donationBottomSheetFragment)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsAdapter", "Navigation to donation bottom sheet failed", e)
                }
            }, 150)
        }
        binding.chipCredits.setOnClickListener { chip ->
            chip.isPressed = false
            chip.clearFocus()
            chip.postDelayed({
                try {
                    chip.findNavController().navigate(R.id.action_global_creditsBottomSheetFragment)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsAdapter", "Navigation to credits bottom sheet failed", e)
                }
            }, 150)
        }
        binding.chipSocials.setOnClickListener { chip ->
            chip.isPressed = false
            chip.clearFocus()
            chip.postDelayed({
                try {
                    chip.findNavController().navigate(R.id.action_global_socialLinksBottomSheetFragment)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsAdapter", "Navigation to social links bottom sheet failed", e)
                }
            }, 150)
        }
    }

    private fun setupSettingSwitch(binding: ItemSettingSwitchBinding, item: SettingsItem.SwitchSetting) = with(binding) {
        itemSettingSwitchTitle.text = item.title
        if(item.content.isNullOrEmpty()){
            itemSettingSwitchContent.isVisible = false
        }else{
            itemSettingSwitchContent.isVisible = true
            itemSettingSwitchContent.text = item.content
        }
        if(item.icon != 0) {
            itemSettingSwitchIcon.setImageDrawable(SettingsIcons.load(itemSettingSwitchIcon.context, item.icon, settings.themeTintedIcons))
        }else{
            itemSettingSwitchIcon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        itemSettingSwitchSwitch.setOnCheckedChangeListener(null)
        itemSettingSwitchSwitch.isChecked = item.getValue?.invoke() ?: item.setting?.get() ?: false
        itemSettingSwitchSwitch.setOnCheckedChangeListener { button, isChecked ->
            if(item.tapAction != null){
                if(item.tapAction.invoke(isChecked)){
                    item.setValue?.invoke(isChecked) ?: item.setting?.set(isChecked)
                }else{
                    button.isChecked = !isChecked
                }
            }else {
                item.setValue?.invoke(isChecked) ?: item.setting?.set(isChecked)
            }
        }
        root.setOnClickListener {
            itemSettingSwitchSwitch.toggle()
        }
        if(!item.centerIconVertically){
            binding.root.gravity = Gravity.NO_GRAVITY
        }
    }

    fun notifySwitchSettings(){
        visibleItems.forEachIndexed { index, settingsItem ->
            if(settingsItem is SettingsItem.SwitchSetting) {
                notifyItemChanged(index)
            }
        }
    }

    sealed class ViewHolder(open val binding: ViewBinding): RecyclerView.ViewHolder(binding.root) {
        data class Header(override val binding: ItemHeaderBinding): ViewHolder(binding)
        data class SettingsSetting(override val binding: ItemSettingBinding): ViewHolder(binding)
        data class SettingsAboutSetting(override val binding: ItemSettingAboutBinding): ViewHolder(binding)
        data class SettingsSwitchSetting(override val binding: ItemSettingSwitchBinding): ViewHolder(binding)
        data class SnackbarPadding(override val binding: ItemSnackbarPaddingBinding): ViewHolder(binding)
    }

}