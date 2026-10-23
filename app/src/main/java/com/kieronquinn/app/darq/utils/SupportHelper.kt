package com.kieronquinn.app.darq.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.kieronquinn.app.darq.R
import java.net.URLEncoder

// NOTE: Support lives only behind the Support development card, so there is never a nag screen or a popup.
// NOTE: The payment button tries the UPI apps first, and falls back to copying the UPI ID when none is installed.
object SupportHelper {

    const val UPI_ID = "mohit1998arora@yescred"
    const val UPI_NAME = "Mohit Arora"
    const val APP_NAME = "DarQ Reborn"

    /**
     *  The UPI pay link with payee name, currency and a note naming the app.
     */
    fun upiUri(appName: String = APP_NAME): String {
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return "upi://pay?pa=$UPI_ID&pn=${encode(UPI_NAME)}&cu=INR&tn=${encode("$appName Support")}"
    }

    fun createUpiIntent(appName: String = APP_NAME): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri(appName)))

    /**
     *  Opens the system chooser over the installed UPI apps, or copies the UPI ID when there are none.
     */
    fun handleUpiPayment(context: Context, appName: String = APP_NAME) {
        val intent = createUpiIntent(appName)
        val hasUpiApps = try {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        } catch (e: Exception) {
            false
        }
        if (hasUpiApps) {
            val chooser = Intent.createChooser(intent, context.getString(R.string.support_upi_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
                return
            } catch (e: Exception) {
                //Fall through to the copy path
            }
        }
        copyToClipboard(context, UPI_ID)
        Toasts.show(context, R.string.support_upi_none, long = true)
    }

    fun copyUpiId(context: Context) {
        copyToClipboard(context, UPI_ID)
        Toasts.show(context, context.getString(R.string.support_upi_copied, UPI_ID))
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", text))
    }

}
