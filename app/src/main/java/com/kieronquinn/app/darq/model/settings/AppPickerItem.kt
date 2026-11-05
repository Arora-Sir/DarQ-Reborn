package com.kieronquinn.app.darq.model.settings

sealed class AppPickerItem(open val itemType: AppPickerItemType) {
    data class App(val packageName: String, val label: CharSequence, var enabled: Boolean): AppPickerItem(AppPickerItemType.APP){
        fun toIPCSetting(): IPCSetting {
            return IPCSetting(packageChange = IPCPackageChange(packageName, enabled))
        }
    }

    data class Header(val titleResId: Int): AppPickerItem(AppPickerItemType.HEADER)

    //A letter header inside the full list, "#" collects every name that does not start with a letter
    data class Letter(val letter: String): AppPickerItem(AppPickerItemType.LETTER)

    object SnackbarPadding: AppPickerItem(AppPickerItemType.SNACKBAR_PADDING)
}

enum class AppPickerItemType {
    APP, SNACKBAR_PADDING, HEADER, LETTER
}
