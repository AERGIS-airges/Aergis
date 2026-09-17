package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

data class HandFrame(
    val timestampMs: Long,
    val handPresent: Boolean,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val handScale: Float = 0.12f,
    val category: String = "None",
    val categoryScore: Float = 0f,
    val openPalmScore: Float = 0f
)

data class GestureDecision(
    val signal: GestureSignal,
    val confidence: Float,
    val reasoning: String
)

data class GestureTelemetry(
    val state: String = "WAITING",
    val confidence: Float = 0f,
    val handScale: Float = 0f,
    val speedPalmsPerSecond: Float = 0f,
    val smoothedX: Float = 0.5f,
    val smoothedY: Float = 0.5f
)

class GestureInterpreter(
    private val presenceArmMs: Long = 70L,
    private val openPalmGraceMs: Long = 320L,
    private val openPalmHoldMs: Long = 520L,
    private val openPalmHoldMaxDisplacement: Float = 0.28f,
    private val staticWindowMs: Long = 520L,
    private val staticHoldMs: Long = 180L,
    private val staticCooldownMs: Long = 420L,
    private val navigationHoldMs: Long = 100L,
    private val navigationCooldownMs: Long = 220L,
    private val navigationRepeatMs: Long = 440L,
    private val navigationRepeatMinScore: Float = 0.72f,
    private val missingGraceMs: Long = 220L,
    private val staticCandidateGapMs: Long = 280L,
    private val staticReleaseMs: Long = 220L,
    private val globalCooldownMs: Long = 180L,
    private val signalEnabled: (GestureSignal) -> Boolean = {
        true
    }
) {
    private data class StaticSample(
        val timeMs: Long,
        val signal: GestureSignal,
        val score: Float
    )

    private val staticSamples = ArrayDeque<StaticSample>()
    private val centerGateSwipeDetector =
        CenterGateSwipeDetector(
            signalEnabled = signalEnabled
        )

    private var presenceSinceMs: Long? = null
    private var lastPresentMs: Long? = null
    private var lastOpenPalmSeenMs: Long? = null

    private var openPalmHoldSinceMs: Long? = null
    private var openPalmHoldAnchorX = 0.5f
    private var openPalmHoldAnchorY = 0.5f
    private var openPalmHoldAnchorScale = 0.12f
    private var openPalmHoldSuppressed = false
    private var openPalmHoldLatched = false

    private var filteredX: Float? = null
    private var filteredY: Float? = null
    private var filteredScale: Float? = null
    private var lastFilterMs: Long? = null

    private var latchedStatic: GestureSignal? = null
    private var latchedStaticLastSeenMs = 0L
    private var staticCandidateSignal: GestureSignal? = null
    private var staticCandidateSinceMs: Long? = null
    private var staticCandidateLastSeenMs: Long? = null

    private var lastStaticEmitMs = Long.MIN_VALUE / 4
    private var lastNavigationEmitMs = Long.MIN_VALUE / 4
    private var lastAnyEmitMs = Long.MIN_VALUE / 4

    private val navigationSignals =
        setOf(
            GestureSignal.NAV_SCROLL_UP,
            GestureSignal.NAV_SCROLL_DOWN,
            GestureSignal.NAV_SCROLL_LEFT,
            GestureSignal.NAV_SCROLL_RIGHT
        )

    private var telemetry = GestureTelemetry()

    fun update(frame: HandFrame): GestureSignal? =
        evaluate(frame)?.signal

    fun evaluate(frame: HandFrame): GestureDecision? {
        val now = frame.timestampMs

        if (!frame.handPresent) {
            return handleMissingFrame(
                now = now,
                state = "COAST"
            )
        }

        if (!isValidFrame(frame)) {
            return handleMissingFrame(
                now = now,
                state = "INVALID_FRAME"
            )
        }

        lastPresentMs = now
        val filtered = filter(frame)

        if (presenceSinceMs == null) {
            presenceSinceMs = now
            telemetry = telemetry.copy(state = "PRESENCE")
            return null
        }

        if (now - (presenceSinceMs ?: now) < presenceArmMs) {
            telemetry = telemetry.copy(state = "PRESENCE")
            return null
        }

        val staticSignal =
            categoryToStaticSignal(
                category = frame.category,
                score = frame.categoryScore
            )
        expireLatchedStaticIfNeeded(
            now = now,
            currentSignal = staticSignal,
            currentScore = frame.categoryScore
        )

        val navigationPoseActive =
            (
                staticSignal in navigationSignals &&
                    frame.categoryScore >= 0.64f
                ) ||
                latchedStatic in navigationSignals

        if (navigationPoseActive) {
            // Navigation owns the hand. Do not preserve pre-navigation
            // center-line motion that could fire after the pose is released.
            centerGateSwipeDetector.reset()
        }

        val gateDecision =
            if (
                !navigationPoseActive &&
                now - lastAnyEmitMs >=
                    globalCooldownMs
            ) {
                centerGateSwipeDetector.update(
                    now = now,
                    x = filtered.first,
                    y = filtered.second,
                    scale = filtered.third
                )
            } else {
                null
            }

        val gateTelemetry =
            centerGateSwipeDetector.telemetry()

        if (gateDecision != null) {
            staticSamples.clear()
            clearStaticCandidate()
            latchedStatic = null
            suppressOpenPalmHold()
            lastAnyEmitMs = now

            telemetry = telemetry.copy(
                state = gateTelemetry.state,
                confidence = gateDecision.confidence,
                speedPalmsPerSecond =
                    gateTelemetry.speedPalmsPerSecond
            )
            return gateDecision
        }

        val openPalmConfidence = max(
            frame.openPalmScore,
            if (frame.category == "Open_Palm") {
                frame.categoryScore
            } else {
                0f
            }
        )
        val openPalmFresh =
            signalEnabled(
                GestureSignal.OPEN_PALM
            ) &&
                openPalmConfidence >= 0.36f
        if (openPalmFresh) {
            lastOpenPalmSeenMs = now
        }

        val openPalmIntentActive =
            openPalmHoldSinceMs != null ||
                openPalmHoldLatched ||
                openPalmHoldSuppressed
        val palmGraceActive =
            lastOpenPalmSeenMs?.let {
                now - it <= openPalmGraceMs
            } == true
        val strongConflictingStatic =
            staticSignal != null &&
                frame.categoryScore >= 0.60f

        if (
            (openPalmFresh && !strongConflictingStatic) ||
            (
                openPalmIntentActive &&
                    palmGraceActive &&
                    !strongConflictingStatic
                )
        ) {
            updateStaticRelease(now)
            pruneStaticSamples(now)

            if (openPalmHoldLatched) {
                telemetry = telemetry.copy(
                    state = "PALM_HELD",
                    confidence =
                        openPalmConfidence.coerceIn(0f, 1f),
                    speedPalmsPerSecond =
                        gateTelemetry.speedPalmsPerSecond
                )
                return null
            }

            if (openPalmFresh) {
                val palmDecision =
                    updateOpenPalmHold(
                        now = now,
                        x = filtered.first,
                        y = filtered.second,
                        scale = filtered.third,
                        openPalmConfidence =
                            openPalmConfidence
                    )
                if (palmDecision != null) {
                    return palmDecision
                }
            }

            if (
                openPalmHoldSinceMs != null &&
                !openPalmHoldSuppressed
            ) {
                telemetry = telemetry.copy(
                    state = "PALM_HOLD",
                    confidence =
                        openPalmConfidence.coerceIn(0f, 1f),
                    speedPalmsPerSecond =
                        gateTelemetry.speedPalmsPerSecond
                )
            }
            return null
        }

        if (staticSignal != null) {
            resetOpenPalmIntent()
            val staticDecision =
                updateStatic(
                    now = now,
                    signal = staticSignal,
                    score = frame.categoryScore
                )
            if (staticDecision != null) {
                return staticDecision
            }
            return null
        }

        updateStaticRelease(now)
        pruneStaticSamples(now)
        resetOpenPalmIntent()

        telemetry = telemetry.copy(
            state =
                if (gateTelemetry.state == "GATE_TRACKING") {
                    "GATE_TRACKING"
                } else {
                    "TRACKING"
                },
            confidence =
                frame.categoryScore.coerceIn(0f, 1f),
            speedPalmsPerSecond =
                gateTelemetry.speedPalmsPerSecond
        )
        return null
    }

    fun telemetry(): GestureTelemetry = telemetry

    fun resetForControlHandChange() {
        resetPresenceState()
        telemetry = GestureTelemetry(state = "WAITING")
    }

    private fun isValidFrame(
        frame: HandFrame
    ): Boolean =
        frame.centerX.isFinite() &&
            frame.centerY.isFinite() &&
            frame.handScale.isFinite() &&
            frame.handScale > 0f &&
            frame.categoryScore.isFinite() &&
            frame.openPalmScore.isFinite()

    private fun handleMissingFrame(
        now: Long,
        state: String
    ): GestureDecision? {
        val lastPresent = lastPresentMs
        val withinGrace =
            lastPresent != null &&
                now >= lastPresent &&
                now - lastPresent <= missingGraceMs

        if (withinGrace) {
            // A brief MediaPipe miss is extremely common during motion blur.
            // Preserve swipe history, static consensus and the adaptive
            // position filter so one dropped result does not erase a real
            // gesture. The next valid sample still passes through the swipe
            // detector's screen-speed/glitch checks before it can commit.
            telemetry = telemetry.copy(
                state = state,
                confidence = 0f,
                speedPalmsPerSecond = 0f
            )
            return null
        }

        // Only a sustained loss breaks gesture continuity.
        resetPresenceState()
        telemetry = GestureTelemetry(state = "WAITING")
        return null
    }

    private fun expireLatchedStaticIfNeeded(
        now: Long,
        currentSignal: GestureSignal?,
        currentScore: Float
    ) {
        val latched = latchedStatic ?: return
        // If the classifier still resolves to the same signal, keep
        // ownership even when confidence is temporarily too weak to repeat.
        // categoryToStaticSignal() has already enforced the minimum usable
        // recognition score. Releasing here on a higher retain threshold
        // caused weak held navigation to fall back into a fresh candidate
        // state instead of remaining safely latched-but-nonrepeating.
        if (
            currentSignal == latched &&
            currentScore.isFinite()
        ) {
            return
        }

        if (
            now - latchedStaticLastSeenMs >
            staticReleaseMs
        ) {
            latchedStatic = null
        }
    }

    private fun clearStaticCandidate() {
        staticCandidateSignal = null
        staticCandidateSinceMs = null
        staticCandidateLastSeenMs = null
    }

    private fun filter(
        frame: HandFrame
    ): Triple<Float, Float, Float> {
        val previousX = filteredX
        val previousY = filteredY
        val previousScale = filteredScale
        val previousTime = lastFilterMs

        if (
            previousX == null ||
            previousY == null ||
            previousScale == null ||
            previousTime == null
        ) {
            filteredX = frame.centerX
            filteredY = frame.centerY
            filteredScale =
                frame.handScale.coerceIn(0.03f, 0.45f)
            lastFilterMs = frame.timestampMs

            telemetry = telemetry.copy(
                handScale = filteredScale!!,
                smoothedX = filteredX!!,
                smoothedY = filteredY!!
            )
            return Triple(
                filteredX!!,
                filteredY!!,
                filteredScale!!
            )
        }

        val dtSeconds =
            (
                (frame.timestampMs - previousTime)
                    .coerceAtLeast(1L) /
                    1000f
                )
        val screenSpeed =
            hypot(
                frame.centerX - previousX,
                frame.centerY - previousY
            ) / dtSeconds

        // Screen-space speed avoids a major old bias: the same physical
        // movement looked artificially "faster" when the hand was farther
        // away and therefore smaller. Slow motion is stabilized; real swipes
        // rapidly approach the raw landmark position with little lag.
        val positionAlpha =
            (
                0.30f +
                    min(
                        screenSpeed / 2.25f,
                        0.62f
                    )
                ).coerceIn(0.30f, 0.92f)
        val scaleAlpha = 0.24f

        val x =
            previousX +
                (frame.centerX - previousX) *
                positionAlpha
        val y =
            previousY +
                (frame.centerY - previousY) *
                positionAlpha
        val newScale =
            previousScale +
                (
                    frame.handScale.coerceIn(
                        0.03f,
                        0.45f
                    ) - previousScale
                    ) * scaleAlpha

        filteredX = x
        filteredY = y
        filteredScale = newScale
        lastFilterMs = frame.timestampMs

        telemetry = telemetry.copy(
            handScale = newScale,
            smoothedX = x,
            smoothedY = y
        )
        return Triple(x, y, newScale)
    }

    private fun updateOpenPalmHold(
        now: Long,
        x: Float,
        y: Float,
        scale: Float,
        openPalmConfidence: Float
    ): GestureDecision? {
        if (
            openPalmHoldSuppressed ||
            openPalmHoldLatched
        ) {
            return null
        }

        val holdStart = openPalmHoldSinceMs
        if (holdStart == null) {
            openPalmHoldSinceMs = now
            openPalmHoldAnchorX = x
            openPalmHoldAnchorY = y
            openPalmHoldAnchorScale = scale
            return null
        }

        val driftPalms =
            hypot(
                x - openPalmHoldAnchorX,
                y - openPalmHoldAnchorY
            ) / max(openPalmHoldAnchorScale, 0.04f)

        if (
            driftPalms >
            openPalmHoldMaxDisplacement
        ) {
            suppressOpenPalmHold()
            return null
        }

        if (now - holdStart < openPalmHoldMs) {
            return null
        }

        val cooledDown =
            now - lastStaticEmitMs >= staticCooldownMs &&
                now - lastAnyEmitMs >= globalCooldownMs
        if (!cooledDown) {
            return null
        }

        val stability =
            (
                1f -
                    driftPalms /
                    openPalmHoldMaxDisplacement
                        .coerceAtLeast(0.01f)
                ).coerceIn(0f, 1f)
        val confidence =
            (
                openPalmConfidence
                    .coerceIn(0f, 1f) * 0.82f +
                    stability * 0.18f
                ).coerceIn(0f, 1f)

        openPalmHoldLatched = true
        openPalmHoldSinceMs = null
        lastStaticEmitMs = now
        lastAnyEmitMs = now

        telemetry = telemetry.copy(
            state = "COMMITTED",
            confidence = confidence,
            speedPalmsPerSecond = 0f
        )

        return GestureDecision(
            signal = GestureSignal.OPEN_PALM,
            confidence = confidence,
            reasoning =
                "${GestureSignal.OPEN_PALM.label} • " +
                    "${(confidence * 100).toInt()}% intent • " +
                    "${now - holdStart} ms steady • " +
                    String.format(
                        Locale.US,
                        "%.2f palms drift",
                        driftPalms
                    )
        )
    }

    private fun updateStatic(
        now: Long,
        signal: GestureSignal,
        score: Float
    ): GestureDecision? {
        val previousCandidate =
            staticCandidateSignal
        val previousCandidateSeen =
            staticCandidateLastSeenMs

        if (
            previousCandidate != signal ||
            previousCandidateSeen == null ||
            now - previousCandidateSeen >
                staticCandidateGapMs
        ) {
            staticCandidateSignal = signal
            staticCandidateSinceMs = now
        }
        staticCandidateLastSeenMs = now

        if (
            latchedStatic in navigationSignals &&
            signal in navigationSignals &&
            latchedStatic != signal
        ) {
            // Direction changes are part of the same navigation pose. Drop
            // old directional votes so the new axis does not wait for the
            // previous direction to age out of the static consensus window.
            staticSamples.clear()
            latchedStatic = null
            latchedStaticLastSeenMs = 0L
            staticCandidateSignal = signal
            staticCandidateSinceMs = now
            staticCandidateLastSeenMs = now
        }

        staticSamples.addLast(
            StaticSample(
                timeMs = now,
                signal = signal,
                score = score.coerceIn(0f, 1f)
            )
        )
        pruneStaticSamples(now)

        if (latchedStatic == signal) {
            val retainScore =
                if (signal in navigationSignals) {
                    0.60f
                } else {
                    0.50f
                }

            if (score >= retainScore) {
                latchedStaticLastSeenMs = now
            }

            if (
                signal in navigationSignals &&
                score >= navigationRepeatMinScore &&
                now - lastNavigationEmitMs >=
                    navigationRepeatMs &&
                now - lastAnyEmitMs >=
                    globalCooldownMs
            ) {
                lastNavigationEmitMs = now
                lastStaticEmitMs = now
                lastAnyEmitMs = now

                telemetry = telemetry.copy(
                    state = "NAV_REPEAT",
                    confidence = score,
                    speedPalmsPerSecond = 0f
                )

                return GestureDecision(
                    signal = signal,
                    confidence =
                        score.coerceIn(0f, 1f),
                    reasoning =
                        "${signal.label} • sustained high-confidence navigation hold"
                )
            }

            telemetry = telemetry.copy(
                state =
                    if (signal in navigationSignals) {
                        if (
                            score >=
                            navigationRepeatMinScore
                        ) {
                            "NAV_HELD"
                        } else {
                            "NAV_HELD_WEAK"
                        }
                    } else {
                        "HELD"
                    },
                confidence = score
            )
            return null
        }

        val relevant = staticSamples.toList()
        if (relevant.size < 3) {
            telemetry = telemetry.copy(
                state = "STATIC",
                confidence = score
            )
            return null
        }

        val firstSeen =
            staticCandidateSinceMs
                ?.takeIf {
                    staticCandidateSignal == signal
                }
                ?: now

        val requiredHoldMs =
            if (signal in navigationSignals) {
                navigationHoldMs
            } else {
                staticHoldMs
            }

        if (now - firstSeen < requiredHoldMs) {
            telemetry = telemetry.copy(
                state =
                    if (signal in navigationSignals) {
                        "NAV_ARMING"
                    } else {
                        "STATIC"
                    },
                confidence = score
            )
            return null
        }

        val totals =
            relevant
                .groupBy { it.signal }
                .mapValues { (_, samples) ->
                    samples
                        .sumOf {
                            it.score.toDouble()
                        }
                        .toFloat()
                }

        val candidateScore =
            totals[signal] ?: 0f
        val totalScore =
            totals.values.sum()
                .coerceAtLeast(0.001f)
        val support =
            candidateScore / totalScore
        val averageConfidence =
            relevant
                .filter { it.signal == signal }
                .map { it.score }
                .average()
                .toFloat()

        val runnerUp =
            totals
                .filterKeys { it != signal }
                .values
                .maxOrNull()
                ?: 0f
        val dominance =
            (candidateScore - runnerUp) /
                totalScore

        val confidence =
            (
                support * 0.42f +
                    averageConfidence * 0.43f +
                    dominance.coerceIn(0f, 1f) *
                    0.15f
                ).coerceIn(0f, 1f)

        telemetry = telemetry.copy(
            state = "STATIC",
            confidence = confidence,
            speedPalmsPerSecond = 0f
        )

        val requiredCooldownMs =
            if (signal in navigationSignals) {
                navigationCooldownMs
            } else {
                staticCooldownMs
            }
        val requiredAverageConfidence =
            if (signal in navigationSignals) {
                0.60f
            } else {
                0.56f
            }
        val requiredConfidence =
            if (signal in navigationSignals) {
                0.64f
            } else {
                0.62f
            }

        val cooledDown =
            now - lastStaticEmitMs >=
                requiredCooldownMs &&
                now - lastAnyEmitMs >=
                globalCooldownMs

        if (
            support < 0.64f ||
            averageConfidence <
                requiredAverageConfidence ||
            dominance < 0.18f ||
            confidence < requiredConfidence ||
            !cooledDown
        ) {
            return null
        }

        latchedStatic = signal
        latchedStaticLastSeenMs = now
        lastStaticEmitMs = now
        if (signal in navigationSignals) {
            lastNavigationEmitMs = now
        }
        lastAnyEmitMs = now

        val reason =
            "${signal.label} • " +
                "${(confidence * 100).toInt()}% intent • " +
                "${(support * 100).toInt()}% consensus • " +
                "${(averageConfidence * 100).toInt()}% model"

        telemetry = telemetry.copy(
            state =
                if (signal in navigationSignals) {
                    "NAV_COMMITTED"
                } else {
                    "COMMITTED"
                },
            confidence = confidence
        )

        return GestureDecision(
            signal = signal,
            confidence = confidence,
            reasoning = reason
        )
    }

    private fun updateStaticRelease(
        now: Long
    ) {
        val latched = latchedStatic ?: return
        val recentlySeen =
            staticSamples.any {
                it.signal == latched &&
                    now - it.timeMs <= staticReleaseMs
            }

        if (recentlySeen) {
            latchedStaticLastSeenMs = now
        } else if (
            now - latchedStaticLastSeenMs >
            staticReleaseMs
        ) {
            latchedStatic = null
        }
    }

    private fun pruneStaticSamples(
        now: Long
    ) {
        while (
            staticSamples.isNotEmpty() &&
            now - staticSamples.first().timeMs >
            staticWindowMs
        ) {
            staticSamples.removeFirst()
        }
    }

    private fun categoryToStaticSignal(
        category: String,
        score: Float
    ): GestureSignal? {
        // Open palm owns a dedicated hold path with motion suppression and
        // grace handling. Feeding it into generic static consensus would
        // make the same pose compete with itself and block the hold path.
        if (category == "Open_Palm") {
            return null
        }

        if (score < 0.38f) {
            return null
        }

        val signal =
            when (category) {
                "Pointing_Up" ->
                    GestureSignal.POINTING_UP
                "Closed_Fist" ->
                    GestureSignal.CLOSED_FIST
                "Thumb_Up" ->
                    GestureSignal.THUMB_UP
                "Thumb_Down" ->
                    GestureSignal.THUMB_DOWN
                "Victory" ->
                    GestureSignal.VICTORY
                "ILoveYou" ->
                    GestureSignal.I_LOVE_YOU
                else ->
                    LandmarkPoseRecognizer
                        .signalForCategory(category)
            }

        return signal?.takeIf(signalEnabled)
    }

    private fun suppressOpenPalmHold() {
        openPalmHoldSinceMs = null
        openPalmHoldSuppressed = true
    }

    private fun resetOpenPalmIntent() {
        lastOpenPalmSeenMs = null
        openPalmHoldSinceMs = null
        openPalmHoldSuppressed = false
        openPalmHoldLatched = false
    }

    private fun resetPresenceState() {
        staticSamples.clear()
        centerGateSwipeDetector.reset()
        presenceSinceMs = null
        lastPresentMs = null
        lastOpenPalmSeenMs = null
        openPalmHoldSinceMs = null
        openPalmHoldSuppressed = false
        openPalmHoldLatched = false
        latchedStatic = null
        clearStaticCandidate()
        lastNavigationEmitMs =
            Long.MIN_VALUE / 4
        filteredX = null
        filteredY = null
        filteredScale = null
        lastFilterMs = null
    }
}
