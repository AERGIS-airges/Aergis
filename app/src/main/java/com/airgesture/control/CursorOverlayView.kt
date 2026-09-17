package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Full-screen, non-touchable cursor surface.
 *
 * AirAccessibilityService keeps this overlay window stationary. Pointer motion
 * changes only this View's internal draw position, avoiding a WindowManager
 * relayout/IPC for every MediaPipe result. Redraws are synchronized to the next
 * display animation step via postInvalidateOnAnimation().
 */
class CursorOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = Color.rgb(139, 232, 255)
    }

    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(215, 8, 15, 24)
    }

    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cursorRadius = 15f * density

    private var normalizedX = 0.5f
    private var normalizedY = 0.5f
    private var positionValid = false
    private var pressed = false
    private var held = false

    init {
        // Prevent a one-frame center flash before the first real fingertip
        // coordinate arrives after the overlay is attached.
        visibility = INVISIBLE
    }

    /**
     * Sets the already calibrated/smoothed pointer coordinate. This method does
     * no additional tracking, palm anchoring, prediction or coordinate gain.
     */
    fun setCursorPosition(
        x: Float,
        y: Float
    ): Boolean {
        if (!x.isFinite() || !y.isFinite()) {
            return false
        }

        val nextX = x.coerceIn(0f, 1f)
        val nextY = y.coerceIn(0f, 1f)
        if (
            positionValid &&
            normalizedX == nextX &&
            normalizedY == nextY
        ) {
            return true
        }

        normalizedX = nextX
        normalizedY = nextY
        positionValid = true
        postInvalidateOnAnimation()
        return true
    }

    fun clearCursorPosition() {
        positionValid = false
        visibility = INVISIBLE
    }

    fun setPressState(
        pressed: Boolean,
        held: Boolean
    ) {
        if (
            this.pressed == pressed &&
            this.held == held
        ) {
            return
        }
        this.pressed = pressed
        this.held = held
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!positionValid) return

        val point =
            PointerOverlayGeometry.center(
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                width = width,
                height = height
            ) ?: return

        val cx = point.x
        val cy = point.y
        val radius = cursorRadius

        canvas.drawCircle(cx, cy, radius, inner)

        outer.color =
            when {
                held -> Color.rgb(255, 199, 102)
                pressed -> Color.rgb(191, 167, 255)
                else -> Color.rgb(139, 232, 255)
            }
        canvas.drawCircle(cx, cy, radius, outer)

        val dotRadius =
            when {
                held -> radius * 0.36f
                pressed -> radius * 0.30f
                else -> radius * 0.18f
            }
        canvas.drawCircle(cx, cy, dotRadius, center)
    }
}
