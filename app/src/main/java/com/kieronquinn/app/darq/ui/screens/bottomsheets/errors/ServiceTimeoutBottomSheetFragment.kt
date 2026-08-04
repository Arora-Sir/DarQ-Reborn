package com.kieronquinn.app.darq.ui.screens.bottomsheets.errors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kieronquinn.app.darq.BuildConfig
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetDialogFragment
import com.kieronquinn.app.darq.utils.extensions.getShizukuPackageName
import rikka.shizuku.Shizuku

class ServiceTimeoutBottomSheetFragment: BaseBottomSheetDialogFragment() {

    override val title by lazy {
        getString(R.string.bottom_sheet_service_timeout_title)
    }

    override val content by lazy {
        val baseContent = getString(R.string.bottom_sheet_service_timeout_content)
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val isXiaomi = manufacturer.equals("Xiaomi", ignoreCase = true) || brand.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) || brand.equals("Redmi", ignoreCase = true) ||
                manufacturer.equals("POCO", ignoreCase = true) || brand.equals("POCO", ignoreCase = true)
        val isOppoLike = manufacturer.equals("OPPO", ignoreCase = true) || brand.equals("OPPO", ignoreCase = true) ||
                manufacturer.equals("OnePlus", ignoreCase = true) || brand.equals("OnePlus", ignoreCase = true) ||
                manufacturer.equals("Realme", ignoreCase = true) || brand.equals("Realme", ignoreCase = true)

        when {
            isXiaomi -> {
                "$baseContent\n\nFor Xiaomi/HyperOS, ensure you have enabled \"USB Debugging (Security settings)\" in Developer Options, and set Shizuku's Battery Saver to \"No restrictions\"."
            }
            isOppoLike -> {
                "$baseContent\n\nFor OPPO/OnePlus/Realme, ensure you have enabled \"Disable permission monitoring\" in Developer Options."
            }
            else -> {
                baseContent
            }
        }
    }

    override val positiveText by lazy {
        getString(R.string.bottom_sheet_service_timeout_positive)
    }

    override val neutralText by lazy {
        getString(R.string.bottom_sheet_service_timeout_neutral)
    }

    override val cancelable = false

    override fun onPositiveClicked(dialog: BottomSheetDialog) {
        super.onPositiveClicked(dialog)
        requireActivity().finish()
    }

    /**
     *  Deliberately does not call super, which would dismiss the sheet. This sheet is not
     *  cancelable and its positive button finishes the Activity, so dismissing here would leave
     *  the user with no way forward. Same pattern as [ShizukuNotInstalledBottomSheetFragment].
     */
    override fun onNeutralClicked(dialog: BottomSheetDialog) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DarQ Diagnostics", buildDiagnostics()))
        //Android 13+ shows its own clipboard confirmation, so ours would be a duplicate
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
            Toast.makeText(
                requireContext(),
                R.string.bottom_sheet_service_timeout_copied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     *  Builds the clipboard payload from an explicit allowlist of fields.
     *
     *  This text is intended to be pasted into public issue reports, so it must never contain
     *  anything personal - no enabled app list, no location settings, no other preference values.
     *  Build it field by field rather than serialising any settings object.
     *
     *  The Shizuku package line is the most valuable one here: users run a variety of Shizuku
     *  forks, and knowing which build is installed is often what identifies the problem.
     */
    private fun buildDiagnostics(): String {
        val context = requireContext()
        val shizukuPackage = context.getShizukuPackageName()
        val shizukuVersion = shizukuPackage?.let { packageName ->
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull()
        }
        //Shizuku.checkSelfPermission() throws when the binder hasn't been received, which is
        //exactly the state this sheet exists to report, so both calls are guarded.
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = runCatching {
            if(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "granted"
            else "denied"
        }.getOrDefault("unknown")
        return buildString {
            appendLine("DarQ Reborn ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine("Shizuku package: ${shizukuPackage ?: "not installed"}")
            appendLine("Shizuku version: ${shizukuVersion ?: "unknown"}")
            appendLine("Shizuku binder: $binderAlive")
            appendLine("Shizuku permission: $permission")
            append("Failure: TIMEOUT")
        }
    }

}