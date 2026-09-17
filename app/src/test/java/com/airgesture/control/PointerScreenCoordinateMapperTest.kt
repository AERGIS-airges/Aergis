package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointerScreenCoordinateMapperTest {
    @Test
    fun normalizedEdgesMapInsideInclusivePixelBounds() {
        val start =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = 0f,
                    normalizedY = 0f,
                    left = 10,
                    top = 20,
                    width = 100,
                    height = 200
                )
        val end =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = 1f,
                    normalizedY = 1f,
                    left = 10,
                    top = 20,
                    width = 100,
                    height = 200
                )

        assertEquals(10f, start!!.x, .0001f)
        assertEquals(20f, start.y, .0001f)
        assertEquals(109f, end!!.x, .0001f)
        assertEquals(219f, end.y, .0001f)
    }

    @Test
    fun outOfRangeNormalizedValuesClampToValidDisplayPixels() {
        val point =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = -0.4f,
                    normalizedY = 1.7f,
                    left = 0,
                    top = 0,
                    width = 1080,
                    height = 2340
                )

        assertEquals(0f, point!!.x, .0001f)
        assertEquals(2339f, point.y, .0001f)
    }

    @Test
    fun midpointPreservesLegacyInteriorMapping() {
        val point =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = .5f,
                    normalizedY = .5f,
                    left = 0,
                    top = 0,
                    width = 100,
                    height = 200
                )

        assertEquals(50f, point!!.x, .0001f)
        assertEquals(100f, point.y, .0001f)
    }

    @Test
    fun nearEdgeInteriorMappingIsPreservedUntilFinalClamp() {
        val point =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = .98f,
                    normalizedY = .49f,
                    left = 10,
                    top = 20,
                    width = 100,
                    height = 200
                )

        assertEquals(108f, point!!.x, .0001f)
        assertEquals(118f, point.y, .0001f)
    }

    @Test
    fun onePixelDisplayAlwaysReturnsOnlyValidPixel() {
        val point =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = 1f,
                    normalizedY = .25f,
                    left = 7,
                    top = 11,
                    width = 1,
                    height = 1
                )

        assertEquals(7f, point!!.x, .0001f)
        assertEquals(11f, point.y, .0001f)
    }

    @Test
    fun nonFiniteCoordinatesFailClosed() {
        assertNull(
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = Float.NaN,
                    normalizedY = .5f,
                    left = 0,
                    top = 0,
                    width = 100,
                    height = 100
                )
        )
        assertNull(
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = .5f,
                    normalizedY =
                        Float.POSITIVE_INFINITY,
                    left = 0,
                    top = 0,
                    width = 100,
                    height = 100
                )
        )
    }

    @Test
    fun invalidDisplaySpanFailsClosed() {
        assertNull(
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = .5f,
                    normalizedY = .5f,
                    left = 0,
                    top = 0,
                    width = 0,
                    height = 100
                )
        )
    }
}
