package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerAimEstimatorTest {
    private fun tip(
        x: Float,
        y: Float,
        z: Float = 0f
    ) = AimLandmark(x, y, z)

    @Test
    fun cursorTargetIsExactlyIndexFingertip() {
        val result =
            PointerAimEstimator().estimate(
                PointerAimInput(
                    indexTip = tip(.72f, .31f)
                )
            )

        assertTrue(result.stable)
        assertEquals(.72f, result.x, .0001f)
        assertEquals(.31f, result.y, .0001f)
        assertTrue(result.reasoning.contains("landmark 8"))
    }

    @Test
    fun movingFingertipMovesPointerOneForOneBeforeCalibration() {
        val estimator = PointerAimEstimator()
        val left =
            estimator.estimate(
                PointerAimInput(indexTip = tip(.35f, .30f))
            )
        val right =
            estimator.estimate(
                PointerAimInput(indexTip = tip(.72f, .30f))
            )

        assertEquals(.35f, left.x, .0001f)
        assertEquals(.72f, right.x, .0001f)
        assertTrue(right.x > left.x)
    }

    @Test
    fun outOfFrameFingertipGetsOnlySafetyClampNotPalmSubstitution() {
        val result =
            PointerAimEstimator().estimate(
                PointerAimInput(
                    indexTip = tip(1.35f, -.35f)
                )
            )

        assertEquals(1.20f, result.x, .0001f)
        assertEquals(-.20f, result.y, .0001f)
        assertTrue(result.stable)
    }

    @Test
    fun invalidFingertipHoldsLastValidFingertip() {
        val estimator = PointerAimEstimator()
        val stable =
            estimator.estimate(
                PointerAimInput(indexTip = tip(.61f, .24f))
            )
        val invalid =
            estimator.estimate(
                PointerAimInput(
                    indexTip = tip(Float.NaN, .20f)
                )
            )

        assertFalse(invalid.stable)
        assertEquals(stable.x, invalid.x, .0001f)
        assertEquals(stable.y, invalid.y, .0001f)
        assertEquals(0f, invalid.confidence, .0001f)
    }

    @Test
    fun estimatorInputConstructorAcceptsOnlyOneIndexTipLandmark() {
        val constructors =
            PointerAimInput::class.java.declaredConstructors
        assertEquals(1, constructors.size)

        val constructor = constructors.single()
        assertEquals(1, constructor.parameterCount)
        assertEquals(
            AimLandmark::class.java,
            constructor.parameterTypes.single()
        )

        // A compile-time construction check beside the signature assertion.
        val input = PointerAimInput(indexTip = tip(.42f, .37f))
        assertEquals(.42f, input.indexTip.x, .0001f)
        assertEquals(.37f, input.indexTip.y, .0001f)
    }
}
