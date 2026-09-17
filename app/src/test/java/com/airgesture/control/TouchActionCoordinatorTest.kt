package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchActionCoordinatorTest {
    @Test
    fun activeTouchOwnsDispatchUntilCompletion() {
        val coordinator = TouchActionCoordinator()
        val first =
            coordinator.tryAcquire(
                nowMs = 100L,
                deadlineMs = 300L,
                label = "first"
            )
        assertNotNull(first.ticket)

        val overlapping =
            coordinator.tryAcquire(
                nowMs = 120L,
                deadlineMs = 300L,
                label = "second"
            )
        assertEquals(
            TouchAcquireFailure.BUSY,
            overlapping.failure
        )

        assertTrue(
            coordinator.release(
                first.ticket!!
            )
        )
        val afterCompletion =
            coordinator.tryAcquire(
                nowMs = 130L,
                deadlineMs = 300L,
                label = "second"
            )
        assertNotNull(afterCompletion.ticket)
    }

    @Test
    fun expiredTouchIsRejectedWithoutTakingOwnership() {
        val coordinator = TouchActionCoordinator()
        val expired =
            coordinator.tryAcquire(
                nowMs = 301L,
                deadlineMs = 300L,
                label = "late"
            )

        assertEquals(
            TouchAcquireFailure.EXPIRED,
            expired.failure
        )
        assertNull(expired.ticket)
        assertNull(coordinator.activeLabel())
    }

    @Test
    fun staleCompletionCannotReleaseNewOwner() {
        val coordinator = TouchActionCoordinator()
        val first =
            coordinator.tryAcquire(
                100L,
                300L,
                "first"
            ).ticket!!
        coordinator.release(first)

        val second =
            coordinator.tryAcquire(
                140L,
                300L,
                "second"
            ).ticket!!

        assertTrue(
            !coordinator.release(first)
        )
        assertEquals(
            "second",
            coordinator.activeLabel()
        )
        assertTrue(coordinator.release(second))
    }
}
