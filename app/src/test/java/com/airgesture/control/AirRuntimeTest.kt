package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AirRuntimeTest {

    @Test
    fun pointerActionIsPendingUntilAndroidConfirmsCompletion() {
        AirRuntime.recordPointerActionPending(
            "Air pointer tap pending"
        )
        var state = AirRuntime.state.value

        assertEquals(true, state.pointerActionPending)
        assertNull(state.pointerLastActionSucceeded)
        assertEquals("Air pointer tap pending", state.lastMessage)

        AirRuntime.recordPointerAction(
            success = true,
            message = "Air pointer tap completed"
        )
        state = AirRuntime.state.value

        assertEquals(false, state.pointerActionPending)
        assertEquals(true, state.pointerLastActionSucceeded)
        assertEquals("Air pointer tap completed", state.lastMessage)
    }

    @Test
    fun sessionTeardownClearsTransientPointerAndVisionState() {
        AirRuntime.update {
            it.copy(
                sessionActive = true,
                cameraReady = true,
                recognizerReady = true,
                trackingState = "TRACKING",
                controlHandLabel = "Right",
                controlHandConfidence = .96f,
                detectedHandCount = 2,
                controlHandLocked = true,
                lastGesture = GestureSignal.CLOSED_FIST,
                lastAction = AirAction.HOME,
                lastActionSucceeded = true,
                lastReasoning = "Old session action",
                pointerVisible = true,
                pointerX = .91f,
                pointerY = .08f,
                pointerRawX = .88f,
                pointerRawY = .12f,
                pointerRawPresent = true,
                pointerAimX = .81f,
                pointerAimY = .16f,
                pointerAimConfidence = .93f,
                pointerState = "LONG_PRESS",
                pointerPressAmount = .31f,
                pointerConfidence = .94f,
                pointerReasoning =
                    "Index long press committed • bend 100%",
                pointerActionPending = true,
                pointerLastActionSucceeded = true,
                visionSubmittedFps = 29f,
                visionResultFps = 24f,
                visionAnalysisToResultMs = 41f,
                visionResultToPointerApplyMs = 6f,
                visionSourceToPointerApplyMs = 47f,
                visionResultToActionDispatchMs = 9f,
                visionSourceToActionDispatchMs = 50f,
                visionSourceToActionCompleteMs = 122f,
                visionResultYieldPercent = 82f,
                visionThrottlePercent = 18f,
                visionWorldGeometryAvailable = true,
                visionThumbExtensionScore = .55f,
                visionThumbWorldScore = .24f
            )
        }

        AirRuntime.clearSessionTransientState("Stopped for test")
        val state = AirRuntime.state.value

        assertFalse(state.sessionActive)
        assertFalse(state.cameraReady)
        assertFalse(state.recognizerReady)
        assertFalse(state.pointerVisible)
        assertFalse(state.pointerRawPresent)
        assertEquals(.5f, state.pointerAimX, .001f)
        assertEquals(.5f, state.pointerAimY, .001f)
        assertEquals(0f, state.pointerAimConfidence, .001f)
        assertEquals("—", state.controlHandLabel)
        assertEquals(0f, state.controlHandConfidence, .001f)
        assertEquals(0, state.detectedHandCount)
        assertFalse(state.controlHandLocked)
        assertNull(state.lastGesture)
        assertNull(state.lastAction)
        assertNull(state.lastActionSucceeded)
        assertEquals("—", state.lastReasoning)
        assertEquals(.5f, state.pointerX, .001f)
        assertEquals(.5f, state.pointerY, .001f)
        assertEquals(.5f, state.pointerRawX, .001f)
        assertEquals(.5f, state.pointerRawY, .001f)
        assertEquals("OFF", state.pointerState)
        assertEquals(0f, state.pointerPressAmount, .001f)
        assertEquals(0f, state.pointerConfidence, .001f)
        assertEquals("—", state.pointerReasoning)
        assertEquals(0f, state.visionSubmittedFps, .001f)
        assertEquals(0f, state.visionAnalysisToResultMs, .001f)
        assertEquals(0f, state.visionResultToPointerApplyMs, .001f)
        assertEquals(0f, state.visionSourceToPointerApplyMs, .001f)
        assertEquals(0f, state.visionResultToActionDispatchMs, .001f)
        assertEquals(0f, state.visionSourceToActionDispatchMs, .001f)
        assertEquals(0f, state.visionSourceToActionCompleteMs, .001f)
        assertFalse(state.visionWorldGeometryAvailable)
        assertNull(state.visionThumbExtensionScore)
        assertNull(state.visionThumbWorldScore)
        assertFalse(state.pointerActionPending)
        assertNull(state.pointerLastActionSucceeded)
        assertEquals("Stopped for test", state.lastMessage)
    }
    @Test
    fun trackingPublishesAndClearsPoseGeometryDiagnostics() {
        AirRuntime.recordTracking(
            telemetry =
                GestureTelemetry(
                    state = "TRACKING",
                    confidence = .81f
                ),
            poseGeometry =
                PoseGeometryTelemetry(
                    worldBacked = true,
                    thumbExtensionScore = .55f,
                    thumbWorldScore = .24f
                )
        )

        var state = AirRuntime.state.value
        assertEquals(true, state.visionWorldGeometryAvailable)
        assertEquals(.55f, state.visionThumbExtensionScore ?: 0f, .001f)
        assertEquals(.24f, state.visionThumbWorldScore ?: 0f, .001f)

        AirRuntime.recordTracking(
            GestureTelemetry(state = "WAITING")
        )

        state = AirRuntime.state.value
        assertFalse(state.visionWorldGeometryAvailable)
        assertNull(state.visionThumbExtensionScore)
        assertNull(state.visionThumbWorldScore)
    }

    @Test
    fun interactionLatencyStagesAreRecordedWithoutRelabelingInference() {
        AirRuntime.clearSessionTransientState()
        AirRuntime.recordVisionInteractionLatency(
            resultToPointerApplyMs = 7L,
            sourceToPointerApplyMs = 52L,
            resultToActionDispatchMs = 11L,
            sourceToActionDispatchMs = 56L,
            sourceToActionCompleteMs = 130L
        )

        val state = AirRuntime.state.value
        assertEquals(7f, state.visionResultToPointerApplyMs, .001f)
        assertEquals(52f, state.visionSourceToPointerApplyMs, .001f)
        assertEquals(11f, state.visionResultToActionDispatchMs, .001f)
        assertEquals(56f, state.visionSourceToActionDispatchMs, .001f)
        assertEquals(130f, state.visionSourceToActionCompleteMs, .001f)
    }

}
