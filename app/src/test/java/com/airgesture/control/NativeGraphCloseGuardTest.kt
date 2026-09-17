package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGraphCloseGuardTest {
    @Test
    fun successfulCloseAllowsBackingFrameRelease() {
        val outcome =
            NativeGraphCloseGuard.attempt {
                Unit
            }

        assertTrue(outcome.safeToReleaseFrames)
        assertNull(outcome.failureMessage)
    }

    @Test
    fun failedCloseQuarantinesBackingFrameOwnership() {
        val outcome =
            NativeGraphCloseGuard.attempt {
                error("synthetic close failure")
            }

        assertFalse(outcome.safeToReleaseFrames)
        assertTrue(
            outcome.failureMessage
                ?.contains("synthetic close failure") ==
                true
        )
    }
}
