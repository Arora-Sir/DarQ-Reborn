package com.kieronquinn.app.darq.utils.extensions

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

// NOTE: A tap shrinks the view slightly and a release springs it back, which makes rows and swatches feel physical.
// NOTE: The listener never consumes the touch, so clicks, long presses, ripples and scrolling behave exactly as before.
private const val PRESSED_SCALE = 0.97f
private const val MAX_SHRINK_DP = 10f
private const val GENTLEST_SCALE = 0.995f

// NOTE: A percentage shrink grows with the view, so a 3 percent shrink moved a wide tablet row by about 58 pixels.
// NOTE: The shrink is capped at 10 dp in total per axis, so small tiles keep the full 3 percent and wide rows barely move.
internal fun pressedScaleFor(widthPx: Float, density: Float, base: Float = PRESSED_SCALE): Float {
    val capped = 1f - MAX_SHRINK_DP * density / widthPx.coerceAtLeast(1f)
    return maxOf(base, capped).coerceAtMost(GENTLEST_SCALE)
}

@SuppressLint("ClickableViewAccessibility")
fun View.applyPressFeedback(pressedScale: Float = PRESSED_SCALE) {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.springScale(pressedScaleFor(view.width.toFloat(), view.resources.displayMetrics.density, pressedScale), SpringForce.DAMPING_RATIO_NO_BOUNCY, SpringForce.STIFFNESS_MEDIUM)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.springScale(1f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_LOW)
        }
        false
    }
}

private fun View.springScale(target: Float, damping: Float, stiffness: Float) {
    for (property in listOf(DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y)) {
        SpringAnimation(this, property, target).apply {
            spring = SpringForce(target).setDampingRatio(damping).setStiffness(stiffness)
        }.start()
    }
}
