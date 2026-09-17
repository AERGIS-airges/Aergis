package com.airgesture.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GestureSignal(val label: String) {
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    OPEN_PALM("Open palm hold"),
    POINTING_UP("Point up"),
    CLOSED_FIST("Closed fist"),
    THUMB_UP("Thumb up"),
    THUMB_DOWN("Thumb down"),
    VICTORY("Victory"),
    I_LOVE_YOU("I love you"),
    L_SHAPE("L / finger gun"),
    FOUR_FINGERS("Four fingers"),
    PINKY_UP("Pinky up"),
    OK_SIGN("OK sign"),
    CALL_ME("Call me"),
    ROCK_SIGN("Rock sign"),
    THREE_FINGERS("Three fingers"),
    NAV_SCROLL_UP("Double gun • scroll up"),
    NAV_SCROLL_DOWN("Double gun • scroll down"),
    NAV_SCROLL_LEFT("Double gun • scroll left"),
    NAV_SCROLL_RIGHT("Double gun • scroll right")
}

enum class AirAction(val label: String) {
    NONE("Do nothing"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recent apps"),
    SELECT("Select / click focused item"),
    FORWARD("Scroll forward"),
    SCROLL_BACKWARD("Scroll backward"),
    COPY("Copy"),
    PASTE("Paste"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    MEDIA_PLAY_PAUSE("Media play / pause"),
    SCROLL_UP("Scroll up a bit"),
    SCROLL_DOWN("Scroll down a bit"),
    SCROLL_LEFT("Scroll left a bit"),
    SCROLL_RIGHT("Scroll right a bit")
}

object DefaultMappings {
    val values: Map<GestureSignal, AirAction> = mapOf(
        GestureSignal.SWIPE_LEFT to AirAction.SCROLL_LEFT,
        GestureSignal.SWIPE_RIGHT to AirAction.SCROLL_RIGHT,
        GestureSignal.SWIPE_UP to AirAction.FORWARD,
        GestureSignal.SWIPE_DOWN to AirAction.SCROLL_BACKWARD,
        GestureSignal.OPEN_PALM to AirAction.NONE,
        GestureSignal.POINTING_UP to AirAction.NONE,
        GestureSignal.CLOSED_FIST to AirAction.NONE,
        GestureSignal.THUMB_UP to AirAction.NONE,
        GestureSignal.THUMB_DOWN to AirAction.NONE,
        GestureSignal.VICTORY to AirAction.NONE,
        GestureSignal.I_LOVE_YOU to AirAction.NONE,
        GestureSignal.L_SHAPE to AirAction.NONE,
        GestureSignal.FOUR_FINGERS to AirAction.NONE,
        GestureSignal.PINKY_UP to AirAction.NONE,
        GestureSignal.OK_SIGN to AirAction.NONE,
        GestureSignal.CALL_ME to AirAction.NONE,
        GestureSignal.ROCK_SIGN to AirAction.NONE,
        GestureSignal.THREE_FINGERS to AirAction.NONE,
        GestureSignal.NAV_SCROLL_UP to AirAction.SCROLL_UP,
        GestureSignal.NAV_SCROLL_DOWN to AirAction.SCROLL_DOWN,
        GestureSignal.NAV_SCROLL_LEFT to AirAction.SCROLL_LEFT,
        GestureSignal.NAV_SCROLL_RIGHT to AirAction.SCROLL_RIGHT
    )
}

data class PoseGeometryTelemetry(
    val worldBacked: Boolean = false,
    val thumbExtensionScore: Float? = null,
    val thumbWorldScore: Float? = null
)

data class RuntimeStatus(
    val accessibilityEnabled: Boolean = false,
    val sessionActive: Boolean = false,
    val cameraReady: Boolean = false,
    val recognizerReady: Boolean = false,
    val lastGesture: GestureSignal? = null,
    val lastAction: AirAction? = null,
    val lastActionSucceeded: Boolean? = null,
    val intentConfidence: Float = 0f,
    val trackingState: String = "WAITING",
    val handScale: Float = 0f,
    val motionSpeedPalmsPerSecond: Float = 0f,
    val lastReasoning: String = "—",
    val controlHandPreference: ControlHandPreference =
        ControlHandPreference.AUTO,
    val controlHandLabel: String = "—",
    val controlHandConfidence: Float = 0f,
    val detectedHandCount: Int = 0,
    val controlHandLocked: Boolean = false,
    val pointerEnabled: Boolean =
        ActionMappingStore.DEFAULT_POINTER_ENABLED,
    val pointerVisible: Boolean = false,
    val pointerX: Float = 0.5f,
    val pointerY: Float = 0.5f,
    val pointerRawX: Float = 0.5f,
    val pointerRawY: Float = 0.5f,
    val pointerRawPresent: Boolean = false,
    val pointerAimX: Float = 0.5f,
    val pointerAimY: Float = 0.5f,
    val pointerAimConfidence: Float = 0f,
    val pointerState: String = "OFF",
    val pointerPressAmount: Float = 0f,
    val pointerConfidence: Float = 0f,
    val pointerReasoning: String = "—",
    val pointerActionPending: Boolean = false,
    val pointerLastActionSucceeded: Boolean? = null,
    val visionSubmittedFps: Float = 0f,
    val visionResultFps: Float = 0f,
    val visionAnalysisToResultMs: Float = 0f,
    val visionResultToPointerApplyMs: Float = 0f,
    val visionSourceToPointerApplyMs: Float = 0f,
    val visionResultToActionDispatchMs: Float = 0f,
    val visionSourceToActionDispatchMs: Float = 0f,
    val visionSourceToActionCompleteMs: Float = 0f,
    val visionResultYieldPercent: Float = 0f,
    val visionThrottlePercent: Float = 0f,
    val visionStaleDropPercent: Float = 0f,
    val visionLastResultAgeMs: Float = 0f,
    val visionInputMode: String = "—",
    val visionDelegate: String = "—",
    val visionFallbackNote: String = "",
    val visionPipelineBenchmarkSummary: String =
        "Pipeline benchmark: no samples",
    val visionRotationDegrees: Int? = null,
    val visionWorldGeometryAvailable: Boolean = false,
    val visionThumbExtensionScore: Float? = null,
    val visionThumbWorldScore: Float? = null,
    val lastMessage: String = "Ready"
)

object AirRuntime {
    private val _state = MutableStateFlow(RuntimeStatus())
    val state = _state.asStateFlow()

    fun update(block: (RuntimeStatus) -> RuntimeStatus) {
        _state.update(block)
    }

    fun clearSessionTransientState(
        message: String? = null
    ) {
        _state.update {
            val cleared = it.copy(
                sessionActive = false,
                cameraReady = false,
                recognizerReady = false,
                intentConfidence = 0f,
                trackingState = "WAITING",
                handScale = 0f,
                motionSpeedPalmsPerSecond = 0f,
                controlHandLabel = "—",
                controlHandConfidence = 0f,
                detectedHandCount = 0,
                controlHandLocked = false,
                lastGesture = null,
                lastAction = null,
                lastActionSucceeded = null,
                lastReasoning = "—",
                pointerVisible = false,
                pointerX = 0.5f,
                pointerY = 0.5f,
                pointerRawX = 0.5f,
                pointerRawY = 0.5f,
                pointerRawPresent = false,
                pointerAimX = 0.5f,
                pointerAimY = 0.5f,
                pointerAimConfidence = 0f,
                pointerState = "OFF",
                pointerPressAmount = 0f,
                pointerConfidence = 0f,
                pointerReasoning = "—",
                pointerActionPending = false,
                pointerLastActionSucceeded = null,
                visionSubmittedFps = 0f,
                visionResultFps = 0f,
                visionAnalysisToResultMs = 0f,
                visionResultToPointerApplyMs = 0f,
                visionSourceToPointerApplyMs = 0f,
                visionResultToActionDispatchMs = 0f,
                visionSourceToActionDispatchMs = 0f,
                visionSourceToActionCompleteMs = 0f,
                visionResultYieldPercent = 0f,
                visionThrottlePercent = 0f,
                visionStaleDropPercent = 0f,
                visionLastResultAgeMs = 0f,
                visionInputMode = "—",
                visionDelegate = "—",
                visionFallbackNote = "",
                visionPipelineBenchmarkSummary =
                    "Pipeline benchmark: no samples",
                visionRotationDegrees = null,
                visionWorldGeometryAvailable = false,
                visionThumbExtensionScore = null,
                visionThumbWorldScore = null
            )

            if (message != null) {
                cleared.copy(lastMessage = message)
            } else {
                cleared
            }
        }
    }

    fun recordTracking(
        telemetry: GestureTelemetry,
        poseGeometry: PoseGeometryTelemetry? = null
    ) {
        _state.update {
            val worldBacked =
                poseGeometry?.worldBacked == true
            it.copy(
                intentConfidence = telemetry.confidence,
                trackingState = telemetry.state,
                handScale = telemetry.handScale,
                motionSpeedPalmsPerSecond =
                    telemetry.speedPalmsPerSecond,
                visionWorldGeometryAvailable =
                    worldBacked,
                visionThumbExtensionScore =
                    poseGeometry
                        ?.thumbExtensionScore
                        ?.takeIf { score ->
                            score.isFinite()
                        }
                        ?.coerceIn(0f, 1f),
                visionThumbWorldScore =
                    if (worldBacked) {
                        poseGeometry
                            ?.thumbWorldScore
                            ?.takeIf { score ->
                                score.isFinite()
                            }
                            ?.coerceIn(0f, 1f)
                    } else {
                        null
                    }
            )
        }
    }

    fun recordControlHand(
        preference: ControlHandPreference,
        selection: ControlHandSelection?,
        detectedCount: Int =
            selection?.detectedCount ?: 0
    ) {
        _state.update {
            it.copy(
                controlHandPreference = preference,
                controlHandLabel =
                    selection?.handedness ?: "—",
                controlHandConfidence =
                    selection?.handednessScore ?: 0f,
                detectedHandCount = detectedCount.coerceAtLeast(0),
                controlHandLocked =
                    selection?.continuityLocked ?: false
            )
        }
    }

    fun recordRawPointer(
        present: Boolean,
        x: Float = 0.5f,
        y: Float = 0.5f
    ) {
        _state.update {
            it.copy(
                pointerRawPresent = present,
                pointerRawX =
                    x.takeIf { it.isFinite() }
                        ?.coerceIn(0f, 1f)
                        ?: it.pointerRawX,
                pointerRawY =
                    y.takeIf { it.isFinite() }
                        ?.coerceIn(0f, 1f)
                        ?: it.pointerRawY
            )
        }
    }

    fun recordPointerAim(
        present: Boolean,
        x: Float = 0.5f,
        y: Float = 0.5f,
        confidence: Float = 0f
    ) {
        _state.update {
            it.copy(
                pointerAimX =
                    if (present) {
                        x.takeIf { it.isFinite() }
                            ?.coerceIn(-0.20f, 1.20f)
                            ?: it.pointerAimX
                    } else {
                        0.5f
                    },
                pointerAimY =
                    if (present) {
                        y.takeIf { it.isFinite() }
                            ?.coerceIn(-0.20f, 1.20f)
                            ?: it.pointerAimY
                    } else {
                        0.5f
                    },
                pointerAimConfidence =
                    if (present) {
                        confidence
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 1f)
                            ?: 0f
                    } else {
                        0f
                    }
            )
        }
    }

    fun recordVisionRotation(
        rotationDegrees: Int
    ) {
        _state.update {
            if (
                it.visionRotationDegrees ==
                    rotationDegrees
            ) {
                it
            } else {
                it.copy(
                    visionRotationDegrees =
                        rotationDegrees
                )
            }
        }
    }

    fun recordVisionFallback(
        note: String
    ) {
        val safe =
            note
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .trim()
                .take(120)
        if (safe.isBlank()) return

        _state.update {
            when {
                it.visionFallbackNote ==
                    safe ->
                    it

                it.visionFallbackNote
                    .isBlank() ->
                    it.copy(
                        visionFallbackNote =
                            safe
                    )

                it.visionFallbackNote
                    .contains(safe) ->
                    it

                else ->
                    it.copy(
                        visionFallbackNote =
                            (
                                it.visionFallbackNote +
                                    " | " +
                                    safe
                                ).take(220)
                    )
            }
        }
    }

    fun recordVisionPipelineBenchmark(
        summary: String
    ) {
        val safe =
            summary
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .take(320)
        _state.update {
            it.copy(
                visionPipelineBenchmarkSummary =
                    safe.ifBlank {
                        "Pipeline benchmark: no samples"
                    }
            )
        }
    }

    fun recordVisionDelegate(
        mode: VisionDelegateMode
    ) {
        _state.update {
            val label =
                when (mode) {
                    VisionDelegateMode.GPU ->
                        "GPU"
                    VisionDelegateMode.CPU ->
                        "CPU"
                }
            if (
                it.visionDelegate ==
                    label
            ) {
                it
            } else {
                it.copy(
                    visionDelegate = label
                )
            }
        }
    }

    fun recordVisionInputMode(
        mode: VisionInputMode
    ) {
        _state.update {
            it.copy(
                visionInputMode =
                    when (mode) {
                        VisionInputMode.DIRECT_MEDIA_IMAGE ->
                            "DIRECT MEDIA"
                        VisionInputMode.BITMAP ->
                            "BITMAP"
                    }
            )
        }
    }

    fun recordVisionPerformance(
        snapshot: VisionPerformanceSnapshot
    ) {
        _state.update {
            it.copy(
                visionSubmittedFps = snapshot.submittedFps,
                visionResultFps = snapshot.resultFps,
                visionAnalysisToResultMs =
                    snapshot.analysisToResultLatencyMs,
                visionResultYieldPercent =
                    snapshot.resultYieldPercent,
                visionThrottlePercent =
                    snapshot.throttlePercent,
                visionStaleDropPercent =
                    snapshot.staleDropPercent,
                visionLastResultAgeMs =
                    snapshot.lastResultAgeMs
            )
        }
    }

    fun recordVisionInteractionLatency(
        resultToPointerApplyMs: Long? = null,
        sourceToPointerApplyMs: Long? = null,
        resultToActionDispatchMs: Long? = null,
        sourceToActionDispatchMs: Long? = null,
        sourceToActionCompleteMs: Long? = null
    ) {
        fun sane(value: Long?): Float? =
            value
                ?.takeIf { it >= 0L }
                ?.coerceAtMost(60_000L)
                ?.toFloat()

        _state.update {
            it.copy(
                visionResultToPointerApplyMs =
                    sane(resultToPointerApplyMs)
                        ?: it.visionResultToPointerApplyMs,
                visionSourceToPointerApplyMs =
                    sane(sourceToPointerApplyMs)
                        ?: it.visionSourceToPointerApplyMs,
                visionResultToActionDispatchMs =
                    sane(resultToActionDispatchMs)
                        ?: it.visionResultToActionDispatchMs,
                visionSourceToActionDispatchMs =
                    sane(sourceToActionDispatchMs)
                        ?: it.visionSourceToActionDispatchMs,
                visionSourceToActionCompleteMs =
                    sane(sourceToActionCompleteMs)
                        ?: it.visionSourceToActionCompleteMs
            )
        }
    }

    fun recordPointer(
        decision: PointerDecision,
        enabled: Boolean
    ) {
        _state.update {
            it.copy(
                pointerEnabled = enabled,
                pointerVisible =
                    enabled &&
                        decision.visible &&
                        decision.x.isFinite() &&
                        decision.y.isFinite(),
                pointerX =
                    decision.x
                        .takeIf { value ->
                            value.isFinite()
                        }
                        ?.coerceIn(0f, 1f)
                        ?: it.pointerX,
                pointerY =
                    decision.y
                        .takeIf { value ->
                            value.isFinite()
                        }
                        ?.coerceIn(0f, 1f)
                        ?: it.pointerY,
                pointerState =
                    if (enabled) decision.state else "OFF",
                pointerPressAmount = decision.pressAmount,
                pointerConfidence = decision.confidence,
                pointerReasoning = decision.reasoning
            )
        }
    }

    fun recordPointerActionPending(
        message: String
    ) {
        _state.update {
            it.copy(
                pointerActionPending = true,
                pointerLastActionSucceeded = null,
                lastMessage = message
            )
        }
    }

    fun recordPointerAction(
        success: Boolean,
        message: String
    ) {
        _state.update {
            it.copy(
                pointerActionPending = false,
                pointerLastActionSucceeded = success,
                lastMessage = message
            )
        }
    }

    fun recordGesture(
        gesture: GestureSignal,
        action: AirAction,
        success: Boolean?,
        message: String,
        confidence: Float,
        reasoning: String
    ) {
        _state.update {
            it.copy(
                lastGesture = gesture,
                lastAction = action,
                lastActionSucceeded = success,
                intentConfidence = confidence,
                lastReasoning = reasoning,
                lastMessage = message
            )
        }
    }
}
