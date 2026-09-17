package com.airgesture.control

/**
 * Shared coordinate contract for the visual cursor inside the fixed full-screen
 * accessibility overlay.
 *
 * Keeping the visual placement on the same inclusive-pixel mapping used by
 * pointer actions prevents the overlay optimization from introducing a new
 * screen-coordinate system or edge discrepancy.
 */
object PointerOverlayGeometry {
    fun center(
        normalizedX: Float,
        normalizedY: Float,
        width: Int,
        height: Int
    ): PointerScreenPoint? =
        PointerScreenCoordinateMapper.actionPoint(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            left = 0,
            top = 0,
            width = width,
            height = height
        )
}
