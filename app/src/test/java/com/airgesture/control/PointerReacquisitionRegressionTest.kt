package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerReacquisitionRegressionTest {

    private fun frame(
        time: Long,
        aimX: Float,
        aimY: Float = .5f
    ) = PointerFrame(
        timestampMs = time,
        handPresent = true,
        aimX = aimX,
        aimY = aimY,
        aimConfidence = .95f,
        aimStable = true,
        indexMiddleTipSeparation = .60f,
        clickContactReliable = true
    )

    @Test
    fun sameHandReacquisitionAfterGraceDoesNotTeleportToFirstNoisySample() {
        val tracker = PointerTracker()
        tracker.update(frame(0L, .50f))
        tracker.update(frame(150L, .50f))

        val hidden =
            tracker.update(
                PointerFrame(
                    timestampMs = 520L,
                    handPresent = false
                )
            )
        assertEquals("HIDDEN", hidden.state)

        val reacquired = tracker.update(frame(580L, .90f))
        val mappedTarget = PointerCalibration.DEFAULT.mapX(.90f)

        assertEquals("ARMING", reacquired.state)
        assertTrue(reacquired.x < .60f)
        assertTrue(reacquired.x < mappedTarget - .25f)
    }

    @Test
    fun explicitControlHandDiscontinuityStartsNewHandWithoutOldMotionHistory() {
        val tracker = PointerTracker()
        tracker.update(frame(0L, .50f))
        tracker.update(frame(150L, .50f))

        tracker.onControlHandDiscontinuity()

        val newHand = tracker.update(frame(200L, .90f))
        val mappedTarget = PointerCalibration.DEFAULT.mapX(.90f)

        assertEquals("ARMING", newHand.state)
        assertTrue(newHand.x > .95f)
        assertEquals(mappedTarget, newHand.x, .001f)
    }
}
