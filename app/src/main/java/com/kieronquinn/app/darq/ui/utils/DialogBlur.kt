package com.kieronquinn.app.darq.ui.utils

import android.animation.ValueAnimator
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kieronquinn.app.darq.providers.blur.BlurProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// NOTE: Dialogs blur the screen behind them the same way the bottom sheets do, so every popup in the app has one consistent look.
// NOTE: The blur fades in with the dialog and is always cleared when the dialog goes away, whichever way it is dismissed.
private object DialogBlurHelper : KoinComponent {
    val blurProvider by inject<BlurProvider>()
    const val DURATION_MS = 220L
}

/**
 *  Builds and shows the dialog with the app window blurred behind it. Returns the dialog so callers can adjust its window.
 */
fun MaterialAlertDialogBuilder.showBlurred(activity: Activity, beforeShow: (AlertDialog) -> Unit = {}): AlertDialog {
    val dialog = create()
    val provider = DialogBlurHelper.blurProvider
    var animator: ValueAnimator? = null
    dialog.setOnShowListener {
        val dialogWindow = dialog.window ?: return@setOnShowListener
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DialogBlurHelper.DURATION_MS
            addUpdateListener { provider.applyDialogBlur(dialogWindow, activity.window, it.animatedValue as Float) }
            start()
        }
    }
    dialog.setOnDismissListener {
        animator?.cancel()
        dialog.window?.let { provider.applyDialogBlur(it, activity.window, 0f) }
    }
    beforeShow(dialog)
    dialog.show()
    return dialog
}
