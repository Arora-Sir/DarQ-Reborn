package com.kieronquinn.app.darq.ui.screens.settings.apppicker

import com.kieronquinn.app.darq.components.theme.ThemeColors
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.ItemAppBinding
import com.kieronquinn.app.darq.databinding.ItemHeaderBinding
import com.kieronquinn.app.darq.databinding.ItemSnackbarPaddingBinding
import com.kieronquinn.app.darq.model.settings.AppPickerItem
import com.kieronquinn.app.darq.model.settings.AppPickerItemType
import com.kieronquinn.app.darq.utils.AppIconRequestHandler
import com.squareup.picasso.Picasso

class SettingsAppPickerAdapter(
    context: Context,
    private val items: MutableList<AppPickerItem>,
    private val onPackageEnabledChanged: (AppPickerItem.App) -> Unit
): RecyclerView.Adapter<SettingsAppPickerAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private val layoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    private val picasso by lazy {
        Picasso.get()
    }

    private val themeColors = ThemeColors

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return item.itemType.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(AppPickerItemType.values().find { it.ordinal == viewType }!!){
            AppPickerItemType.APP -> ViewHolder.ItemAppViewHolder(ItemAppBinding.inflate(layoutInflater, parent, false).also { it.root.applyPressFeedback() })
            AppPickerItemType.SNACKBAR_PADDING -> ViewHolder.ItemSnackbarPaddingViewHolder(ItemSnackbarPaddingBinding.inflate(layoutInflater, parent, false))
            AppPickerItemType.HEADER -> ViewHolder.ItemHeaderViewHolder(ItemHeaderBinding.inflate(layoutInflater, parent, false))
            AppPickerItemType.LETTER -> ViewHolder.ItemHeaderViewHolder(ItemHeaderBinding.inflate(layoutInflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[holder.adapterPosition]
        when(holder){
            is ViewHolder.ItemAppViewHolder -> setupApp(holder.binding, item as AppPickerItem.App)
            is ViewHolder.ItemHeaderViewHolder -> setupHeader(holder.binding, item)
            //The snackbar padding row has nothing to bind
            else -> {}
        }
    }

    private fun setupApp(binding: ItemAppBinding, item: AppPickerItem.App){
        with(binding){
            title.text = item.label
            val uri = Uri.parse("${AppIconRequestHandler.SCHEME_PNAME}:${item.packageName}")
            //Decoded at row size, so the memory cache holds many more icons than at full launcher density
            picasso.load(uri).resizeDimen(R.dimen.item_app_height, R.dimen.item_app_height).centerInside().into(icon)
            appSwitch.setOnCheckedChangeListener(null)
            appSwitch.isChecked = item.enabled
            appSwitch.setOnCheckedChangeListener { _, isChecked ->
                item.enabled = isChecked
                onPackageEnabledChanged.invoke(item)
            }
            root.setOnClickListener {
                appSwitch.toggle()
            }
        }
    }

    private fun setupHeader(binding: ItemHeaderBinding, item: AppPickerItem) {
        with(binding) {
            when (item) {
                is AppPickerItem.Header -> itemHeadingTitle.setText(item.titleResId)
                is AppPickerItem.Letter -> itemHeadingTitle.text = item.letter
                else -> {}
            }
            itemHeadingTitle.setTextColor(themeColors.getAccentColor(itemHeadingTitle.context))
        }
    }

    // NOTE: A diff keeps untouched rows in place and animates a toggled app into its new section instead of redrawing the whole list.
    fun setItems(newItems: List<AppPickerItem>){
        val old = ArrayList(items)
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = idOf(old[oldPosition]) == idOf(newItems[newPosition])
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == newItems[newPosition]
        })
        items.clear()
        items.addAll(newItems)
        result.dispatchUpdatesTo(this)
    }

    private fun idOf(item: AppPickerItem): Long = when(item){
        is AppPickerItem.App -> item.packageName.hashCode().toLong()
        is AppPickerItem.SnackbarPadding -> "SnackbarPadding".hashCode().toLong()
        is AppPickerItem.Header -> item.titleResId.toLong()
        is AppPickerItem.Letter -> "Letter${item.letter}".hashCode().toLong()
    }

    override fun getItemId(position: Int): Long {
        return idOf(items[position])
    }

    fun addSnackbarPadding(){
        if(!items.contains(AppPickerItem.SnackbarPadding)){
            items.add(AppPickerItem.SnackbarPadding)
            notifyItemInserted(items.size - 1)
        }
    }

    fun removeSnackbarPadding(){
        if(items.contains(AppPickerItem.SnackbarPadding)){
            items.remove(AppPickerItem.SnackbarPadding)
            notifyItemRemoved(items.size)
        }
    }

    sealed class ViewHolder(open val view: View): RecyclerView.ViewHolder(view) {
        data class ItemAppViewHolder(val binding: ItemAppBinding): ViewHolder(binding.root)
        data class ItemSnackbarPaddingViewHolder(val binding: ItemSnackbarPaddingBinding): ViewHolder(binding.root)
        data class ItemHeaderViewHolder(val binding: ItemHeaderBinding): ViewHolder(binding.root)
    }

}