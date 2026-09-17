package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerClickOcclusionRegressionTest {
    private fun frame(
        t: Long,
        separation: Float = .70f,
        reliable: Boolean = true,
        handPresent: Boolean = true,
        aimStable: Boolean = true
    ) = PointerFrame(
        timestampMs = t,
        handPresent = handPresent,
        aimX = .50f,
        aimY = .50f,
        aimConfidence = .95f,
        aimStable = aimStable,
        indexMiddleTipSeparation = separation,
        clickContactReliable = reliable
    )

    private fun arm(tracker: PointerTracker) {
        tracker.update(frame(0L))
        tracker.update(frame(130L))
    }

    private fun beginConfirmedContact(tracker: PointerTracker) {
        tracker.update(frame(150L, separation = .20f))
        val confirmed = tracker.update(frame(210L, separation = .20f))
        assertTrue(confirmed.state == "PRESSING")
    }

    @Test
    fun confirmedContactSurvivesBriefGeometryOcclusionThenClicksOnRelease() {
        val tracker = PointerTracker()
        arm(tracker)
        beginConfirmedContact(tracker)
        val occluded = tracker.update(frame(260L, reliable = false))
        assertTrue(occluded.state == "PRESS_OCCLUDED")
        assertFalse(occluded.tap)
        assertFalse(occluded.hold)
        val released = tracker.update(frame(340L, separation = .70f))
        assertTrue(released.tap)
        assertFalse(released.hold)
    }

    @Test
    fun confirmedContactSurvivesBriefWholeHandDropoutThenClicksOnRelease() {
        val tracker = PointerTracker()
        arm(tracker)
        beginConfirmedContact(tracker)
        val occluded = tracker.update(frame(260L, handPresent = false))
        assertTrue(occluded.state == "PRESS_OCCLUDED")
        assertFalse(occluded.tap)
        val released = tracker.update(frame(340L, separation = .70f))
        assertTrue(released.tap)
    }

    @Test
    fun prolongedContactGeometryLossCancelsInsteadOfInventingClick() {
        val tracker = PointerTracker()
        arm(tracker)
        beginConfirmedContact(tracker)
        tracker.update(frame(260L, reliable = false))
        val expired = tracker.update(frame(400L, reliable = false))
        assertFalse(expired.tap)
        assertFalse(expired.hold)
        val released = tracker.update(frame(450L, separation = .70f))
        assertFalse(released.tap)
        assertFalse(released.hold)
    }

    @Test
    fun trackingLossWithoutStartedContactCannotCreateClick() {
        val tracker = PointerTracker()
        arm(tracker)
        val unknown = tracker.update(frame(180L, reliable = false))
        assertFalse(unknown.tap)
        assertFalse(unknown.hold)
        assertTrue(unknown.state == "POINTING_CONTACT_UNKNOWN")
        val released = tracker.update(frame(250L, separation = .70f))
        assertFalse(released.tap)
        assertFalse(released.hold)
    }

    @Test
    fun occludedTimeDoesNotCountTowardLongPressDuration() {
        val tracker = PointerTracker()
        arm(tracker)
        beginConfirmedContact(tracker)
        tracker.update(frame(260L, reliable = false))
        val reacquired = tracker.update(frame(340L, separation = .20f))
        assertFalse(reacquired.hold)
        val stillPressing = tracker.update(frame(780L, separation = .20f))
        assertFalse(stillPressing.hold)
    }
}
