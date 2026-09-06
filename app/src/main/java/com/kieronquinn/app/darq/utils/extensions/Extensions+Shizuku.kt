package com.kieronquinn.app.darq.utils.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.kieronquinn.app.darq.model.shizuku.ShizukuConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.coroutines.resume

private const val ACTION_SHIZUKU_REQUEST_BINDER = "rikka.shizuku.intent.action.REQUEST_BINDER"

private val SHIZUKU_PERMISSIONS = listOf(
    ShizukuProvider.PERMISSION,
    "af.shizuku.plus.permission.API_V23",
    "af.shizuku.plus.api.permission.API_V2",
    "moe.shizuku.privileged.api.permission.API_V2"
)

private val SHIZUKU_PACKAGES = listOf(
    ShizukuConstants.SHIZUKU_PACKAGE_NAME,
    "af.shizuku.plus.api"
)

/**
 * Retrieves the [PermissionInfo] of the Shizuku provider if it exists on the system.
 */
private fun Context.getShizukuPermissionInfo(): PermissionInfo? {
    for (perm in SHIZUKU_PERMISSIONS) {
        val info = runCatching {
            packageManager.getPermissionInfo(perm, 0)
        }.getOrNull()
        if (info != null) return info
    }
    return null
}

/**
 * Returns the package name of the installed Shizuku provider, if available.
 * Tries direct lookup of known packages and permissions first, then falls back to a
 * dynamic REQUEST_BINDER receiver query for any other Shizuku-compatible manager.
 * The device-wide query is last-resort rather than first-choice so the common case
 * (stock Shizuku or Shizuku+ installed under their known package names) never needs it.
 */
fun Context.getShizukuPackageName(): String? {
    // 1. Direct package lookup for known packages
    for (pkg in SHIZUKU_PACKAGES) {
        val exists = runCatching {
            packageManager.getPackageInfo(pkg, 0)
        }.isSuccess
        if (exists) return pkg
    }

    // 2. Defined permission fallback
    val permPackage = getShizukuPermissionInfo()?.packageName
    if (permPackage != null) return permPackage

    // 3. Dynamic intent query: find any manager registering the Shizuku REQUEST_BINDER receiver.
    // Only reached when neither a known package nor a known permission was found.
    val receiverIntent = Intent(ACTION_SHIZUKU_REQUEST_BINDER)
    val receivers = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryBroadcastReceivers(
                receiverIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryBroadcastReceivers(receiverIntent, 0)
        }
    }.getOrNull()

    if (!receivers.isNullOrEmpty()) {
        // Prioritize a package that provides a launcher intent (user-facing manager app)
        for (resolveInfo in receivers) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                return pkg
            }
        }
        val firstPkg = receivers.firstNotNullOfOrNull { it.activityInfo?.packageName }
        if (firstPkg != null) return firstPkg
    }

    return null
}

/**
 * Resolves the launch intent for the installed Shizuku provider, or falls back to the default package name.
 */
fun Context.getShizukuLaunchIntent(): Intent? {
    val detectedPackage = getShizukuPackageName()
    val candidates = listOfNotNull(detectedPackage, "af.shizuku.plus.api", ShizukuConstants.SHIZUKU_PACKAGE_NAME).distinct()
    for (pkg in candidates) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) return intent
    }
    return packageManager.getLaunchIntentForPackage(ShizukuConstants.SHIZUKU_PACKAGE_NAME)
}

/**
 * Checks if Shizuku is available.
 * If the IPC binder is already active (e.g. Sui, active background daemon, or zygote injection),
 * this immediately returns true without requiring a manager package lookup.
 * Otherwise, verifies that a supported Shizuku manager package is installed.
 */
fun Context.isShizukuInstalled(): Boolean {
    if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return true
    return getShizukuPackageName() != null
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