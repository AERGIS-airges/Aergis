package com.airgesture.control

import kotlin.math.hypot

data class PointerTouchDragTarget(
    val x: Float,
    val y: Float,
    val dragging: Boolean
)

/**
 * Touch-slop gate for the virtual touchscreen finger.
 *
 * Before the pointer moves beyond [dragStartDistancePx], injected touch
 * coordinates remain anchored to the original down point. This prevents
 * landmark shimmer from turning an intended tap/long-press into a scroll.
 *
 * After the threshold is crossed, the gate stays in drag mode for the rest of
 * the touch contact and follows the current pointer coordinate directly.
 */
class PointerTouchDragGate(
    private val dragStartDistancePx: Float
) {
    private var startX: Float? = null
    private var startY: Float? = null
    private var dragging = false

    fun start(
        x: Float,
        y: Float
    ): PointerTouchDragTarget {
        val safeX = x.takeIf { it.isFinite() } ?: 0f
        val safeY = y.takeIf { it.isFinite() } ?: 0f
        startX = safeX
        startY = safeY
        dragging = false
        return PointerTouchDragTarget(
            x = safeX,
            y = safeY,
            dragging = false
        )
    }

    fun update(
        x: Float,
        y: Float
    ): PointerTouchDragTarget? {
        val downX = startX ?: return null
        val downY = startY ?: return null

        val safeX = x.takeIf { it.isFinite() } ?: downX
        val safeY = y.takeIf { it.isFinite() } ?: downY

        if (!dragging) {
            val distance =
                hypot(
                    safeX - downX,
                    safeY - downY
                )
            if (
                distance >=
                dragStartDistancePx.coerceAtLeast(1f)
            ) {
                dragging = true
            }
        }

        return if (dragging) {
            PointerTouchDragTarget(
                x = safeX,
                y = safeY,
                dragging = true
            )
        } else {
            PointerTouchDragTarget(
                x = downX,
                y = downY,
                dragging = false
            )
        }
    }

    fun isDragging(): Boolean = dragging

    fun reset() {
        startX = null
        startY = null
        dragging = false
    }
}
