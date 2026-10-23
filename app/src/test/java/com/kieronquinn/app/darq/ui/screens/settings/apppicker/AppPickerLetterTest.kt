package com.kieronquinn.app.darq.ui.screens.settings.apppicker

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPickerLetterTest {

    @Test
    fun namesStartingWithADigitOrSymbolGroupUnderHash() {
        assertEquals("#", SettingsAppPickerViewModelImpl.letterOf("1Password"))
        assertEquals("#", SettingsAppPickerViewModelImpl.letterOf("[Beta] Tool"))
        assertEquals("#", SettingsAppPickerViewModelImpl.letterOf(""))
    }

    @Test
    fun lettersAreUppercasedAndAccentsKept() {
        assertEquals("C", SettingsAppPickerViewModelImpl.letterOf("calendar"))
        assertEquals("Ж", SettingsAppPickerViewModelImpl.letterOf("жук"))
        assertEquals("É", SettingsAppPickerViewModelImpl.letterOf("école"))
    }

}
