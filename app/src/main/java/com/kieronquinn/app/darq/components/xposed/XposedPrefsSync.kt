package com.kieronquinn.app.darq.components.xposed

import android.content.SharedPreferences
import com.kieronquinn.app.darq.model.xposed.XposedPrefKeys

// NOTE: Copies the hook-relevant keys from the app's local preferences into the framework's remote preferences.
// NOTE: Local preferences stay the single source of truth, so every write here reads the local value instead of trusting a caller-supplied one.
object XposedPrefsSync {

    fun writeAll(source: SharedPreferences, editor: SharedPreferences.Editor) {
        XposedPrefKeys.ALL.forEach { writeKey(source, editor, it) }
    }

    // Returns false for keys the hooks never read, so callers can skip a pointless remote commit.
    fun writeKey(source: SharedPreferences, editor: SharedPreferences.Editor, key: String): Boolean {
        XposedPrefKeys.BOOLEAN_KEYS[key]?.let {
            editor.putBoolean(key, source.getBoolean(key, it))
            return true
        }
        XposedPrefKeys.INT_KEYS[key]?.let {
            editor.putInt(key, source.getInt(key, it))
            return true
        }
        XposedPrefKeys.STRING_KEYS[key]?.let {
            editor.putString(key, source.getString(key, it) ?: it)
            return true
        }
        return false
    }

}
