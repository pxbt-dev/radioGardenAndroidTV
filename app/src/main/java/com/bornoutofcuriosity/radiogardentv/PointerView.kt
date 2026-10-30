package com.bornoutofcuriosity.radiogardentv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Draws the remote-controlled pointer over the WebView. Never takes focus or touches. */
class PointerView(context: Context) : View(context) {

    var px = 0f
        private set
    var py = 0f
        private set
    private var placed = false

    var spinMode = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.BLACK
    }

    init {
        isFocusable = false
        isClickable = false
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        placed = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!placed) {
            px = w / 2f
            py = h / 2f
        } else {
            px = px.coerceIn(0f, w - 1f)
            py = py.coerceIn(0f, h - 1f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (spinMode) {
            // In spin mode the drags happen at the centre, so mark the centre with a large ring.
            fill.color = SPIN_COLOR
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 4f * density
            canvas.drawCircle(width / 2f, height / 2f, 28f * density, fill)
        }
        fill.style = Paint.Style.FILL
        fill.color = if (spinMode) SPIN_COLOR else Color.WHITE
        val r = 10f * density
        canvas.drawCircle(px, py, r, fill)
        canvas.drawCircle(px, py, r, outline)
    }

    private companion object {
        val SPIN_COLOR = Color.rgb(0x2F, 0xD2, 0x66)
    }
}
