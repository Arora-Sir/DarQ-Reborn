package io.github.libxposed.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log

// NOTE: The libxposed service library declares an exported XposedProvider with no permission that forwards any binder it receives.
// NOTE: Any installed app could therefore impersonate the Xposed framework, switch DarQ into Xposed mode and receive its settings.
// NOTE: This provider keeps the same authority but only accepts the binder from root or the system UID, which is where LSPosed's daemon calls from.
// NOTE: It lives in the library package because XposedServiceHelper.onBinderReceived and RemotePreferences.shouldNotifyCleared are package-private.
class DarqSecureXposedProvider : ContentProvider() {

    companion object {
        private const val TAG = "DarqXposedProvider"
        private const val METHOD_SEND_BINDER = "SendBinder"
        private const val EXTRA_BINDER = "binder"
        private const val SERVICE_DESCRIPTOR = "io.github.libxposed.service.IXposedService"
        private const val ROOT_APP_ID = 0
        private const val PER_USER_RANGE = 100000

        fun isTrustedCaller(uid: Int): Boolean {
            val appId = uid % PER_USER_RANGE
            return appId == ROOT_APP_ID || appId == Process.SYSTEM_UID
        }
    }

    override fun onCreate(): Boolean {
        // Mirrors the library provider so RemotePreferences reports a cleared group to listeners on Android 11 and later.
        val targetSdk = context?.applicationInfo?.targetSdkVersion ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && targetSdk >= Build.VERSION_CODES.R) {
            RemotePreferences.shouldNotifyCleared = true
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_SEND_BINDER || extras == null) return null
        val callingUid = Binder.getCallingUid()
        if (!isTrustedCaller(callingUid)) {
            Log.w(TAG, "Rejected framework binder from untrusted uid $callingUid")
            return null
        }
        val binder = extras.getBinder(EXTRA_BINDER) ?: return null
        if (!binder.hasServiceDescriptor()) {
            Log.w(TAG, "Rejected framework binder with an unexpected interface descriptor")
            return null
        }
        XposedServiceHelper.onBinderReceived(binder)
        return Bundle()
    }

    private fun IBinder.hasServiceDescriptor(): Boolean {
        return try {
            interfaceDescriptor == SERVICE_DESCRIPTOR
        } catch (e: RemoteException) {
            false
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

}
