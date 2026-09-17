package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionCoordinateMapperTest {
    @Test
    fun zeroDegreeCoordinatesMatchLegacyHorizontalMirror() {
        val point =
            VisionCoordinateMapper
                .canonicalLandmark(
                    rawX = .20f,
                    rawY = .30f,
                    rawZ = -.10f,
                    rotationDegrees = 0
                )

        assertEquals(.80f, point.x, .0001f)
        assertEquals(.30f, point.y, .0001f)
        assertEquals(-.10f, point.z, .0001f)
    }

    @Test
    fun ninetyDegreeCoordinatesRotateBeforeSelfieMirror() {
        val point =
            VisionCoordinateMapper
                .canonicalLandmark(
                    rawX = .20f,
                    rawY = .30f,
                    rawZ = 0f,
                    rotationDegrees = 90
                )

        // 90° clockwise => (.70, .20), then selfie mirror => (.30, .20).
        // The old implementation incorrectly returned (.80, .30), which is
        // exactly the axis/orientation mismatch seen on the Galaxy A54.
        assertEquals(.30f, point.x, .0001f)
        assertEquals(.20f, point.y, .0001f)
    }

    @Test
    fun oneEightyDegreeCoordinatesRotateBeforeSelfieMirror() {
        val point =
            VisionCoordinateMapper
                .canonicalLandmark(
                    rawX = .20f,
                    rawY = .30f,
                    rawZ = 0f,
                    rotationDegrees = 180
                )

        assertEquals(.20f, point.x, .0001f)
        assertEquals(.70f, point.y, .0001f)
    }

    @Test
    fun twoSeventyDegreeCoordinatesRotateBeforeSelfieMirror() {
        val point =
            VisionCoordinateMapper
                .canonicalLandmark(
                    rawX = .20f,
                    rawY = .30f,
                    rawZ = 0f,
                    rotationDegrees = 270
                )

        assertEquals(.70f, point.x, .0001f)
        assertEquals(.80f, point.y, .0001f)
    }

    @Test
    fun canonicalCoordinatePreservesNonFiniteFailureSignals() {
        val point =
            VisionCoordinateMapper
                .canonicalLandmark(
                    rawX = Float.NaN,
                    rawY = .30f,
                    rawZ = 0f,
                    rotationDegrees = 90
                )

        assertTrue(point.x.isNaN())
        assertEquals(.30f, point.y, .0001f)
    }

    @Test
    fun rotationNormalizationHandlesEquivalentQuarterTurns() {
        assertEquals(
            270,
            VisionCoordinateMapper
                .normalizeRotation(-90)
        )
        assertEquals(
            90,
            VisionCoordinateMapper
                .normalizeRotation(450)
        )
    }

    @Test
    fun nonQuarterTurnRotationIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            VisionCoordinateMapper
                .normalizeRotation(45)
        }
    }

    @Test
    fun canonicalXMatchesLegacyHorizontalPixelMirror() {
        assertEquals(
            .80f,
            VisionCoordinateMapper
                .canonicalX(.20f),
            .0001f
        )
        assertEquals(
            .25f,
            VisionCoordinateMapper
                .canonicalX(.75f),
            .0001f
        )
        assertEquals(
            .50f,
            VisionCoordinateMapper
                .canonicalX(.50f),
            .0001f
        )
    }

    @Test
    fun canonicalXPreservesNonFiniteFailureSignals() {
        assertTrue(
            VisionCoordinateMapper
                .canonicalX(Float.NaN)
                .isNaN()
        )
        assertEquals(
            Float.POSITIVE_INFINITY,
            VisionCoordinateMapper
                .canonicalX(
                    Float.POSITIVE_INFINITY
                )
        )
    }

    @Test
    fun handednessSwapPreservesLegacyMirroredInputSemantics() {
        assertEquals(
            "Right",
            VisionCoordinateMapper
                .canonicalHandedness("Left")
        )
        assertEquals(
            "Left",
            VisionCoordinateMapper
                .canonicalHandedness("Right")
        )
        assertEquals(
            "Unknown",
            VisionCoordinateMapper
                .canonicalHandedness(
                    "Unknown"
                )
        )
    }
}
