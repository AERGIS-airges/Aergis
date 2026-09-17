package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerTipContactEstimatorTest {
    private fun straightHand(
        indexTipX: Float,
        middleTipX: Float,
        tipY: Float = .30f
    ): List<AimLandmark> =
        MutableList(21) { AimLandmark(.5f, .70f, 0f) }
            .also {
                it[5] = AimLandmark(indexTipX, .60f, 0f)
                it[6] = AimLandmark(indexTipX, .50f, 0f)
                it[7] = AimLandmark(indexTipX, .40f, 0f)
                it[8] = AimLandmark(indexTipX, tipY, 0f)

                it[9] = AimLandmark(middleTipX, .60f, 0f)
                it[10] = AimLandmark(middleTipX, .50f, 0f)
                it[11] = AimLandmark(middleTipX, .40f, 0f)
                it[12] = AimLandmark(middleTipX, tipY, 0f)
            }

    private fun relaxedCurledHand(): List<AimLandmark> =
        straightHand(.50f, .52f, .53f)
            .toMutableList()
            .also {
                it[5] = AimLandmark(.44f, .58f, 0f)
                it[6] = AimLandmark(.44f, .50f, 0f)
                it[7] = AimLandmark(.48f, .52f, 0f)
                it[8] = AimLandmark(.50f, .53f, 0f)

                it[9] = AimLandmark(.58f, .58f, 0f)
                it[10] = AimLandmark(.58f, .50f, 0f)
                it[11] = AimLandmark(.54f, .52f, 0f)
                it[12] = AimLandmark(.52f, .53f, 0f)
            }

    @Test
    fun straightIndexMiddleNearContactIsReliable() {
        val result =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.50f, .54f),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )

        assertTrue(result.reliable)
        assertEquals(.20f, result.normalizedSeparation, .0001f)
        assertTrue(result.source.contains("#8↔#12"))
    }

    @Test
    fun translatingEntireHandDoesNotChangeContactSeparation() {
        val first =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.40f, .44f),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )
        val second =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.70f, .74f),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )

        assertTrue(first.reliable)
        assertTrue(second.reliable)
        assertEquals(
            first.normalizedSeparation,
            second.normalizedSeparation,
            .0001f
        )
    }

    @Test
    fun imageSeparationIsAspectCorrected() {
        val square =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.50f, .60f),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )
        val wide =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.50f, .55f),
                imageHandScale = .20f,
                imageAspectRatio = 2f
            )

        assertTrue(square.reliable)
        assertTrue(wide.reliable)
        assertEquals(
            square.normalizedSeparation,
            wide.normalizedSeparation,
            .0001f
        )
    }

    @Test
    fun trustedWorldGeometryTakesPriority() {
        val world =
            MutableList(21) { AimLandmark(0f, .05f, 0f) }
                .also {
                    it[5] = AimLandmark(0f, .03f, 0f)
                    it[6] = AimLandmark(0f, .02f, 0f)
                    it[7] = AimLandmark(0f, .01f, 0f)
                    it[8] = AimLandmark(0f, 0f, 0f)
                    it[9] = AimLandmark(.01f, .03f, 0f)
                    it[10] = AimLandmark(.01f, .02f, 0f)
                    it[11] = AimLandmark(.01f, .01f, 0f)
                    it[12] = AimLandmark(.01f, 0f, 0f)
                }

        val result =
            PointerTipContactEstimator.estimate(
                imageHand = straightHand(.20f, .80f),
                imageHandScale = .20f,
                imageAspectRatio = 1f,
                trustedWorldHand = world,
                worldHandScale = .05f
            )

        assertTrue(result.reliable)
        assertEquals(.20f, result.normalizedSeparation, .0001f)
        assertTrue(result.source.startsWith("world"))
    }

    @Test
    fun relaxedCurledFingerProximityCannotBecomeReliableClickContact() {
        val result =
            PointerTipContactEstimator.estimate(
                imageHand = relaxedCurledHand(),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )

        assertFalse(result.reliable)
        assertTrue(result.normalizedSeparation < .34f)
        assertTrue(result.source.contains("suppressed"))
    }

    @Test
    fun invalidContactGeometryFailsClosed() {
        val result =
            PointerTipContactEstimator.estimate(
                imageHand = emptyList(),
                imageHandScale = .20f,
                imageAspectRatio = 1f
            )

        assertFalse(result.reliable)
        assertTrue(result.normalizedSeparation.isInfinite())
    }
}
