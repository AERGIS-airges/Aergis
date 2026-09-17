package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionWatchdogPolicyTest {
    private fun stalled(first: Long = 100L, last: Long = 0L,
                        now: Long = 2100L, recovering: Boolean = false) =
        VisionWatchdogPolicy.isStalled(first, last, now, 2000L, recovering)

    @Test fun noSubmissionDoesNotTimeout() {
        assertFalse(stalled(first = 0L, now = 100_000L))
    }

    @Test fun firstResultTimeoutHasExactBoundary() {
        assertFalse(stalled(now = 2099L))
        assertTrue(stalled())
    }

    @Test fun resultOvertakingQueuedFailureCancelsTimeout() {
        assertTrue(stalled())
        assertFalse(stalled(last = 2100L, now = 2101L))
        assertTrue(stalled(last = 2100L, now = 4100L))
    }

    @Test fun rebuildSuspendsTimeoutAndGetsFreshDeadline() {
        assertFalse(stalled(now = 50_000L, recovering = true))
        assertFalse(stalled(first = 50_000L, last = 100L, now = 50_001L))
        assertTrue(stalled(first = 50_000L, now = 52_000L))
    }

    @Test fun futureReferenceCannotTriggerTimeout() {
        assertFalse(stalled(last = 3000L, now = 2500L))
    }
}
