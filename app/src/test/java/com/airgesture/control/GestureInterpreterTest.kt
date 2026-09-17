package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInterpreterTest {

    private fun palm(
        time: Long,
        x: Float,
        y: Float,
        scale: Float = .12f,
        confidence: Float = .95f
    ) = HandFrame(
        timestampMs = time,
        handPresent = true,
        centerX = x,
        centerY = y,
        handScale = scale,
        category = "Open_Palm",
        categoryScore = confidence,
        openPalmScore = confidence
    )

    private fun hand(
        time: Long,
        x: Float,
        y: Float,
        scale: Float = .12f,
        category: String = "None",
        score: Float = 0f
    ) = HandFrame(
        timestampMs = time,
        handPresent = true,
        centerX = x,
        centerY = y,
        handScale = scale,
        category = category,
        categoryScore = score,
        openPalmScore = 0f
    )

    private fun pose(
        time: Long,
        category: String,
        score: Float = .90f
    ) = hand(
        time = time,
        x = .5f,
        y = .5f,
        category = category,
        score = score
    )

    @Test
    fun centerGateSwipeWorksWithoutPoseClassification() {
        val interpreter = GestureInterpreter()

        assertNull(
            interpreter.evaluate(
                hand(0, .30f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(100, .30f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(180, .39f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(260, .49f, .50f)
            )
        )

        val decision =
            interpreter.evaluate(
                hand(340, .66f, .50f)
            )

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            decision?.signal
        )
        assertTrue(
            decision?.reasoning
                ?.contains("user-perspective broad hand stroke") == true
        )
    }

    @Test
    fun controlHandChangeCannotCompletePartialGateCrossing() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(
            hand(0, .30f, .50f)
        )
        interpreter.evaluate(
            hand(100, .30f, .50f)
        )
        interpreter.evaluate(
            hand(180, .42f, .50f)
        )
        interpreter.evaluate(
            hand(240, .49f, .50f)
        )

        interpreter.resetForControlHandChange()

        assertNull(
            interpreter.evaluate(
                hand(280, .66f, .50f)
            )
        )
        assertEquals(
            "PRESENCE",
            interpreter.telemetry().state
        )
    }

    @Test
    fun slowCenterPassDoesNotBecomeSwipe() {
        val interpreter = GestureInterpreter()

        val frames = listOf(
            hand(0, .32f, .50f),
            hand(100, .32f, .50f),
            hand(380, .43f, .50f),
            hand(660, .56f, .50f),
            hand(940, .67f, .50f)
        )

        frames.forEach {
            assertNull(interpreter.evaluate(it))
        }
    }

    @Test
    fun cameraJitterNearGateDoesNotBecomeSwipe() {
        val interpreter = GestureInterpreter()

        val frames = listOf(
            hand(0, .50f, .50f),
            hand(100, .50f, .50f),
            hand(180, .505f, .497f),
            hand(260, .496f, .503f),
            hand(340, .507f, .501f),
            hand(420, .493f, .499f),
            hand(500, .504f, .502f)
        )

        frames.forEach {
            assertNull(interpreter.evaluate(it))
        }
    }

    @Test
    fun diagonalGateCrossingDoesNotChooseArbitraryAxis() {
        val interpreter = GestureInterpreter()

        val frames = listOf(
            hand(0, .30f, .30f),
            hand(100, .30f, .30f),
            hand(180, .39f, .39f),
            hand(260, .49f, .49f),
            hand(340, .66f, .66f)
        )

        frames.forEach {
            assertNull(interpreter.evaluate(it))
        }
    }

    @Test
    fun swipeReturnIsResetBeforeNextDeliberatePass() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(hand(0, .30f, .50f))
        interpreter.evaluate(hand(100, .30f, .50f))
        interpreter.evaluate(hand(180, .39f, .50f))
        interpreter.evaluate(hand(260, .49f, .50f))

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            interpreter.evaluate(
                hand(340, .66f, .50f)
            )?.signal
        )

        // The hand can remain at the endpoint while cooldown expires.
        assertNull(
            interpreter.evaluate(
                hand(460, .66f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(720, .66f, .50f)
            )
        )

        // Returning across the same axis is repositioning only, matching
        // the demonstrated swipe -> return -> ready interaction.
        assertNull(
            interpreter.evaluate(
                hand(800, .60f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(880, .50f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(960, .34f, .50f)
            )
        )

        // A fresh deliberate pass after re-arm can emit again.
        assertNull(
            interpreter.evaluate(
                hand(1040, .40f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(1120, .50f, .50f)
            )
        )
        assertEquals(
            GestureSignal.SWIPE_LEFT,
            interpreter.evaluate(
                hand(1200, .66f, .50f)
            )?.signal
        )
    }

    @Test
    fun staticGestureNeedsContinuousDwellAfterDifferentPose() {
        val interpreter = GestureInterpreter()

        assertNull(
            interpreter.evaluate(
                pose(0, "Closed_Fist", .91f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(100, "Closed_Fist", .91f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(220, "Victory", .88f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(300, "Closed_Fist", .92f)
            )
        )

        // The earlier fist frames cannot satisfy dwell after Victory.
        assertNull(
            interpreter.evaluate(
                pose(390, "Closed_Fist", .93f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(470, "Closed_Fist", .93f)
            )
        )

        val decision =
            interpreter.evaluate(
                pose(560, "Closed_Fist", .93f)
            )

        assertEquals(
            GestureSignal.CLOSED_FIST,
            decision?.signal
        )
        assertTrue(
            decision?.reasoning
                ?.contains("consensus") == true
        )
    }

    @Test
    fun heldStaticGestureDoesNotRepeatUntilReleased() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(
            pose(0, "Closed_Fist", .92f)
        )
        interpreter.evaluate(
            pose(100, "Closed_Fist", .92f)
        )
        interpreter.evaluate(
            pose(220, "Closed_Fist", .92f)
        )

        assertEquals(
            GestureSignal.CLOSED_FIST,
            interpreter.evaluate(
                pose(360, "Closed_Fist", .92f)
            )?.signal
        )

        assertNull(
            interpreter.evaluate(
                pose(800, "Closed_Fist", .94f)
            )
        )
    }

    @Test
    fun lowConfidenceStaticGestureDoesNotFire() {
        val interpreter = GestureInterpreter()

        assertNull(
            interpreter.evaluate(
                pose(0, "Closed_Fist", .40f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(150, "Closed_Fist", .40f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(350, "Closed_Fist", .40f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(600, "Closed_Fist", .40f)
            )
        )
    }

    @Test
    fun steadyOpenPalmStillRegistersAsOwnGesture() {
        val interpreter = GestureInterpreter()

        assertNull(
            interpreter.evaluate(
                palm(0, .50f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                palm(100, .50f, .50f)
            )
        )
        assertNull(
            interpreter.evaluate(
                palm(300, .502f, .499f)
            )
        )
        assertNull(
            interpreter.evaluate(
                palm(560, .501f, .501f)
            )
        )

        val decision =
            interpreter.evaluate(
                palm(780, .501f, .500f)
            )

        assertEquals(
            GestureSignal.OPEN_PALM,
            decision?.signal
        )
        assertTrue(
            decision?.reasoning
                ?.contains("steady") == true
        )
    }

    @Test
    fun movingOpenPalmCanSwipeBecauseShapeIsIrrelevant() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(
            palm(0, .70f, .50f)
        )
        interpreter.evaluate(
            palm(100, .70f, .50f)
        )
        interpreter.evaluate(
            palm(180, .62f, .50f)
        )
        interpreter.evaluate(
            palm(260, .51f, .50f)
        )

        val swipe =
            interpreter.evaluate(
                palm(340, .34f, .50f)
            )

        assertEquals(
            GestureSignal.SWIPE_RIGHT,
            swipe?.signal
        )
    }

    @Test
    fun strongStaticPoseStillWorksAfterNeutralTracking() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(
            hand(0, .50f, .50f)
        )
        interpreter.evaluate(
            hand(100, .50f, .50f)
        )

        assertNull(
            interpreter.evaluate(
                pose(220, "Victory", .95f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(350, "Victory", .95f)
            )
        )

        val decision =
            interpreter.evaluate(
                pose(500, "Victory", .95f)
            )

        assertEquals(
            GestureSignal.VICTORY,
            decision?.signal
        )
    }
    @Test
    fun customLandmarkPoseUsesSameTemporalConsensus() {
        val interpreter = GestureInterpreter()
        val category =
            LandmarkPoseRecognizer.CATEGORY_OK_SIGN

        assertNull(
            interpreter.evaluate(
                pose(0, category, .91f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(100, category, .92f)
            )
        )
        assertNull(
            interpreter.evaluate(
                pose(220, category, .93f)
            )
        )

        val decision =
            interpreter.evaluate(
                pose(360, category, .94f)
            )

        assertEquals(
            GestureSignal.OK_SIGN,
            decision?.signal
        )
        assertTrue(
            (decision?.confidence ?: 0f) >= .70f
        )
    }


    @Test
    fun doubleGunNavigationCommitsFasterThanOrdinaryStaticPose() {
        val interpreter = GestureInterpreter()
        val category =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_UP

        assertNull(
            interpreter.evaluate(
                hand(
                    time = 0,
                    x = .50f,
                    y = .50f,
                    category = category,
                    score = .92f
                )
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(
                    time = 100,
                    x = .50f,
                    y = .50f,
                    category = category,
                    score = .93f
                )
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(
                    time = 170,
                    x = .50f,
                    y = .50f,
                    category = category,
                    score = .94f
                )
            )
        )

        val decision =
            interpreter.evaluate(
                hand(
                    time = 250,
                    x = .50f,
                    y = .50f,
                    category = category,
                    score = .95f
                )
            )

        assertEquals(
            GestureSignal.NAV_SCROLL_UP,
            decision?.signal
        )
        assertEquals(
            "NAV_COMMITTED",
            interpreter.telemetry().state
        )
    }

    @Test
    fun heldNavigationRepeatsAtControlledInterval() {
        val interpreter = GestureInterpreter()
        val category =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_DOWN

        interpreter.evaluate(
            hand(0, .50f, .50f, category = category, score = .94f)
        )
        interpreter.evaluate(
            hand(100, .50f, .50f, category = category, score = .94f)
        )
        interpreter.evaluate(
            hand(170, .50f, .50f, category = category, score = .94f)
        )

        assertEquals(
            GestureSignal.NAV_SCROLL_DOWN,
            interpreter.evaluate(
                hand(250, .50f, .50f, category = category, score = .94f)
            )?.signal
        )

        assertNull(
            interpreter.evaluate(
                hand(500, .50f, .50f, category = category, score = .94f)
            )
        )

        val repeat =
            interpreter.evaluate(
                hand(780, .50f, .50f, category = category, score = .94f)
            )

        assertEquals(
            GestureSignal.NAV_SCROLL_DOWN,
            repeat?.signal
        )
        assertEquals(
            "NAV_REPEAT",
            interpreter.telemetry().state
        )
    }

    @Test
    fun navigationPoseSuppressesCenterLineSwipeInterpretation() {
        val interpreter = GestureInterpreter()
        val category =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_RIGHT

        assertNull(
            interpreter.evaluate(
                hand(0, .30f, .50f, category = category, score = .94f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(100, .34f, .50f, category = category, score = .94f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(170, .48f, .50f, category = category, score = .94f)
            )
        )

        val decision =
            interpreter.evaluate(
                hand(250, .68f, .50f, category = category, score = .94f)
            )

        assertEquals(
            GestureSignal.NAV_SCROLL_RIGHT,
            decision?.signal
        )
    }


    @Test
    fun heldDoubleGunCanChangeDirectionWithoutPoseRelease() {
        val interpreter = GestureInterpreter()
        val up =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_UP
        val right =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_RIGHT

        interpreter.evaluate(
            hand(0, .50f, .50f, category = up, score = .94f)
        )
        interpreter.evaluate(
            hand(100, .50f, .50f, category = up, score = .94f)
        )
        interpreter.evaluate(
            hand(170, .50f, .50f, category = up, score = .94f)
        )
        assertEquals(
            GestureSignal.NAV_SCROLL_UP,
            interpreter.evaluate(
                hand(250, .50f, .50f, category = up, score = .94f)
            )?.signal
        )

        assertNull(
            interpreter.evaluate(
                hand(330, .50f, .50f, category = right, score = .94f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(420, .50f, .50f, category = right, score = .94f)
            )
        )

        val changed =
            interpreter.evaluate(
                hand(520, .50f, .50f, category = right, score = .94f)
            )

        assertEquals(
            GestureSignal.NAV_SCROLL_RIGHT,
            changed?.signal
        )
    }

    @Test
    fun moderateConfidenceNavigationOwnsHandBeforeBroadSwipeCanFire() {
        val interpreter = GestureInterpreter()
        val nav =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_RIGHT

        assertNull(
            interpreter.evaluate(
                hand(0, .30f, .50f, category = nav, score = .68f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(100, .38f, .50f, category = nav, score = .68f)
            )
        )
        assertNull(
            interpreter.evaluate(
                hand(180, .50f, .50f, category = nav, score = .68f)
            )
        )

        // Crossing the broad-swipe axis while a credible navigation pose
        // owns the hand must not leak a SWIPE_* event.
        val decision =
            interpreter.evaluate(
                hand(280, .68f, .50f, category = nav, score = .68f)
            )

        assertTrue(
            decision == null ||
                decision.signal ==
                    GestureSignal.NAV_SCROLL_RIGHT
        )
    }

    @Test
    fun credibleStaticPoseInterruptsOpenPalmGrace() {
        val interpreter =
            GestureInterpreter(
                signalEnabled = { true }
            )

        interpreter.evaluate(
            palm(0, .50f, .50f, confidence = .90f)
        )
        interpreter.evaluate(
            palm(100, .50f, .50f, confidence = .90f)
        )

        // A credible Victory frame must own the hand immediately instead of
        // being swallowed by stale Open Palm grace.
        assertNull(
            interpreter.evaluate(
                hand(
                    180,
                    .50f,
                    .50f,
                    category = "Victory",
                    score = .66f
                )
            )
        )
        assertTrue(
            interpreter.telemetry().state != "PALM_HOLD"
        )
    }

    @Test
    fun navigationModeClearsPreexistingCenterGateHistory() {
        val interpreter = GestureInterpreter()
        val nav =
            LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_UP

        interpreter.evaluate(
            hand(0, .30f, .50f)
        )
        interpreter.evaluate(
            hand(100, .34f, .50f)
        )
        interpreter.evaluate(
            hand(170, .44f, .50f)
        )

        // Navigation takes ownership before the hand reaches the opposite
        // center-line side, which must discard that partial swipe history.
        interpreter.evaluate(
            hand(240, .50f, .50f, category = nav, score = .94f)
        )
        interpreter.evaluate(
            hand(320, .62f, .50f, category = nav, score = .94f)
        )

        assertNull(
            interpreter.evaluate(
                hand(430, .68f, .50f)
            )
        )
    }


    @Test
    fun briefHandLossDoesNotRequireFullPresenceRearm() {
        val interpreter =
            GestureInterpreter(
                missingGraceMs = 160L
            )

        interpreter.evaluate(
            hand(0, .50f, .50f)
        )
        interpreter.evaluate(
            hand(100, .50f, .50f)
        )
        interpreter.evaluate(
            hand(180, .50f, .50f)
        )

        assertNull(
            interpreter.evaluate(
                HandFrame(
                    timestampMs = 250,
                    handPresent = false
                )
            )
        )
        assertEquals(
            "COAST",
            interpreter.telemetry().state
        )

        assertNull(
            interpreter.evaluate(
                hand(300, .50f, .50f)
            )
        )
        assertTrue(
            interpreter.telemetry().state !=
                "PRESENCE"
        )
    }

    @Test
    fun briefMissingFrameCanBridgeRealSwipe() {
        val interpreter =
            GestureInterpreter(
                missingGraceMs = 180L
            )

        interpreter.evaluate(
            hand(0, .30f, .50f)
        )
        interpreter.evaluate(
            hand(100, .30f, .50f)
        )
        interpreter.evaluate(
            hand(180, .48f, .50f)
        )

        assertNull(
            interpreter.evaluate(
                HandFrame(
                    timestampMs = 230,
                    handPresent = false
                )
            )
        )

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            interpreter.evaluate(
                hand(280, .68f, .50f)
            )?.signal
        )
    }

    @Test
    fun fastSmallHandSwipeSurvivesSelectorContinuityAndBriefDropout() {
        val selector =
            ControlHandSelector(
                lostResetMs = 500L
            )
        val interpreter =
            GestureInterpreter(
                missingGraceMs = 220L
            )

        fun candidate(
            index: Int,
            x: Float
        ) =
            HandCandidate(
                index = index,
                centerX = x,
                centerY = .50f,
                scale = .05f,
                handedness = "Left",
                handednessScore = .92f
            )

        fun feed(
            time: Long,
            x: Float,
            index: Int
        ): GestureDecision? {
            val selected =
                selector.select(
                    time,
                    listOf(candidate(index, x))
                ) ?: return interpreter.evaluate(
                    HandFrame(
                        timestampMs = time,
                        handPresent = false
                    )
                )

            if (!selected.continuityLocked) {
                interpreter.resetForControlHandChange()
            }

            return interpreter.evaluate(
                hand(
                    time = time,
                    x = x,
                    y = .50f,
                    scale = .05f
                )
            )
        }

        assertNull(feed(0, .30f, 0))
        assertNull(feed(80, .42f, 1))

        assertNull(
            selector.select(
                140,
                emptyList()
            )
        )
        assertNull(
            interpreter.evaluate(
                HandFrame(
                    timestampMs = 140,
                    handPresent = false
                )
            )
        )

        assertEquals(
            GestureSignal.SWIPE_LEFT,
            feed(220, .62f, 0)?.signal
        )
    }

    @Test
    fun sustainedMissingHandStillBreaksPartialSwipe() {
        val interpreter =
            GestureInterpreter(
                missingGraceMs = 180L
            )

        interpreter.evaluate(
            hand(0, .30f, .50f)
        )
        interpreter.evaluate(
            hand(100, .30f, .50f)
        )
        interpreter.evaluate(
            hand(180, .48f, .50f)
        )

        assertNull(
            interpreter.evaluate(
                HandFrame(
                    timestampMs = 420,
                    handPresent = false
                )
            )
        )

        assertNull(
            interpreter.evaluate(
                hand(500, .68f, .50f)
            )
        )
        assertEquals(
            "PRESENCE",
            interpreter.telemetry().state
        )
    }

    @Test
    fun invalidGeometryFailsClosedWithoutPoisoningFilter() {
        val interpreter = GestureInterpreter()

        interpreter.evaluate(
            hand(0, .40f, .50f)
        )
        interpreter.evaluate(
            hand(100, .40f, .50f)
        )

        assertNull(
            interpreter.evaluate(
                hand(
                    150,
                    Float.NaN,
                    .50f
                )
            )
        )
        assertEquals(
            "INVALID_FRAME",
            interpreter.telemetry().state
        )

        assertNull(
            interpreter.evaluate(
                hand(220, .42f, .50f)
            )
        )
        assertTrue(
            interpreter.telemetry()
                .smoothedX
                .isFinite()
        )
    }

    @Test
    fun weakHeldNavigationCannotAutoRepeat() {
        val interpreter =
            GestureInterpreter(
                navigationRepeatMinScore = .82f
            )
        val category =
            LandmarkPoseRecognizer
                .CATEGORY_DOUBLE_GUN_DOWN

        interpreter.evaluate(
            hand(0, .50f, .50f, category = category, score = .94f)
        )
        interpreter.evaluate(
            hand(100, .50f, .50f, category = category, score = .94f)
        )
        interpreter.evaluate(
            hand(170, .50f, .50f, category = category, score = .94f)
        )

        assertEquals(
            GestureSignal.NAV_SCROLL_DOWN,
            interpreter.evaluate(
                hand(250, .50f, .50f, category = category, score = .94f)
            )?.signal
        )

        assertNull(
            interpreter.evaluate(
                hand(800, .50f, .50f, category = category, score = .62f)
            )
        )
        assertEquals(
            "NAV_HELD_WEAK",
            interpreter.telemetry().state
        )
    }


    @Test
    fun disabledStaticGestureIsCompletelyInert() {
        val interpreter =
            GestureInterpreter(
                signalEnabled = {
                    it != GestureSignal.CLOSED_FIST
                }
            )

        val frames = listOf(
            pose(0, "Closed_Fist", .96f),
            pose(100, "Closed_Fist", .96f),
            pose(250, "Closed_Fist", .96f),
            pose(500, "Closed_Fist", .96f)
        )

        frames.forEach {
            assertNull(interpreter.evaluate(it))
        }

        assertTrue(
            interpreter.telemetry().state !=
                "COMMITTED"
        )
    }

    @Test
    fun disabledOpenPalmNeverCommitsHold() {
        val interpreter =
            GestureInterpreter(
                signalEnabled = {
                    it != GestureSignal.OPEN_PALM
                }
            )

        listOf(
            palm(0, .50f, .50f),
            palm(100, .50f, .50f),
            palm(350, .50f, .50f),
            palm(700, .50f, .50f),
            palm(900, .50f, .50f)
        ).forEach {
            assertNull(interpreter.evaluate(it))
        }

        assertTrue(
            interpreter.telemetry().state !=
                "PALM_HELD"
        )
    }


}
