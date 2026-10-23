package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 *  A horizontal strip showing every hue, with a draggable thumb. Has no dependency on the rest of the app, so it copies to other apps unchanged.
 */
class HueStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val STEP_DEGREES = 5f
    }

    var hue: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            invalidate()
        }

    var onHueChanged: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackHeight = 16f * density
    private val thumbRadius = 14f * density
    private val trackRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val thumbShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        color = 0x40000000
    }

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (thumbRadius * 2f + 8f * density).toInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val left = thumbRadius
        val right = w - thumbRadius
        val top = (h - trackHeight) / 2f
        trackRect.set(left, top, right, top + trackHeight)
        val colors = IntArray(7) { ColorMath.hsvToColor(it * 60f, 1f, 1f) }
        trackPaint.shader = LinearGradient(left, 0f, right, 0f, colors, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)
        val x = trackRect.left + hue / 360f * trackRect.width()
        val y = height / 2f
        thumbFill.color = ColorMath.hsvToColor(hue, 1f, 1f)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbShadow)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbFill)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x)
            }
            MotionEvent.ACTION_MOVE -> update(event.x)
            MotionEvent.ACTION_UP -> {
                update(event.x)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun update(x: Float) {
        val fraction = ((x - trackRect.left) / trackRect.width()).coerceIn(0f, 1f)
        change(fraction * 360f)
    }

    private fun change(value: Float) {
        val next = value.coerceIn(0f, 360f)
        if (next == hue) return
        hue = next
        onHueChanged?.invoke(next)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { change(hue - STEP_DEGREES); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { change(hue + STEP_DEGREES); true }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.text = "${hue.toInt()}"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { change(hue + STEP_DEGREES * 3f); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { change(hue - STEP_DEGREES * 3f); true }
        else -> super.performAccessibilityAction(action, arguments)
    }

}
