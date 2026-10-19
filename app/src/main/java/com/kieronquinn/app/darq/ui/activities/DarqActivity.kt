package com.kieronquinn.app.darq.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.ThemeManager
import org.koin.android.ext.android.inject

class DarqActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_ADVANCED_SETTINGS = "open_advanced_settings"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission request handled
    }

    private val settings by inject<DarqSharedPreferences>()
    private var appliedTheme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemeManager.apply(this, settings)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_darq)
        setUseLightStatusNav(resources.getBoolean(R.bool.lightStatusNav))

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        //An accent or pure black change made elsewhere (a restore, another task) only shows after the theme is applied again
        if(ThemeManager.signature(settings) != appliedTheme){
            recreate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setUseLightStatusNav(enabled: Boolean){
        WindowCompat.getInsetsController(window, window.decorView)?.run {
            isAppearanceLightNavigationBars = enabled
            isAppearanceLightStatusBars = enabled
        }
    }

}