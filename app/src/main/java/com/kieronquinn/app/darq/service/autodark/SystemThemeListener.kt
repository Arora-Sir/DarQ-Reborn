package com.kieronquinn.app.darq.service.autodark

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.kieronquinn.app.darq.utils.extensions.isDarkTheme

/**
 *  Watches Configuration.uiMode for changes to the *effectively rendered* night mode and
 *  invokes [onDarkStateChanged] only when it actually flips relative to the last known value.
 *
 *  Deliberately reads Configuration.uiMode rather than Settings.Secure.ui_night_mode: the latter
 *  reports "auto"/"custom_schedule" instead of a plain yes/no when the system's own Dark Theme
 *  is set to "Sunset to sunrise" or "Bedtime", which is exactly the ambiguity documented in the
 *  Auto Dark restore caveat (see README.md/faq.md). Configuration.uiMode always resolves to a
 *  concrete rendered value regardless of which mechanism produced it.
 */
class SystemThemeListener(
    private val appContext: Context,
    private val onDarkStateChanged: (isDark: Boolean) -> Unit
) : ComponentCallbacks2 {

    var lastKnownDark: Boolean = appContext.isDarkTheme
        private set

    override fun onConfigurationChanged(newConfig: Configuration) {
        val newDark = newConfig.isDarkTheme
        if (newDark != lastKnownDark) {
            lastKnownDark = newDark
            onDarkStateChanged(newDark)
        }
    }

    override fun onLowMemory() {}

    override fun onTrimMemory(level: Int) {}

}
