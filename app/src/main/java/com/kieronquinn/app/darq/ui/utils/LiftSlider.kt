package com.kieronquinn.app.darq.ui.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

// NOTE: A pill with one dot per step, filled up to the chosen step, as in the surface lift of Super Artemis and DualClone. A touch or a drag picks the nearest dot.
// NOTE: It is focusable and answers the arrow keys and the accessibility scroll actions, so a keyboard or a screen reader reaches every step.
// NOTE: There is no thumb circle, because a bright disc on pure black looks harsh. The rounded end of the fill is the indicator.
class LiftSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var steps = 10
        private set
    var value = 0
        private set
    var onChange: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val density = resources.displayMetrics.density

    init {
        isFocusable = true
        isClickable = true
    }

    /** Sets the step without telling the listener, for the first draw. */
    fun setInitial(step: Int) {
        value = step.coerceIn(0, steps)
        invalidate()
    }

    fun setStep(step: Int) {
        val next = step.coerceIn(0, steps)
        if (next == value) return
        value = next
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
        onChange?.invoke(value)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(resolveSize(0, widthSpec), resolveSize((48 * density).toInt(), heightSpec))
    }

    private fun dotX(step: Int, pad: Float, width: Float) = pad + (width - 2 * pad) * step / steps

    private fun color(attr: Int, fallback: Int) = MaterialColors.getColor(this, attr, fallback)

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val pad = height / 2f
        val radius = height / 2f
        box.set(0f, 0f, width, height)
        paint.color = color(com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A2A2A.toInt())
        canvas.drawRoundRect(box, radius, radius, paint)
        box.set(0f, 0f, dotX(value, pad, width) + pad, height)
        paint.color = color(com.google.android.material.R.attr.colorPrimary, 0xFF9FB6FF.toInt())
        canvas.drawRoundRect(box, radius, radius, paint)
        val onPrimary = color(com.google.android.material.R.attr.colorOnPrimary, 0xFF102A6B.toInt())
        val onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFAAAAAA.toInt())
        for (step in 0..steps) {
            paint.color = if (step <= value) onPrimary else onSurfaceVariant
            canvas.drawCircle(dotX(step, pad, width), height / 2f, 3 * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
                val pad = height / 2f
                val span = maxOf(1f, width - 2 * pad)
                setStep(((event.x - pad) / span * steps).roundToInt())
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { setStep(value - 1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { setStep(value + 1); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, steps.toFloat(), value.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { setStep(value + 1); true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { setStep(value - 1); true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

}
