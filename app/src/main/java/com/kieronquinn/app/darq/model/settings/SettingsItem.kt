package com.kieronquinn.app.darq.model.settings

import androidx.annotation.DrawableRes
import kotlin.reflect.KMutableProperty0

sealed class SettingsItem constructor(open val itemType: SettingsItemType, open var centerIconVertically: Boolean = true, open var visible: () -> Boolean = {true}) {
    data class Header(val title: String, override var visible: () -> Boolean = {true}): SettingsItem(SettingsItemType.HEADER)
    data class Setting(
        @DrawableRes val icon: Int,
        val title: String,
        var content: CharSequence? = null,
        override var centerIconVertically: Boolean = true,
        override var visible: () -> Boolean = {true},
        val tapAction: (() -> Unit)? = null
    ): SettingsItem(SettingsItemType.SETTING)
    data class SwitchSetting(
        @DrawableRes val icon: Int,
        val title: String,
        var content: CharSequence? = null,
        val setting: KMutableProperty0<Boolean>? = null,
        val getValue: (() -> Boolean)? = null,
        val setValue: ((Boolean) -> Unit)? = null,
        override var centerIconVertically: Boolean = true,
        override var visible: () -> Boolean = {true},
        val tapAction: ((Boolean) -> Boolean)? = null,
        //Read on every bind, so a subtitle that depends on the switch state stays current without rebuilding the list
        val contentProvider: (() -> CharSequence?)? = null,
        //A tap on the row, not on the switch. Returning true means it was handled, false toggles the switch as usual
        val rowTapAction: (() -> Boolean)? = null
    ): SettingsItem(SettingsItemType.SWITCH_SETTING)
    data class AboutSetting(
        @DrawableRes val icon: Int,
        val title: String,
        var content: CharSequence? = null,
        override var visible: () -> Boolean = {true},
        val tripleTapAction: (() -> Unit)? = null
    ): SettingsItem(SettingsItemType.ABOUT_SETTING)
    object SnackbarPadding: SettingsItem(SettingsItemType.SNACKBAR_PADDING)
}

enum class SettingsItemType {
    HEADER, SETTING, ABOUT_SETTING, SWITCH_SETTING, SNACKBAR_PADDING
}
