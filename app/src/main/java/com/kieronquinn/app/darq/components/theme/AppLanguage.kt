package com.kieronquinn.app.darq.components.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

// NOTE: Changing the OS app locale while DarQ is visible makes the system relayout the window, which shows as a black flash.
// NOTE: The choice is stored here and applied through a wrapped context, so the screen redraws in the new language without that relayout.
// NOTE: The OS app locale is synced only after the activity stops, which keeps the system per-app language screen and the notification strings in step.
object AppLanguage {

    private const val PREFS = "darq_language"
    private const val KEY_TAG = "tag"

    val TAGS = listOf("en", "hi", "uk", "zh-CN")

    fun tag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "") ?: ""

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).apply()
    }

    fun wrap(base: Context): Context {
        val tag = tag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    //System default has to be the phone language, which on Android 13 and newer is not what Resources.getSystem() reports once an app locale was synced
    fun effectiveLocale(context: Context): Locale {
        val tag = tag(context)
        if (tag.isNotEmpty()) return Locale.forLanguageTag(tag)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val system = context.getSystemService(android.app.LocaleManager::class.java).systemLocales
            if (!system.isEmpty) return system[0]
        }
        return android.content.res.Resources.getSystem().configuration.locales[0]
    }

    //The OS delivers a configuration change when the app locale is synced, and on some phones that change can leave the activity resources on the phone language while the chosen language is stored. The check puts the chosen language back.
    fun enforce(activity: android.app.Activity, reason: String) {
        val tag = tag(activity)
        if (tag.isEmpty()) return
        val wanted = Locale.forLanguageTag(tag)
        val current = activity.resources.configuration.locales[0]
        if (current.language == wanted.language) return
        android.util.Log.w("DarqLang", "$reason: resources were ${current.toLanguageTag()}, chosen ${wanted.toLanguageTag()}, corrected")
        Locale.setDefault(wanted)
        for (res in listOf(activity.resources, activity.applicationContext.resources)) {
            val config = Configuration(res.configuration)
            config.setLocale(wanted)
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    private const val KEY_SYNCED = "synced"

    //A language picked in the Android 13 system screen is adopted. The OS value only counts as a change when it differs from what DarQ last handed over, so a choice that was not synced yet (the app was killed first) is kept.
    fun adoptSystemChoice(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val system = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val synced = prefs.getString(KEY_SYNCED, null) ?: system
        //An empty OS value is never adopted, because the OS can still report the old value for a moment after DarQ handed a new one over, and that would wipe the choice
        if (system.isNotEmpty() && system != synced) prefs.edit().putString(KEY_TAG, system).putString(KEY_SYNCED, system).apply()
    }

    fun syncToSystem(context: Context) {
        val wanted = tag(context)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(wanted))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SYNCED, wanted).apply()
    }

}
