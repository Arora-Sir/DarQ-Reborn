package com.kieronquinn.app.darq.utils.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PermissionInfo
import com.kieronquinn.app.darq.model.shizuku.ShizukuConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.coroutines.resume

/**
 * Retrieves the [PermissionInfo] of the Shizuku provider if it exists on the system.
 */
private fun Context.getShizukuPermissionInfo(): PermissionInfo? {
    return runCatching {
        packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
    }.getOrNull() ?: runCatching {
        packageManager.getPermissionInfo("af.shizuku.plus.api.permission.API_V2", 0)
    }.getOrNull()
}

/**
 * Returns the package name of the installed Shizuku provider, if available.
 */
fun Context.getShizukuPackageName(): String? {
    return getShizukuPermissionInfo()?.packageName
}

/**
 * Resolves the launch intent for the installed Shizuku provider, or falls back to the default package name.
 */
fun Context.getShizukuLaunchIntent(): Intent? {
    val packageName = getShizukuPackageName() ?: ShizukuConstants.SHIZUKU_PACKAGE_NAME
    return packageManager.getLaunchIntentForPackage(packageName)
}

fun Context.isShizukuInstalled(): Boolean {
    return getShizukuPermissionInfo() != null
}

suspend fun Shizuku_awaitBinderReceived() = suspendCancellableCoroutine<Unit> {
    val receiver = object: Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            Shizuku.removeBinderReceivedListener(this)
            it.resume(Unit)
        }
    }
    Shizuku.addBinderReceivedListener(receiver)
    it.invokeOnCancellation {
        Shizuku.removeBinderReceivedListener(receiver)
    }
}