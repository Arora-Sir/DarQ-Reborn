package com.kieronquinn.app.darq.components.settings

import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.kieronquinn.app.darq.model.settings.SettingsBackup
import java.io.File
import java.io.IOException

// NOTE: Keeps a copy of the user's settings in the app's files directory, outside the shared_prefs folder.
// NOTE: While LSPosed's "New XSharedPreferences" is active, LSPosed redirects DarQ's preferences into its own folder.
// NOTE: DarQ therefore sees a different preferences file when the module is toggled, when LSPosed 2.3.0 drops the feature, or when DarQ moves to libxposed.
// NOTE: Every settings change bumps a revision stored in both places, and a snapshot with a newer revision than the preferences file is restored on startup.
class SettingsSnapshot(filesDir: File) {

    companion object {
        private const val TAG = "SettingsSnapshot"
        private const val FILE_NAME = "settings_snapshot.json"
        const val KEY_REVISION = "settings_revision"

        private val gson = Gson()

        fun shouldRestore(preferencesRevision: Long, snapshotRevision: Long?): Boolean {
            return snapshotRevision != null && snapshotRevision > preferencesRevision
        }

        fun encode(snapshot: Stored): String = gson.toJson(snapshot)

        // Returns null for unreadable or incomplete files, so a damaged snapshot is ignored instead of restored.
        fun decode(json: String): Stored? {
            return try {
                val stored = gson.fromJson(json, Stored::class.java) ?: return null
                // Gson bypasses Kotlin null checks, so missing fields are caught here before a restore touches them.
                stored.settings.enabledApps.size
                stored
            } catch (e: Exception) {
                null
            }
        }
    }

    data class Stored(val revision: Long, val settings: SettingsBackup)

    private val file = AtomicFile(File(filesDir, FILE_NAME))

    fun read(): Stored? {
        if (!file.baseFile.exists()) return null
        return try {
            decode(String(file.readFully(), Charsets.UTF_8))
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read settings snapshot", e)
            null
        }
    }

    fun write(stored: Stored) {
        val output = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to open settings snapshot", e)
            return
        }
        try {
            output.write(encode(stored).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: IOException) {
            file.failWrite(output)
            Log.w(TAG, "Failed to write settings snapshot", e)
        }
    }

}
