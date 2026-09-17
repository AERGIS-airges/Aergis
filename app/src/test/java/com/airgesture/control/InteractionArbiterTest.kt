package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionArbiterTest {

    @Test
    fun pointerModeIgnoresPoseClassifierForPressEligibility() {
        for (
            evidence in listOf(
                "Victory" to .98f,
                "Closed_Fist" to .99f,
                LandmarkPoseRecognizer.CATEGORY_OK_SIGN to .94f,
                LandmarkPoseRecognizer.CATEGORY_DOUBLE_GUN_LEFT to .92f
            )
        ) {
            assertTrue(
                InteractionArbiter.pointerPressAllowed(
                    category = evidence.first,
                    categoryScore = evidence.second
                )
            )
        }
    }

    @Test
    fun pointerModeOwnsTheHandAndBlocksGenericGestureExecution() {
        val states =
            listOf(
                "POINTING",
                "POINTING_ARMING",
                "PRESS_CANDIDATE",
                "PRESSING",
                "TAP",
                "LONG_PRESS",
                "COAST"
            )
        val signals =
            listOf(
                GestureSignal.CLOSED_FIST,
                GestureSignal.VICTORY,
                GestureSignal.SWIPE_LEFT,
                GestureSignal.SWIPE_RIGHT,
                GestureSignal.SWIPE_UP,
                GestureSignal.SWIPE_DOWN,
                GestureSignal.POINTING_UP
            )

        for (state in states) {
            for (signal in signals) {
                assertFalse(
                    InteractionArbiter.gestureExecutionAllowed(
                        pointerEnabled = true,
                        pointerState = state,
                        signal = signal
                    )
                )
            }
        }
    }

    @Test
    fun gestureModeStillAllowsMappedGesturesWhenPointerIsDisabled() {
        for (
            signal in listOf(
                GestureSignal.CLOSED_FIST,
                GestureSignal.VICTORY,
                GestureSignal.SWIPE_LEFT,
                GestureSignal.SWIPE_RIGHT,
                GestureSignal.SWIPE_UP,
                GestureSignal.SWIPE_DOWN,
                GestureSignal.POINTING_UP
            )
        ) {
            assertTrue(
                InteractionArbiter.gestureExecutionAllowed(
                    pointerEnabled = false,
                    pointerState = "OFF",
                    signal = signal
                )
            )
        }
    }
}
