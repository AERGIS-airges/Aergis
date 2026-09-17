package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PointerTrackerTest {

    private fun frame(
        time: Long,
        aimX: Float = .5f,
        aimY: Float = .5f,
        aimConfidence: Float = .95f,
        aimStable: Boolean = true,
        separation: Float = .60f,
        contactReliable: Boolean = true,
        straightness: Float = .98f,
        pressAllowed: Boolean = true
    ) = PointerFrame(
        timestampMs = time,
        handPresent = true,
        aimX = aimX,
        aimY = aimY,
        aimConfidence = aimConfidence,
        aimStable = aimStable,
        indexMiddleTipSeparation = separation,
        clickContactReliable = contactReliable,
        indexStraightness = straightness,
        pressAllowed = pressAllowed
    )

    private fun arm(tracker: PointerTracker) {
        tracker.update(frame(0))
        val armed = tracker.update(frame(150))
        assertEquals("POINTING", armed.state)
    }

    private fun confirmTipContact(
        tracker: PointerTracker,
        startMs: Long = 250L,
        aimX: Float = .5f
    ): PointerDecision {
        val candidate =
            tracker.update(
                frame(
                    startMs,
                    aimX = aimX,
                    separation = .16f
                )
            )
        assertEquals("PRESS_CANDIDATE", candidate.state)

        val confirmed =
            tracker.update(
                frame(
                    startMs + 70L,
                    aimX = aimX,
                    separation = .16f
                )
            )
        assertEquals("PRESSING", confirmed.state)
        return confirmed
    }

    @Test
    fun firstAimSampleMapsThroughCalibrationExactly() {
        val tracker = PointerTracker()

        val decision =
            tracker.update(
                frame(
                    0,
                    aimX = PointerCalibration.DEFAULT_MIN_X,
                    aimY = PointerCalibration.DEFAULT_MIN_Y
                )
            )

        assertEquals(0f, decision.x, .001f)
        assertEquals(0f, decision.y, .001f)
    }

    @Test
    fun microJitterIsDampedNearStationaryIndexTip() {
        val tracker = PointerTracker()
        tracker.update(frame(0))

        val before = tracker.update(frame(100, aimX = .500f))
        val jitter = tracker.update(frame(133, aimX = .502f))
        val rawMapped = PointerCalibration.DEFAULT.mapX(.502f)

        assertTrue(
            abs(jitter.x - before.x) <
                abs(rawMapped - before.x)
        )
    }

    @Test
    fun fastDeliberateIndexTipTravelRetainsHighResponsivenessAfterConfirmation() {
        val tracker = PointerTracker()
        tracker.update(frame(0, aimX = .35f))

        val staged = tracker.update(frame(40, aimX = .78f))
        val moved = tracker.update(frame(80, aimX = .78f))
        val target = PointerCalibration.DEFAULT.mapX(.78f)

        assertTrue(staged.x < moved.x)
        assertTrue(moved.x > .70f)
        assertTrue(abs(target - moved.x) < .08f)
    }

    @Test
    fun pointerDirectionChangesNeverCreateClick() {
        val tracker = PointerTracker()
        arm(tracker)

        val movements = listOf(
            frame(250, aimX = .70f, aimY = .50f),
            frame(330, aimX = .40f, aimY = .50f),
            frame(410, aimX = .40f, aimY = .72f),
            frame(490, aimX = .40f, aimY = .30f),
            frame(570, aimX = .76f, aimY = .68f),
            frame(650, aimX = .28f, aimY = .36f)
        )

        movements.forEach { sample ->
            val decision = tracker.update(sample)
            assertFalse(decision.tap)
            assertFalse(decision.hold)
            assertTrue(
                decision.state == "POINTING" ||
                    decision.state == "POINTING_ARMING"
            )
        }
    }

    @Test
    fun indexBendingAloneCannotClick() {
        val tracker = PointerTracker()
        arm(tracker)

        val samples = listOf(
            frame(250, straightness = .95f, separation = .60f),
            frame(320, straightness = .55f, separation = .60f),
            frame(390, straightness = .25f, separation = .60f),
            frame(460, straightness = .80f, separation = .60f),
            frame(530, straightness = .98f, separation = .60f)
        )

        samples.forEach { sample ->
            val decision = tracker.update(sample)
            assertFalse(decision.tap)
            assertFalse(decision.hold)
            assertFalse(decision.state.startsWith("PRESS"))
        }
    }

    @Test
    fun touchingIndexAndMiddleTipsThenReleasingCreatesPointerClick() {
        val tracker = PointerTracker()
        arm(tracker)

        val press = confirmTipContact(tracker)
        assertFalse(press.tap)
        assertFalse(press.hold)

        val release =
            tracker.update(
                frame(
                    430,
                    separation = .60f
                )
            )

        assertTrue(release.tap)
        assertFalse(release.hold)
        assertEquals("TAP", release.state)
        assertEquals(press.actionX!!, release.actionX!!, .001f)
        assertEquals(press.actionY!!, release.actionY!!, .001f)
        assertTrue(
            release.reasoning.contains(
                "Index + middle fingertip click"
            )
        )
    }

    @Test
    fun sustainedIndexMiddleTipContactProducesOneLongPress() {
        val tracker = PointerTracker()
        arm(tracker)
        confirmTipContact(tracker)

        tracker.update(frame(500, separation = .16f))
        tracker.update(frame(700, separation = .16f))

        val hold = tracker.update(frame(940, separation = .16f))
        assertTrue(hold.hold)
        assertFalse(hold.tap)
        assertEquals("LONG_PRESS", hold.state)

        val stillHeld = tracker.update(frame(1020, separation = .16f))
        assertFalse(stillHeld.hold)
        assertFalse(stillHeld.tap)
        assertEquals("LONG_PRESS_WAIT_RELEASE", stillHeld.state)
    }

    @Test
    fun pointerKeepsFollowingIndexTipDuringClickContact() {
        val tracker = PointerTracker()
        arm(tracker)

        val candidate =
            tracker.update(
                frame(
                    250,
                    aimX = .45f,
                    separation = .16f
                )
            )
        val confirmed =
            tracker.update(
                frame(
                    320,
                    aimX = .62f,
                    separation = .16f
                )
            )

        assertEquals("PRESS_CANDIDATE", candidate.state)
        assertEquals("PRESSING", confirmed.state)
        assertTrue(confirmed.x > candidate.x)
        assertFalse(confirmed.tap)
    }

    @Test
    fun poseVetoWithSeparatedTipsBlocksClickButNotIndexTipMovement() {
        val tracker = PointerTracker()
        arm(tracker)

        val before = tracker.update(frame(220, aimX = .45f))
        val locked =
            tracker.update(
                frame(
                    300,
                    aimX = .70f,
                    separation = .60f,
                    pressAllowed = false
                )
            )

        assertEquals("POINTING_LOCKED", locked.state)
        assertTrue(locked.x > before.x)
        assertFalse(locked.tap)
        assertFalse(locked.hold)
    }

    @Test
    fun explicitTipContactOverridesPoseVetoAndCompletesClickOnRelease() {
        val tracker = PointerTracker()
        arm(tracker)

        val candidate =
            tracker.update(
                frame(
                    250,
                    separation = .16f,
                    pressAllowed = false
                )
            )
        assertEquals("PRESS_CANDIDATE", candidate.state)

        val confirmed =
            tracker.update(
                frame(
                    320,
                    separation = .16f,
                    pressAllowed = false
                )
            )
        assertEquals("PRESSING", confirmed.state)

        val release =
            tracker.update(
                frame(
                    430,
                    separation = .60f,
                    pressAllowed = false
                )
            )

        assertTrue(release.tap)
        assertFalse(release.hold)
        assertEquals("TAP", release.state)
    }

    @Test
    fun unreliableContactGeometryFailsClosedButPointerRemainsLive() {
        val tracker = PointerTracker()
        arm(tracker)

        val before = tracker.update(frame(220, aimX = .45f))
        val unknown =
            tracker.update(
                frame(
                    300,
                    aimX = .70f,
                    separation = .10f,
                    contactReliable = false
                )
            )

        assertEquals("POINTING_CONTACT_UNKNOWN", unknown.state)
        assertTrue(unknown.x > before.x)
        assertFalse(unknown.tap)
        assertFalse(unknown.hold)
    }

    @Test
    fun veryBriefTipTouchIsRejectedAsNoise() {
        val tracker = PointerTracker()
        arm(tracker)

        val candidate = tracker.update(frame(250, separation = .16f))
        val released = tracker.update(frame(285, separation = .60f))

        assertEquals("PRESS_CANDIDATE", candidate.state)
        assertFalse(released.tap)
        assertFalse(released.hold)
    }

    @Test
    fun contactThresholdHysteresisPreventsChatterClicks() {
        val tracker = PointerTracker()
        arm(tracker)

        val samples = listOf(
            frame(250, separation = .37f),
            frame(300, separation = .35f),
            frame(350, separation = .39f),
            frame(400, separation = .36f),
            frame(450, separation = .44f),
            frame(520, separation = .50f)
        )

        samples.forEach { sample ->
            val decision = tracker.update(sample)
            assertFalse(decision.tap)
            assertFalse(decision.hold)
        }
    }

    @Test
    fun controlHandDiscontinuityCancelsPendingClickIntent() {
        val tracker = PointerTracker()
        arm(tracker)
        tracker.update(frame(250, separation = .16f))

        tracker.onControlHandDiscontinuity()

        val moved =
            tracker.update(
                frame(
                    330,
                    aimX = .72f,
                    aimY = .60f,
                    separation = .60f
                )
            )

        assertFalse(moved.tap)
        assertFalse(moved.hold)
    }

    @Test
    fun briefTrackingLossPreservesPendingCandidateWithoutInventingClick() {
        val tracker = PointerTracker()
        arm(tracker)
        tracker.update(frame(250, separation = .16f))

        val occluded =
            tracker.update(
                PointerFrame(
                    timestampMs = 300,
                    handPresent = false
                )
            )
        assertEquals("PRESS_OCCLUDED", occluded.state)
        assertFalse(occluded.tap)
        assertFalse(occluded.hold)

        val released = tracker.update(frame(360, separation = .60f))
        val back = tracker.update(frame(500, separation = .60f))
        assertFalse(released.tap)
        assertFalse(released.hold)
        assertFalse(back.tap)
        assertFalse(back.hold)
    }

    @Test
    fun nonFiniteAimSampleCannotPoisonCursorPosition() {
        val tracker = PointerTracker()
        val stable =
            tracker.update(
                frame(
                    0,
                    aimX = .45f,
                    aimY = .55f
                )
            )

        val bad =
            tracker.update(
                frame(
                    100,
                    aimX = Float.NaN,
                    aimY = Float.NaN
                )
            )

        assertTrue(bad.x.isFinite())
        assertTrue(bad.y.isFinite())
        assertEquals(stable.x, bad.x, .001f)
        assertEquals(stable.y, bad.y, .001f)
    }

    @Test
    fun ambiguousAimHoldsCursorAndCannotStartClick() {
        val tracker = PointerTracker()
        arm(tracker)

        val stable =
            tracker.update(
                frame(
                    250,
                    aimX = .62f,
                    aimY = .44f
                )
            )

        val ambiguous =
            tracker.update(
                frame(
                    330,
                    aimX = .20f,
                    aimY = .80f,
                    aimConfidence = .22f,
                    aimStable = false,
                    separation = .10f
                )
            )

        assertEquals("POINTING_AMBIGUOUS", ambiguous.state)
        assertEquals(stable.x, ambiguous.x, .0001f)
        assertEquals(stable.y, ambiguous.y, .0001f)
        assertFalse(ambiguous.tap)
        assertFalse(ambiguous.hold)
    }

    @Test
    fun sustainedTouchscreenContactRemainsActiveBeyondOldLongPressTimeout() {
        val tracker = PointerTracker()
        arm(tracker)
        confirmTipContact(tracker)

        val hold =
            tracker.update(
                frame(
                    1_000,
                    separation = .16f
                )
            )
        assertEquals("LONG_PRESS", hold.state)

        val stillDown =
            tracker.update(
                frame(
                    5_000,
                    aimX = .68f,
                    aimY = .62f,
                    separation = .16f
                )
            )

        assertEquals(
            "LONG_PRESS_WAIT_RELEASE",
            stillDown.state
        )
        assertFalse(stillDown.tap)
        assertFalse(stillDown.hold)
        assertTrue(stillDown.x > .5f)
    }


    @Test
    fun forgivingNearContactDoesNotRequirePerfectTipTouch() {
        val tracker = PointerTracker()
        arm(tracker)

        val candidate = tracker.update(frame(250, separation = .32f))
        assertEquals("PRESS_CANDIDATE", candidate.state)

        val confirmed = tracker.update(frame(320, separation = .32f))
        assertEquals("PRESSING", confirmed.state)

        val release = tracker.update(frame(430, separation = .60f))
        assertTrue(release.tap)
        assertFalse(release.hold)
        assertEquals("TAP", release.state)
    }

    @Test
    fun pointerCoastsThroughBriefLossLongerThanPressSafetyGrace() {
        val tracker = PointerTracker()
        arm(tracker)
        tracker.update(frame(250, aimX = .62f, aimY = .44f))

        val coast = tracker.update(
            PointerFrame(timestampMs = 500, handPresent = false)
        )
        assertTrue(coast.visible)
        assertEquals("COAST", coast.state)

        val expired = tracker.update(
            PointerFrame(timestampMs = 700, handPresent = false)
        )
        assertFalse(expired.visible)
        assertEquals("HIDDEN", expired.state)
    }

    @Test
    fun contactOcclusionStillFailsClosedBeforePointerCoastExpires() {
        val tracker = PointerTracker()
        arm(tracker)
        tracker.update(frame(250, separation = .16f))

        val firstGap = tracker.update(
            PointerFrame(timestampMs = 300, handPresent = false)
        )
        assertEquals("PRESS_OCCLUDED", firstGap.state)

        val contactExpired = tracker.update(
            PointerFrame(timestampMs = 450, handPresent = false)
        )
        assertTrue(contactExpired.visible)
        assertEquals("COAST", contactExpired.state)
        assertFalse(contactExpired.tap)
        assertFalse(contactExpired.hold)
    }

    @Test
    fun comfortReachHitsScreenEdgeBeforeCameraCalibrationEdge() {
        val tracker = PointerTracker()

        val decision = tracker.update(
            frame(
                0,
                aimX = .85f,
                aimY = .86f
            )
        )

        assertTrue(decision.x > .98f)
        assertTrue(decision.y > .98f)
    }

}
