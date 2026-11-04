package com.kieronquinn.app.darq.ui.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.kieronquinn.app.darq.components.theme.AppLanguage
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.diagnostics.DiagnosticsDialogs
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
        if(savedInstanceState == null){
            DiagnosticsDialogs.offerPendingReport(this)
        }

        askNotificationPermission()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    //Recreating the activity shows a black frame while the system builds a new window, so the language is swapped in place and every screen is rebuilt inside the same window
    fun applyLanguageInPlace() {
        val locale = AppLanguage.effectiveLocale(this)
        Locale.setDefault(locale)
        for(res in listOf(resources, applicationContext.resources)){
            val config = Configuration(res.configuration)
            config.setLocale(locale)
            res.updateConfiguration(config, res.displayMetrics)
        }
        //The old container hands its saved state to the new one, so every screen of the back stack is rebuilt in the same transaction and the user stays where they were
        val old = supportFragmentManager.findFragmentById(R.id.container_fragment)
        val container = com.kieronquinn.app.darq.ui.screens.container.ContainerFragment()
        old?.let { container.setInitialSavedState(supportFragmentManager.saveFragmentInstanceState(it)) }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container_fragment, container)
            .commitNowAllowingStateLoss()
    }

    override fun onStart() {
        super.onStart()
        AppLanguage.adoptSystemChoice(this)
    }

    override fun onStop() {
        super.onStop()
        //The OS locale follows only once the screen is gone, so no relayout shows
        AppLanguage.syncToSystem(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.enforce(this, "onConfigurationChanged")
        //A locale change made by the system while a language is chosen in the app is ignored, the wrapped context already shows it. With System default the screen has to reload.
        if(AppLanguage.tag(this).isEmpty()) applyLanguageInPlace()
    }

    override fun onResume() {
        super.onResume()
        AppLanguage.enforce(this, "onResume")
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