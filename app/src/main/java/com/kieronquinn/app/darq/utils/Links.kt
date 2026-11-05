package com.kieronquinn.app.darq.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.kieronquinn.app.darq.R

/**
 *  Opens a link without ever crashing.
 *  Every external link goes through here, so it starts in a new task, targets the default browser and survives a phone with no browser at all.
 */
fun Context.openLink(url: String) = LinkOpener.open(this, url)

// NOTE: The GitHub app claims every github.com link but cannot render issue forms, so the default browser is targeted directly.
// NOTE: Without a default browser the plain VIEW intent lets the user choose, and without any handler a short message is shown.
object LinkOpener {

    fun open(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        defaultBrowser(context)?.let { intent.setPackage(it) }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                context.startActivity(intent.setPackage(null))
                true
            } catch (e: Exception) {
                Toasts.show(context, R.string.link_no_browser, long = true)
                false
            }
        }
    }

    private fun defaultBrowser(context: Context): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = try {
            context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
        return browser?.takeIf { it != "android" }
    }

}

object Links {
    const val LINK_TWITTER = "https://x.com/MohitAr0rA"
    const val LINK_DONATE = "https://paypal.me/arorasir"
    const val LINK_SPONSORS = "https://github.com/sponsors/Arora-Sir"
    const val LINK_GITHUB = "https://github.com/Arora-Sir/DarQ-Reborn"
    const val LINK_GITHUB_PROFILE = "https://github.com/Arora-Sir"
    const val LINK_RELEASES = "https://github.com/Arora-Sir/DarQ-Reborn/releases"
    const val LINK_WEBSITE = "https://mohitarora.me"
    const val LINK_LINKEDIN = "https://www.linkedin.com/in/arora-sir"
    const val LINK_INSTAGRAM = "https://www.instagram.com/arora.sir/"
    const val LINK_REDDIT = "https://www.reddit.com/user/AroraSir/"
    const val LINK_UPSTREAM = "https://github.com/KieronQuinn/DarQ"
    const val LINK_UPSTREAM_AUTHOR = "https://github.com/KieronQuinn"
}
