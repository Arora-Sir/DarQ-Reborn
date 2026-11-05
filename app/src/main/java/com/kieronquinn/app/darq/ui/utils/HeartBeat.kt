package com.kieronquinn.app.darq.ui.utils

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import com.kieronquinn.app.darq.R

// NOTE: A gentle double beat for the heart of the Support row, a pause and then again, about once every two seconds.
// NOTE: It runs only while the view is on screen, a recycled row is reset to its normal size, and it is off when the system animations are off.
object HeartBeat {

    private const val DURATION_MS = 2200L
    private const val PEAK = 1.14f
    private const val SECOND_PEAK = 1.09f

    fun attach(view: View, enabled: Boolean) {
        (view.getTag(R.id.heart_beat_tag) as? ObjectAnimator)?.cancel()
        view.setTag(R.id.heart_beat_tag, null)
        view.scaleX = 1f
        view.scaleY = 1f
        view.removeOnAttachStateChangeListener(listener)
        if (!enabled || !ValueAnimator.areAnimatorsEnabled()) return
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) start(view)
    }

    private val listener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = start(v)
        override fun onViewDetachedFromWindow(v: View) = stop(v)
    }

    private fun start(view: View) {
        if (view.getTag(R.id.heart_beat_tag) != null) return
        fun frames() = arrayOf(
            Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.05f, PEAK), Keyframe.ofFloat(0.11f, 1f),
            Keyframe.ofFloat(0.16f, SECOND_PEAK), Keyframe.ofFloat(0.22f, 1f), Keyframe.ofFloat(1f, 1f)
        )
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofKeyframe(View.SCALE_X, *frames()),
            PropertyValuesHolder.ofKeyframe(View.SCALE_Y, *frames())
        ).apply {
            duration = DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            // NOTE: The pivot is set on every frame from the real size, because the view can still have no size when the animation starts, and a zero pivot scales from the top left corner.
            addUpdateListener {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
            }
        }
        view.setTag(R.id.heart_beat_tag, animator)
        animator.start()
    }

    private fun stop(view: View) {
        (view.getTag(R.id.heart_beat_tag) as? ObjectAnimator)?.cancel()
        view.setTag(R.id.heart_beat_tag, null)
        view.scaleX = 1f
        view.scaleY = 1f
    }

}
