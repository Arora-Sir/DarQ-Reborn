package com.kieronquinn.app.darq.ui.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.menu.ListMenuItemView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.darq.R
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively

class DarqActivity: MonetCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission request handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setUseLightStatusNav(false)
        lifecycleScope.launchWhenResumed {
            monet.awaitMonetReady()
            setContentView(R.layout.activity_darq)
            val lightStatusNav = resources.getBoolean(R.bool.lightStatusNav)
            setUseLightStatusNav(lightStatusNav)
        }

        askNotificationPermission()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     *  Applies Monet to the overflow menu's checkbox
     */
    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        if (applyBackgroundColorToMenu && name == ListMenuItemView::class.java.name) {
            //Toolbar dropdown menu list item
            (parent?.parent as? View)?.run {
                post {
                    applyMonetRecursively()
                }
            }
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    private fun setUseLightStatusNav(enabled: Boolean){
        WindowCompat.getInsetsController(window, window.decorView)?.run {
            isAppearanceLightNavigationBars = enabled
            isAppearanceLightStatusBars = enabled
        }
    }

}