package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsableVisionGapPolicyTest {
    @Test
    fun pointerFailsSafeAfterUsableDataExceedsFreshnessEnvelope() {
        assertFalse(
            UsableVisionGapPolicy
                .shouldFailSafeReset(
                    firstSubmittedMs = 1_000L,
                    lastUsableResultMs = 1_100L,
                    nowMs = 1_300L,
                    pointerActive = true,
                    alreadyApplied = false
                )
        )
        assertTrue(
            UsableVisionGapPolicy
                .shouldFailSafeReset(
                    firstSubmittedMs = 1_000L,
                    lastUsableResultMs = 1_100L,
                    nowMs = 1_321L,
                    pointerActive = true,
                    alreadyApplied = false
                )
        )
    }

    @Test
    fun neverUsableStreamUsesFirstSubmissionAsLivenessReference() {
        assertTrue(
            UsableVisionGapPolicy
                .shouldFailSafeReset(
                    firstSubmittedMs = 1_000L,
                    lastUsableResultMs = 0L,
                    nowMs = 1_221L,
                    pointerActive = true,
                    alreadyApplied = false
                )
        )
    }

    @Test
    fun failSafeDoesNotRepeatUntilFreshDataRearmsIt() {
        assertFalse(
            UsableVisionGapPolicy
                .shouldFailSafeReset(
                    firstSubmittedMs = 1_000L,
                    lastUsableResultMs = 1_100L,
                    nowMs = 2_000L,
                    pointerActive = true,
                    alreadyApplied = true
                )
        )
    }
}
