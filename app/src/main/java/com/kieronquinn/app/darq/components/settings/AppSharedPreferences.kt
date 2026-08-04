package com.kieronquinn.app.darq.components.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.model.settings.IPCSetting
import com.kieronquinn.app.darq.model.xposed.XposedSelfHooks
import com.kieronquinn.app.darq.utils.extensions.ReadWriteProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

//Writes below are committed synchronously on the calling thread, deliberately.
//
//The settings UI starts services that immediately re-read these values: every Auto Dark toggle
//calls rescheduleAutoDark() from its tapAction, and SettingsAdapter invokes tapAction BEFORE it
//writes the new value. Committing on a background thread meant the service could win that race
//and act on the previous value. Turning the Auto Dark schedule off started the service while the
//schedule still read "on", so it applied the current period instead of restoring the system dark
//mode the user had before, leaving them stuck in whatever the schedule had last set.
//
//commit() rather than apply(): the prefs file is world readable for the Xposed module and
//notifyPrefChange() re-applies that permission. SharedPreferences persists by writing a new file
//and renaming it over the old one, so the permission has to be re-applied after the replacement
//has actually happened. apply() defers that write and would let the chmod race ahead of it.
@SuppressLint("ApplySharedPref")
class AppSharedPreferences(context: Context): DarqSharedPreferences() {

    override val sharedPreferences: SharedPreferences by lazy {
        val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
        try {
            context.getSharedPreferences(
                prefsFile,
                Context.MODE_WORLD_READABLE
            ).also {
                makeWorldReadable()
            }
        }catch (e: SecurityException){
            context.getSharedPreferences(
                prefsFile,
                Context.MODE_PRIVATE
            )
        }
    }

    private val _changed = MutableSharedFlow<IPCSetting>()
    override val changed: Flow<IPCSetting> = _changed.asSharedFlow()

    override fun shared(key: String, default: String) = ReadWriteProperty({
        sharedPreferences.getString(key, default) ?: default
    }, {
        sharedPreferences.edit().putString(key, it).commit()
        runInBackground { notifyPrefChange(key) }
    })

    override fun shared(key: String, default: Int) = ReadWriteProperty({
        sharedPreferences.getInt(key, default)
    }, {
        sharedPreferences.edit().putInt(key, it).commit()
        runInBackground { notifyPrefChange(key) }
    })

    override fun shared(key: String, default: Boolean) = ReadWriteProperty({
        sharedPreferences.getBoolean(key, default)
    }, {
        sharedPreferences.edit().putBoolean(key, it).commit()
        runInBackground { notifyPrefChange(key) }
    })

    //The following two, while their types are supported by SharedPreferences, are not by the SharedPrefsProvider

    override fun shared(key: String, default: Float) = ReadWriteProperty({
        sharedPreferences.getString(key, default.toString())?.toFloatOrNull() ?: default
    }, {
        sharedPreferences.edit().putString(key, it.toString()).commit()
        runInBackground { notifyPrefChange(key) }
    })

    override fun shared(key: String, default: Long) = ReadWriteProperty({
        sharedPreferences.getString(key, default.toString())?.toLongOrNull() ?: default
    }, {
        sharedPreferences.edit().putString(key, it.toString()).commit()
        runInBackground { notifyPrefChange(key) }
    })

    override fun shared(key: String, default: Double) = ReadWriteProperty({
        sharedPreferences.getString(key, default.toString())?.toDoubleOrNull() ?: default
    }, {
        sharedPreferences.edit().putString(key, it.toString()).commit()
        runInBackground { notifyPrefChange(key) }
    })

    override fun sharedJSONArray(key: String): ReadWriteProperty<Any?, Array<String>> = ReadWriteProperty({
        val rawJson = sharedPreferences.getString(key, "[]") ?: "[]"
        JSONArray(rawJson).toStringArray()
    }, {
        sharedPreferences.edit().putString(key, it.toJSONArray().toString()).commit()
        runInBackground { notifyPrefChange(key) }
    })

    inline fun <reified T : Enum<T>> sharedEnum(key: String, default: Enum<T>): ReadWriteProperty<Any?, T> {
        return object: ReadWriteProperty<Any?, T> {

            override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return java.lang.Enum.valueOf(T::class.java, sharedPreferences.getString(key, default.name))
            }

            override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                sharedPreferences.edit().putString(key, value.name).commit()
                GlobalScope.launch(Dispatchers.IO) { notifyPrefChange(key) }
            }

        }
    }

    fun notifyPrefChange(key: String){
        makeWorldReadable()
        val ipcSetting = getIPCSettingForKey(key) ?: return
        GlobalScope.launch(Dispatchers.IO) {
            _changed.emit(ipcSetting)
        }
    }

    private fun runInBackground(method: suspend () -> Unit){
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }

    private fun Array<String>.toJSONArray(): JSONArray {
        return JSONArray().apply {
            this@toJSONArray.forEach {
                put(it)
            }
        }
    }

    /**
     *  Make the redirected prefs file world readable ourselves - fixes a bug in Ed/lsposed
     *
     *  This requires the XSharedPreferences file path, which we get via a self hook. It does nothing
     *  when the Xposed module is not enabled.
     */
    private fun makeWorldReadable(){
        XposedSelfHooks.getXSharedPrefsPath().let {
            if(it.isNotEmpty()){
                File(it).setReadable(true, false)
            }
        }
    }

}