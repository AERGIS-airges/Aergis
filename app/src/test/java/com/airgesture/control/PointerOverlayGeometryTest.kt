package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerOverlayGeometryTest {
    @Test
    fun visualCursorUsesSameNormalizedScreenMappingAsPointerActions() {
        val visual =
            PointerOverlayGeometry.center(
                normalizedX = .42f,
                normalizedY = .73f,
                width = 1080,
                height = 2340
            )!!
        val action =
            PointerScreenCoordinateMapper.actionPoint(
                normalizedX = .42f,
                normalizedY = .73f,
                left = 0,
                top = 0,
                width = 1080,
                height = 2340
            )!!

        assertEquals(action.x, visual.x, .0001f)
        assertEquals(action.y, visual.y, .0001f)
    }

    @Test
    fun edgesStayInsideOverlaySurface() {
        val topLeft =
            PointerOverlayGeometry.center(
                normalizedX = 0f,
                normalizedY = 0f,
                width = 1080,
                height = 2340
            )!!
        val bottomRight =
            PointerOverlayGeometry.center(
                normalizedX = 1f,
                normalizedY = 1f,
                width = 1080,
                height = 2340
            )!!

        assertEquals(0f, topLeft.x, .0001f)
        assertEquals(0f, topLeft.y, .0001f)
        assertTrue(bottomRight.x <= 1079f)
        assertTrue(bottomRight.y <= 2339f)
    }

    @Test
    fun invalidOverlayGeometryIsRejected() {
        assertNull(
            PointerOverlayGeometry.center(
                normalizedX = Float.NaN,
                normalizedY = .5f,
                width = 1080,
                height = 2340
            )
        )
        assertNull(
            PointerOverlayGeometry.center(
                normalizedX = .5f,
                normalizedY = .5f,
                width = 0,
                height = 2340
            )
        )
    }
}
