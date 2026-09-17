package com.airgesture.control

data class PointerScreenPoint(
    val x: Float,
    val y: Float
)

/**
 * Converts normalized pointer coordinates into valid inclusive display pixels.
 *
 * Android display bounds are [left, right) / [top, bottom), so multiplying
 * 1.0 by width/height produces the exclusive right/bottom edge. Pointer
 * actions must never dispatch there.
 */
object PointerScreenCoordinateMapper {
    fun actionPoint(
        normalizedX: Float,
        normalizedY: Float,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): PointerScreenPoint? {
        if (
            !normalizedX.isFinite() ||
            !normalizedY.isFinite() ||
            width <= 0 ||
            height <= 0
        ) {
            return null
        }

        val x =
            inclusivePixel(
                normalized = normalizedX,
                start = left,
                size = width
            )
        val y =
            inclusivePixel(
                normalized = normalizedY,
                start = top,
                size = height
            )

        return PointerScreenPoint(
            x = x,
            y = y
        )
    }

    fun inclusivePixel(
        normalized: Float,
        start: Int,
        size: Int
    ): Float {
        require(size > 0) {
            "Pixel span must be positive"
        }
        require(normalized.isFinite()) {
            "Normalized coordinate must be finite"
        }

        val bounded =
            normalized.coerceIn(0f, 1f)
        val startPixel =
            start.toFloat()
        val endPixel =
            startPixel +
                (size - 1)
                    .coerceAtLeast(0)
                    .toFloat()

        // Preserve the existing interior mapping exactly. Only the exclusive
        // right/bottom edge is clamped inward to the last valid pixel.
        return (
            startPixel +
                bounded * size.toFloat()
            ).coerceIn(
                startPixel,
                endPixel
            )
    }
}
