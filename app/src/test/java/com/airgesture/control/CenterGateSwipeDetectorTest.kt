package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CenterGateSwipeDetectorTest {

    @Test
    fun mirroredLeftToRightCrossingEmitsUserSwipeLeft() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .30f, .50f, .12f))
        assertNull(detector.update(80, .36f, .50f, .12f))
        assertNull(detector.update(160, .48f, .50f, .12f))

        val decision =
            detector.update(
                240,
                .64f,
                .50f,
                .12f
            )

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            decision?.signal
        )
        assertTrue(
            (decision?.confidence ?: 0f) >= .60f
        )
    }

    @Test
    fun mirroredRightToLeftCrossingEmitsUserSwipeRight() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .72f, .50f, .12f)
        detector.update(80, .66f, .50f, .12f)
        detector.update(160, .52f, .50f, .12f)

        assertEquals(
            GestureSignal.SWIPE_RIGHT,
            detector.update(
                240,
                .36f,
                .50f,
                .12f
            )?.signal
        )
    }

    @Test
    fun verticalCenterAxisDeterminesUpAndDown() {
        val down = CenterGateSwipeDetector()
        down.update(0, .50f, .30f, .12f)
        down.update(80, .50f, .38f, .12f)
        down.update(160, .50f, .49f, .12f)

        assertEquals(
            GestureSignal.SWIPE_DOWN,
            down.update(
                240,
                .50f,
                .66f,
                .12f
            )?.signal
        )

        val up = CenterGateSwipeDetector()
        up.update(0, .50f, .70f, .12f)
        up.update(80, .50f, .62f, .12f)
        up.update(160, .50f, .51f, .12f)

        assertEquals(
            GestureSignal.SWIPE_UP,
            up.update(
                240,
                .50f,
                .34f,
                .12f
            )?.signal
        )
    }

    @Test
    fun handShapeAndPalmSizeDoNotGateCrosshairSwipe() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .05f)
        detector.update(90, .40f, .50f, .05f)
        detector.update(170, .49f, .50f, .05f)

        val decision =
            detector.update(
                250,
                .66f,
                .50f,
                .05f
            )

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            decision?.signal
        )
    }

    @Test
    fun slowCenterCrossingIsNotASwipe() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .32f, .50f, .12f))
        assertNull(detector.update(260, .44f, .50f, .12f))
        assertNull(detector.update(520, .56f, .50f, .12f))
        assertNull(detector.update(780, .66f, .50f, .12f))
    }

    @Test
    fun equalDiagonalCrossingIsAmbiguousInsteadOfGuessingAxis() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .30f, .30f, .12f))
        assertNull(detector.update(80, .39f, .39f, .12f))
        assertNull(detector.update(160, .49f, .49f, .12f))
        assertNull(detector.update(240, .66f, .66f, .12f))

        assertEquals(
            "GATE_AMBIGUOUS_DIAGONAL",
            detector.telemetry().state
        )
    }

    @Test
    fun curvedPassStillChoosesTheDominantCrosshairAxis() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .42f, .12f)
        detector.update(80, .39f, .44f, .12f)
        detector.update(160, .49f, .47f, .12f)

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                240,
                .66f,
                .58f,
                .12f
            )?.signal
        )
    }

    @Test
    fun preCrossingWobbleDoesNotCancelARealFinalPass() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(70, .43f, .50f, .12f)
        detector.update(140, .34f, .50f, .12f)
        detector.update(210, .49f, .50f, .12f)
        detector.update(280, .40f, .50f, .12f)

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                350,
                .66f,
                .50f,
                .12f
            )?.signal
        )
    }

    @Test
    fun immediateReturnDuringCooldownIsForgotten() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(80, .39f, .50f, .12f)
        detector.update(160, .49f, .50f, .12f)

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                240,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        assertNull(detector.update(300, .56f, .50f, .12f))
        assertNull(detector.update(360, .46f, .50f, .12f))
        assertNull(detector.update(420, .36f, .50f, .12f))
        assertNull(detector.update(620, .36f, .50f, .12f))
    }

    @Test
    fun naturalReturnAfterCooldownRearmsWithoutOppositeSwipe() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(80, .39f, .50f, .12f)
        detector.update(160, .49f, .50f, .12f)

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                240,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        // The demonstrated post-swipe hand return is reset/repositioning,
        // not an intentional opposite swipe, even when it takes longer
        // than the short global cooldown.
        assertNull(detector.update(620, .66f, .50f, .12f))
        assertNull(detector.update(700, .60f, .50f, .12f))
        assertNull(detector.update(780, .50f, .50f, .12f))
        assertNull(detector.update(860, .34f, .50f, .12f))
        assertEquals(
            "GATE_REARMED_HORIZONTAL",
            detector.telemetry().state
        )

        // Once returned to the starting side, the next deliberate pass
        // can fire normally.
        assertNull(detector.update(940, .40f, .50f, .12f))
        assertNull(detector.update(1020, .50f, .50f, .12f))
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                1100,
                .66f,
                .50f,
                .12f
            )?.signal
        )
    }

    @Test
    fun suppressedReturnHistoryCannotFireAfterRefractoryExpires() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(100, .42f, .50f, .12f)
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                220,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        // Natural return happens entirely while horizontal recognition is
        // suppressed.
        assertNull(detector.update(420, .58f, .50f, .12f))
        assertNull(detector.update(620, .46f, .50f, .12f))
        assertNull(detector.update(820, .34f, .50f, .12f))

        // Suppression has expired, but old return samples are ineligible and
        // cannot suddenly resurrect as SWIPE_RIGHT.
        assertNull(detector.update(940, .34f, .50f, .12f))
        assertNull(detector.update(1010, .35f, .50f, .12f))
    }

    @Test
    fun horizontalResetDoesNotBlockIntentionalVerticalSwipe() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(80, .39f, .50f, .12f)
        detector.update(160, .49f, .50f, .12f)
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                240,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        // Stay on the right side horizontally, but perform an upward
        // pass after cooldown. Only the horizontal axis is awaiting reset.
        assertNull(detector.update(620, .66f, .70f, .12f))
        assertNull(detector.update(700, .66f, .61f, .12f))
        assertNull(detector.update(780, .66f, .51f, .12f))
        assertEquals(
            GestureSignal.SWIPE_UP,
            detector.update(
                860,
                .66f,
                .34f,
                .12f
            )?.signal
        )
    }

    @Test
    fun strongOffCenterStrokeStillRecognizesWhenFramingMissesExactGate() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .18f, .72f, .10f))
        assertNull(detector.update(100, .28f, .70f, .10f))

        // Large, clearly horizontal user-left stroke that remains below the
        // exact horizontal center line still counts.
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                220,
                .49f,
                .68f,
                .10f
            )?.signal
        )
    }

    @Test
    fun twoStrongSamplesCanRecognizeFastSwipeAtLowResultRate() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .30f, .50f, .08f))
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                120,
                .62f,
                .50f,
                .08f
            )?.signal
        )
    }

    @Test
    fun oneFrameTeleportAcrossCenterIsRejected() {
        val detector = CenterGateSwipeDetector()

        assertNull(detector.update(0, .30f, .50f, .12f))
        assertNull(detector.update(100, .31f, .50f, .12f))

        assertNull(
            detector.update(
                133,
                .82f,
                .50f,
                .12f
            )
        )
        assertEquals(
            "GATE_REJECT_GLITCH",
            detector.telemetry().state
        )
    }

    @Test
    fun disabledSwipeIsInertAndItsReturnCannotFireOppositeDirection() {
        val detector =
            CenterGateSwipeDetector(
                signalEnabled = {
                    it != GestureSignal.SWIPE_LEFT
                }
            )

        detector.update(0, .30f, .50f, .12f)
        detector.update(80, .38f, .50f, .12f)
        detector.update(160, .49f, .50f, .12f)

        assertNull(
            detector.update(
                240,
                .66f,
                .50f,
                .12f
            )
        )
        assertEquals(
            "GATE_DISABLED_GESTURE",
            detector.telemetry().state
        )

        // Natural return is repositioning and must not turn into the enabled
        // opposite command.
        assertNull(detector.update(400, .56f, .50f, .12f))
        assertNull(detector.update(560, .46f, .50f, .12f))
        assertNull(detector.update(720, .34f, .50f, .12f))
        assertNull(detector.update(940, .34f, .50f, .12f))

        // Reposition slowly to the mirrored-image right without
        // creating another gesture, then make a fresh deliberate user-right
        // pass (mirrored image moves right-to-left).
        assertNull(detector.update(1240, .45f, .50f, .12f))
        assertNull(detector.update(1540, .56f, .50f, .12f))
        assertNull(detector.update(1840, .68f, .50f, .12f))
        assertNull(detector.update(1920, .58f, .50f, .12f))
        assertNull(detector.update(2000, .48f, .50f, .12f))
        assertEquals(
            GestureSignal.SWIPE_RIGHT,
            detector.update(
                2080,
                .34f,
                .50f,
                .12f
            )?.signal
        )
    }

    @Test
    fun timeAloneCannotRearmSameAxisWithoutPhysicalReturn() {
        val detector = CenterGateSwipeDetector()

        detector.update(0, .30f, .50f, .12f)
        detector.update(100, .42f, .50f, .12f)
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                220,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        // Far beyond the old 700 ms refractory interval, but the hand has
        // remained on the destination side. Same-axis intent must stay blocked.
        assertNull(
            detector.update(
                1_500,
                .67f,
                .50f,
                .12f
            )
        )
        assertEquals(
            "GATE_RETURN_SUPPRESSION_HORIZONTAL",
            detector.telemetry().state
        )

        assertNull(
            detector.update(
                1_620,
                .34f,
                .50f,
                .12f
            )
        )
        assertEquals(
            "GATE_REARMED_HORIZONTAL",
            detector.telemetry().state
        )
    }

    @Test
    fun physicalReturnRearmsWithoutWaitingForLegacyTimer() {
        val detector =
            CenterGateSwipeDetector(
                cooldownMs = 120L
            )

        detector.update(0, .30f, .50f, .12f)
        detector.update(70, .44f, .50f, .12f)
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                140,
                .66f,
                .50f,
                .12f
            )?.signal
        )

        // After the short global action cooldown, physically return to the
        // originating side. There is no fixed 700 ms same-axis timer anymore.
        assertNull(detector.update(270, .52f, .50f, .12f))
        assertNull(detector.update(340, .34f, .50f, .12f))
        assertEquals(
            "GATE_REARMED_HORIZONTAL",
            detector.telemetry().state
        )

        assertNull(detector.update(410, .43f, .50f, .12f))
        assertNull(detector.update(480, .51f, .50f, .12f))
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            detector.update(
                560,
                .66f,
                .50f,
                .12f
            )?.signal
        )
    }

}
