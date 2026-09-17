package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionLatencyPolicyTest {
    @Test
    fun pointerModeAllowsOnlyCurrentPlusOneLatestCandidate() {
        assertEquals(
            2,
            VisionLatencyPolicy.maxInFlight(
                pointerActive = true
            )
        )
    }

    @Test
    fun pointerRejectsDangerouslyOldResults() {
        assertTrue(
            VisionLatencyPolicy.resultIsFreshEnough(
                pointerActive = true,
                resultAgeMs = 140L
            )
        )
        assertFalse(
            VisionLatencyPolicy.resultIsFreshEnough(
                pointerActive = true,
                resultAgeMs = 260L
            )
        )
    }

    @Test
    fun gestureModeKeepsWiderFreshnessEnvelopeButStillHasHardLimit() {
        assertTrue(
            VisionLatencyPolicy.resultIsFreshEnough(
                pointerActive = false,
                resultAgeMs = 260L
            )
        )
        assertFalse(
            VisionLatencyPolicy.resultIsFreshEnough(
                pointerActive = false,
                resultAgeMs = 600L
            )
        )
    }

    @Test
    fun bothModesKeepSubmissionDepthStrictlyBounded() {
        assertEquals(
            2,
            VisionLatencyPolicy.maxInFlight(
                pointerActive = false
            )
        )
        assertEquals(
            2,
            VisionLatencyPolicy.maxInFlight(
                pointerActive = true
            )
        )
    }
}
