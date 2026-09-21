package com.example.vivomock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class JoystickView(context: Context, attrs: android.util.AttributeSet?) : View(context, attrs) {
    var onVectorChanged: ((x: Float, y: Float) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99333333.toInt() }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDDFFFFFF.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private var knobX = 0f
    private var knobY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f
        canvas.drawCircle(cx, cy, radius, basePaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx + knobX, cy + knobY, radius * 0.34f, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = hypot(dx, dy)
                val scale = if (dist > radius && dist > 0f) radius / dist else 1f
                knobX = dx * scale
                knobY = dy * scale
                onVectorChanged?.invoke(knobX / radius, knobY / radius)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = 0f; knobY = 0f
                onVectorChanged?.invoke(0f, 0f)
                invalidate()
            }
        }
        return true
    }
}
