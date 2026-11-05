package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 *  A square area for one hue: saturation grows to the right and brightness grows upward. Has no dependency on the rest of the app.
 */
class SaturationBrightnessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val STEP = 0.04f
    }

    var hue: Float = 0f
        set(value) {
            field = value
            rebuildShader()
            invalidate()
        }

    var saturation: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var brightness: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onChanged: ((saturation: Float, brightness: Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val corner = 12f * density
    private val thumbRadius = 11f * density
    private val area = RectF()
    private val clip = Path()
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        //A 5 to 3 area keeps it tall enough to aim at without pushing the buttons off small phones
        val desired = (width * 0.6f).toInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        area.set(thumbRadius, thumbRadius, w - thumbRadius, h - thumbRadius)
        clip.reset()
        clip.addRoundRect(area, corner, corner, Path.Direction.CW)
        rebuildShader()
    }

    private fun rebuildShader() {
        if (area.isEmpty) return
        whitePaint.shader = LinearGradient(
            area.left, 0f, area.right, 0f, 0xFFFFFFFF.toInt(), ColorMath.hsvToColor(hue, 1f, 1f), Shader.TileMode.CLAMP
        )
        blackPaint.shader = LinearGradient(
            0f, area.top, 0f, area.bottom, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(area, whitePaint)
        canvas.drawRect(area, blackPaint)
        canvas.restore()
        val x = area.left + saturation * area.width()
        val y = area.top + (1f - brightness) * area.height()
        thumbFill.color = ColorMath.hsvToColor(hue, saturation, brightness)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbShadow)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbFill)
        canvas.drawCircle(x, y, thumbRadius - 2f * density, thumbStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> update(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                update(event.x, event.y)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun update(x: Float, y: Float) {
        change(
            ((x - area.left) / area.width()).coerceIn(0f, 1f),
            1f - ((y - area.top) / area.height()).coerceIn(0f, 1f)
        )
    }

    private fun change(newSaturation: Float, newBrightness: Float) {
        val s = newSaturation.coerceIn(0f, 1f)
        val b = newBrightness.coerceIn(0f, 1f)
        if (s == saturation && b == brightness) return
        saturation = s
        brightness = b
        onChanged?.invoke(s, b)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { change(saturation - STEP, brightness); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { change(saturation + STEP, brightness); true }
        KeyEvent.KEYCODE_DPAD_UP -> { change(saturation, brightness + STEP); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { change(saturation, brightness - STEP); true }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.text = "${(saturation * 100).toInt()}% / ${(brightness * 100).toInt()}%"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { change(saturation + STEP * 2f, brightness); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { change(saturation - STEP * 2f, brightness); true }
        else -> super.performAccessibilityAction(action, arguments)
    }

}
