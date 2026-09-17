package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerCalibrationTest {

    @Test
    fun defaultBoundsMapToScreenEdgesAndCenter() {
        val calibration = PointerCalibration.DEFAULT

        assertEquals(
            0f,
            calibration.mapX(
                PointerCalibration.DEFAULT_MIN_X
            ),
            .001f
        )
        assertEquals(
            1f,
            calibration.mapX(
                PointerCalibration.DEFAULT_MAX_X
            ),
            .001f
        )
        assertEquals(
            0f,
            calibration.mapY(
                PointerCalibration.DEFAULT_MIN_Y
            ),
            .001f
        )
        assertEquals(
            1f,
            calibration.mapY(
                PointerCalibration.DEFAULT_MAX_Y
            ),
            .001f
        )
        assertEquals(
            .5f,
            calibration.mapX(.5f),
            .001f
        )
        assertEquals(
            .5f,
            calibration.mapY(.5f),
            .001f
        )
    }

    @Test
    fun defaultResponseIsLinearSoCenterTravelIsNotArtificiallyCompressed() {
        val calibration =
            PointerCalibration.DEFAULT

        assertEquals(
            1f,
            PointerCalibration.DEFAULT_CURVE_X,
            .001f
        )
        assertEquals(
            1f,
            PointerCalibration.DEFAULT_CURVE_Y,
            .001f
        )
        assertEquals(
            .25f,
            calibration.mapX(.30f),
            .001f
        )
    }

    @Test
    fun precisionCurveReducesGainNearCenterWithoutLosingEdges() {
        val linear =
            PointerCalibration(
                minX = .10f,
                maxX = .90f,
                curveX = 1f
            ).sanitized()
        val precise =
            PointerCalibration(
                minX = .10f,
                maxX = .90f,
                curveX = 1.5f
            ).sanitized()

        val raw = .30f

        assertTrue(
            precise.mapX(raw) >
                linear.mapX(raw)
        )
        assertEquals(
            0f,
            precise.mapX(.10f),
            .001f
        )
        assertEquals(
            1f,
            precise.mapX(.90f),
            .001f
        )
    }

    @Test
    fun reversedAndUnsafeBoundsBecomeValid() {
        val calibration =
            PointerCalibration(
                minX = 1.12f,
                maxX = .86f,
                minY = -.4f,
                maxY = .08f,
                curveX = 8f,
                curveY = Float.NaN
            ).sanitized()

        assertTrue(
            calibration.minX <
                calibration.maxX
        )
        assertTrue(
            calibration.maxX -
                calibration.minX >=
                PointerCalibration.MIN_SPAN -
                .001f
        )
        assertTrue(
            calibration.minY >=
                PointerCalibration.MIN_BOUND
        )
        assertTrue(
            calibration.maxY <=
                PointerCalibration.MAX_BOUND
        )
        assertTrue(
            calibration.curveX <=
                PointerCalibration.MAX_CURVE
        )
        assertEquals(
            PointerCalibration.DEFAULT_CURVE_Y,
            calibration.curveY,
            .001f
        )
    }

    @Test
    fun offCenterCalibrationIsPreserved() {
        val calibration =
            PointerCalibration(
                minX = .32f,
                maxX = .78f,
                minY = .18f,
                maxY = .72f,
                curveX = 1.42f,
                curveY = 1.18f
            ).sanitized()

        assertEquals(.32f, calibration.minX, .001f)
        assertEquals(.78f, calibration.maxX, .001f)
        assertEquals(.18f, calibration.minY, .001f)
        assertEquals(.72f, calibration.maxY, .001f)
        assertEquals(1.42f, calibration.curveX, .001f)
        assertEquals(1.18f, calibration.curveY, .001f)
    }

    @Test
    fun valuesOutsideCalibratedAimRegionClampToDisplay() {
        val calibration =
            PointerCalibration(
                minX = .2f,
                maxX = .8f,
                minY = .2f,
                maxY = .8f
            ).sanitized()

        assertEquals(
            0f,
            calibration.mapX(-.15f),
            .001f
        )
        assertEquals(
            1f,
            calibration.mapX(1.15f),
            .001f
        )
    }
    @Test
    fun calibrationSessionCancelRestoresExactBaseline() {
        val baseline =
            PointerCalibration(
                minX = .18f,
                maxX = .82f,
                minY = .16f,
                maxY = .84f,
                curveX = 1.2f,
                curveY = 1.1f
            ).sanitized()
        val session =
            PointerCalibrationSession(
                baseline
            )

        session.update(
            baseline.copy(
                minX = .30f,
                curveX = 1.6f
            )
        )
        session.resetDraft()

        assertEquals(
            baseline,
            session.cancel()
        )
        assertEquals(
            baseline,
            session.draft
        )
    }

    @Test
    fun calibrationSessionApplyReturnsSanitizedDraft() {
        val session =
            PointerCalibrationSession(
                PointerCalibration.DEFAULT
            )

        val draft =
            session.update(
                PointerCalibration(
                    minX = .22f,
                    maxX = .78f,
                    curveX = 1.42f
                )
            )

        assertEquals(
            draft,
            session.apply()
        )
    }

    @Test
    fun calibrationContextKeysSeparateHandAndOrientation() {
        val autoPortrait =
            PointerCalibrationContext
                .forOrientation(
                    ControlHandPreference.AUTO,
                    landscape = false
                )
        val rightPortrait =
            PointerCalibrationContext
                .forOrientation(
                    ControlHandPreference.RIGHT,
                    landscape = false
                )
        val autoLandscape =
            PointerCalibrationContext
                .forOrientation(
                    ControlHandPreference.AUTO,
                    landscape = true
                )

        assertTrue(
            autoPortrait.storageKey !=
                rightPortrait.storageKey
        )
        assertTrue(
            autoPortrait.storageKey !=
                autoLandscape.storageKey
        )
        assertEquals(
            PointerCalibrationOrientation
                .LANDSCAPE,
            autoLandscape.orientation
        )
    }

}
