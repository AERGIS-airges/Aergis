package com.airgesture.control

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class CenterGateTelemetry(
    val state: String = "GATE_READY",
    val speedPalmsPerSecond: Float = 0f,
    val confidence: Float = 0f
)

/**
 * Broad-swipe detector tuned for real front-camera motion rather than
 * classifier pose labels.
 *
 * Important coordinate convention:
 * GestureCaptureService mirrors the front camera before MediaPipe sees it.
 * Therefore horizontal intent is reported from the user's physical
 * perspective: increasing mirrored-image X is SWIPE_LEFT and decreasing X is
 * SWIPE_RIGHT. This matches the supplied demonstration video.
 *
 * Recognition is intentionally tolerant of natural arcs and brief landmark
 * noise. A center-axis crossing is strong evidence, but a sufficiently large
 * dominant-axis stroke can still count when camera framing makes the exact
 * center line miss by a small amount.
 */
class CenterGateSwipeDetector(
    private val centerX: Float = 0.5f,
    private val centerY: Float = 0.5f,
    private val gateHalfWidth: Float = 0.04f,
    private val windowMs: Long = 720L,
    private val cooldownMs: Long = 220L,
    private val minCrossDurationMs: Long = 40L,
    private val minScreenDisplacement: Float = 0.09f,
    private val minFreeStrokeDisplacement: Float = 0.22f,
    private val minFreeStrokeStartDistanceFromCenter: Float = 0.12f,
    private val minScreenSpeedPerSecond: Float = 0.50f,
    private val maxInstantaneousScreenSpeedPerSecond: Float = 8.5f,
    private val minAxisEvidence: Float = 0.56f,
    private val simultaneousAxisAmbiguityMargin: Float = 0.055f,
    private val signalEnabled: (GestureSignal) -> Boolean = {
        true
    }
) {
    private enum class RearmSide {
        LOW,
        HIGH
    }

    private data class Point(
        val timeMs: Long,
        val x: Float,
        val y: Float,
        val scale: Float
    )

    private data class Candidate(
        val signal: GestureSignal,
        val confidence: Float,
        val speedPalmsPerSecond: Float,
        val primary: Float,
        val secondary: Float,
        val crossedCenter: Boolean
    ) {
        val axisEvidence: Float
            get() =
                primary /
                    max(primary + secondary, 0.0001f)
    }

    private val points = ArrayDeque<Point>()

    private var lastPoint: Point? = null
    private var lastEmitMs = Long.MIN_VALUE / 4
    private var horizontalRearmSide: RearmSide? = null
    private var verticalRearmSide: RearmSide? = null
    private var horizontalEligibleSinceMs = Long.MIN_VALUE / 4
    private var verticalEligibleSinceMs = Long.MIN_VALUE / 4

    private var telemetry = CenterGateTelemetry()

    fun update(
        now: Long,
        x: Float,
        y: Float,
        scale: Float
    ): GestureDecision? {
        if (
            !x.isFinite() ||
            !y.isFinite() ||
            !scale.isFinite() ||
            scale <= 0f
        ) {
            points.clear()
            lastPoint = null
            telemetry =
                CenterGateTelemetry(
                    state = "GATE_REJECT_INVALID"
                )
            return null
        }

        val current =
            Point(
                timeMs = now,
                x = x.coerceIn(0f, 1f),
                y = y.coerceIn(0f, 1f),
                scale = scale.coerceIn(0.035f, 0.45f)
            )

        val instantaneousScreenSpeed =
            lastPoint?.let { previous ->
                val dtSeconds =
                    (now - previous.timeMs)
                        .coerceAtLeast(1L) /
                        1000f

                hypot(
                    current.x - previous.x,
                    current.y - previous.y
                ) / dtSeconds
            } ?: 0f

        lastPoint = current

        if (
            instantaneousScreenSpeed >
            maxInstantaneousScreenSpeedPerSecond
        ) {
            // Reject detector teleports, but immediately seed tracking again
            // from the new location so one bad result does not poison the
            // following real motion.
            points.clear()
            points.addLast(current)
            telemetry =
                CenterGateTelemetry(
                    state = "GATE_REJECT_GLITCH"
                )
            return null
        }

        val rearmTransition =
            updateRearmState(
                now = now,
                current = current
            )

        if (now - lastEmitMs < cooldownMs) {
            // Do not queue motion during the immediate action cooldown.
            points.clear()
            points.addLast(current)
            telemetry =
                CenterGateTelemetry(
                    state = "GATE_COOLDOWN"
                )
            return null
        }

        points.addLast(current)
        prune(now)

        if (points.size < 2) {
            telemetry =
                CenterGateTelemetry(
                    state =
                        when {
                            horizontalRearmSide != null &&
                                verticalRearmSide != null ->
                                "GATE_RETURN_SUPPRESSION_BOTH"
                            horizontalRearmSide != null ->
                                "GATE_RETURN_SUPPRESSION_HORIZONTAL"
                            verticalRearmSide != null ->
                                "GATE_RETURN_SUPPRESSION_VERTICAL"
                            else ->
                                "GATE_READY"
                        }
                )
            return null
        }

        val horizontalAvailable =
            horizontalRearmSide == null
        val verticalAvailable =
            verticalRearmSide == null

        val candidates =
            listOfNotNull(
                if (horizontalAvailable) {
                    horizontalCandidate(current)
                } else {
                    null
                },
                if (verticalAvailable) {
                    verticalCandidate(current)
                } else {
                    null
                }
            )

        val ambiguousAxes =
            (
                candidates.size > 1 &&
                    axesAreAmbiguous(candidates)
                ) ||
                hasRawDiagonalAmbiguity(current)

        val candidate =
            when {
                ambiguousAxes -> null
                candidates.isEmpty() -> null
                candidates.size == 1 ->
                    candidates.first()
                else ->
                    chooseAxis(candidates)
            }

        if (candidate == null) {
            telemetry =
                CenterGateTelemetry(
                    state =
                        when {
                            rearmTransition != null ->
                                rearmTransition
                            ambiguousAxes ->
                                "GATE_AMBIGUOUS_DIAGONAL"
                            !horizontalAvailable &&
                                !verticalAvailable ->
                                "GATE_RETURN_SUPPRESSION_BOTH"
                            !horizontalAvailable ->
                                "GATE_RETURN_SUPPRESSION_HORIZONTAL"
                            !verticalAvailable ->
                                "GATE_RETURN_SUPPRESSION_VERTICAL"
                            else ->
                                "GATE_TRACKING"
                        }
                )
            return null
        }

        when (candidate.signal) {
            GestureSignal.SWIPE_LEFT -> {
                horizontalRearmSide =
                    RearmSide.LOW
            }
            GestureSignal.SWIPE_RIGHT -> {
                horizontalRearmSide =
                    RearmSide.HIGH
            }
            GestureSignal.SWIPE_DOWN -> {
                verticalRearmSide =
                    RearmSide.LOW
            }
            GestureSignal.SWIPE_UP -> {
                verticalRearmSide =
                    RearmSide.HIGH
            }
            else -> Unit
        }

        points.clear()
        points.addLast(current)

        if (!signalEnabled(candidate.signal)) {
            // Disabled is truly inert. Still consume the physical stroke and
            // suppress its return so repositioning cannot become the enabled
            // opposite direction.
            telemetry =
                CenterGateTelemetry(
                    state = "GATE_DISABLED_GESTURE",
                    speedPalmsPerSecond =
                        candidate.speedPalmsPerSecond,
                    confidence = candidate.confidence
                )
            return null
        }

        lastEmitMs = now
        telemetry =
            CenterGateTelemetry(
                state = "GATE_COMMITTED",
                speedPalmsPerSecond =
                    candidate.speedPalmsPerSecond,
                confidence = candidate.confidence
            )

        return GestureDecision(
            signal = candidate.signal,
            confidence = candidate.confidence,
            reasoning =
                buildString {
                    append(candidate.signal.label)
                    append(" • user-perspective broad hand stroke")
                    if (candidate.crossedCenter) {
                        append(" • center axis crossed")
                    } else {
                        append(" • dominant displacement fallback")
                    }
                }
        )
    }

    fun telemetry(): CenterGateTelemetry = telemetry

    fun reset() {
        points.clear()
        lastPoint = null
        lastEmitMs = Long.MIN_VALUE / 4
        horizontalRearmSide = null
        verticalRearmSide = null
        horizontalEligibleSinceMs = Long.MIN_VALUE / 4
        verticalEligibleSinceMs = Long.MIN_VALUE / 4
        telemetry = CenterGateTelemetry()
    }

    private fun updateRearmState(
        now: Long,
        current: Point
    ): String? {
        var horizontalRearmed = false
        var verticalRearmed = false

        val horizontalSide =
            horizontalRearmSide
        if (
            horizontalSide != null &&
            reachedRearmSide(
                value = current.x,
                center = centerX,
                side = horizontalSide
            )
        ) {
            horizontalRearmSide = null
            horizontalEligibleSinceMs = now
            horizontalRearmed = true
        }

        val verticalSide =
            verticalRearmSide
        if (
            verticalSide != null &&
            reachedRearmSide(
                value = current.y,
                center = centerY,
                side = verticalSide
            )
        ) {
            verticalRearmSide = null
            verticalEligibleSinceMs = now
            verticalRearmed = true
        }

        return when {
            horizontalRearmed &&
                verticalRearmed ->
                "GATE_REARMED_BOTH"
            horizontalRearmed ->
                "GATE_REARMED_HORIZONTAL"
            verticalRearmed ->
                "GATE_REARMED_VERTICAL"
            else -> null
        }
    }

    private fun reachedRearmSide(
        value: Float,
        center: Float,
        side: RearmSide
    ): Boolean {
        val margin =
            max(
                gateHalfWidth,
                minScreenDisplacement
            )
        return when (side) {
            RearmSide.LOW ->
                value <= center - margin
            RearmSide.HIGH ->
                value >= center + margin
        }
    }

    private fun horizontalCandidate(
        current: Point
    ): Candidate? =
        previousPoints()
            .asSequence()
            .filter {
                it.timeMs >= horizontalEligibleSinceMs
            }
            .mapNotNull { start ->
                val dx = current.x - start.x
                val dy = current.y - start.y
                val crossedCenter =
                    crossedHorizontalCenter(
                        startX = start.x,
                        endX = current.x
                    )

                buildCandidate(
                    start = start,
                    end = current,
                    primary = abs(dx),
                    secondary = abs(dy),
                    crossedCenter = crossedCenter,
                    signal =
                        // Mirrored front-camera coordinates: image-right is
                        // the user's physical LEFT, matching the video.
                        if (dx > 0f) {
                            GestureSignal.SWIPE_LEFT
                        } else {
                            GestureSignal.SWIPE_RIGHT
                        }
                )
            }
            .maxByOrNull {
                it.confidence
            }

    private fun verticalCandidate(
        current: Point
    ): Candidate? =
        previousPoints()
            .asSequence()
            .filter {
                it.timeMs >= verticalEligibleSinceMs
            }
            .mapNotNull { start ->
                val dx = current.x - start.x
                val dy = current.y - start.y
                val crossedCenter =
                    crossedVerticalCenter(
                        startY = start.y,
                        endY = current.y
                    )

                buildCandidate(
                    start = start,
                    end = current,
                    primary = abs(dy),
                    secondary = abs(dx),
                    crossedCenter = crossedCenter,
                    signal =
                        if (dy > 0f) {
                            GestureSignal.SWIPE_DOWN
                        } else {
                            GestureSignal.SWIPE_UP
                        }
                )
            }
            .maxByOrNull {
                it.confidence
            }

    private fun chooseAxis(
        candidates: List<Candidate>
    ): Candidate? =
        candidates
            .maxByOrNull(::scoreForRanking)

    private fun axesAreAmbiguous(
        candidates: List<Candidate>
    ): Boolean {
        val ranked =
            candidates.sortedByDescending(
                ::scoreForRanking
            )
        if (ranked.size < 2) {
            return false
        }

        return scoreForRanking(ranked[0]) -
            scoreForRanking(ranked[1]) <
            simultaneousAxisAmbiguityMargin
    }

    private fun hasRawDiagonalAmbiguity(
        current: Point
    ): Boolean =
        previousPoints().any { start ->
            val durationMs =
                current.timeMs - start.timeMs
            if (
                durationMs < minCrossDurationMs ||
                durationMs > windowMs
            ) {
                return@any false
            }

            val dx = abs(current.x - start.x)
            val dy = abs(current.y - start.y)
            val dominant = max(dx, dy)
            val weaker = minOf(dx, dy)

            if (
                dominant < minScreenDisplacement ||
                weaker < minScreenDisplacement
            ) {
                return@any false
            }

            val durationSeconds =
                durationMs / 1000f
            if (
                dominant / durationSeconds <
                minScreenSpeedPerSecond
            ) {
                return@any false
            }

            val balance =
                abs(dx - dy) /
                    max(dx + dy, 0.0001f)

            balance <= 0.10f
        }

    private fun scoreForRanking(
        candidate: Candidate
    ): Float =
        candidate.axisEvidence * 0.54f +
            candidate.confidence * 0.38f +
            if (candidate.crossedCenter) {
                0.08f
            } else {
                0f
            }

    private fun buildCandidate(
        start: Point,
        end: Point,
        primary: Float,
        secondary: Float,
        crossedCenter: Boolean,
        signal: GestureSignal
    ): Candidate? {
        val durationMs =
            end.timeMs - start.timeMs

        if (
            durationMs < minCrossDurationMs ||
            durationMs > windowMs ||
            primary < minScreenDisplacement
        ) {
            return null
        }

        if (!crossedCenter) {
            if (primary < minFreeStrokeDisplacement) {
                return null
            }

            val startDistanceFromCenter =
                when (signal) {
                    GestureSignal.SWIPE_LEFT,
                    GestureSignal.SWIPE_RIGHT ->
                        abs(start.x - centerX)

                    GestureSignal.SWIPE_UP,
                    GestureSignal.SWIPE_DOWN ->
                        abs(start.y - centerY)

                    else -> 0f
                }

            // Off-center fallback exists for imperfect framing, not for
            // interpreting a move from the neutral middle into a starting
            // position as a gesture.
            if (
                startDistanceFromCenter <
                minFreeStrokeStartDistanceFromCenter
            ) {
                return null
            }
        }

        val durationSeconds =
            durationMs / 1000f
        val screenSpeed =
            primary / durationSeconds

        if (screenSpeed < minScreenSpeedPerSecond) {
            return null
        }

        val axisEvidence =
            primary /
                max(
                    primary + secondary,
                    0.0001f
                )

        if (axisEvidence < minAxisEvidence) {
            return null
        }

        val samples =
            points.filter {
                it.timeMs >= start.timeMs
            }
        val averageScale =
            samples
                .map { it.scale }
                .average()
                .toFloat()
                .coerceAtLeast(0.035f)
        val palmSpeed =
            primary / averageScale /
                durationSeconds

        val spanScore =
            (
                (primary - minScreenDisplacement) /
                    0.24f
                ).coerceIn(0f, 1f)
        val speedScore =
            (
                (screenSpeed -
                    minScreenSpeedPerSecond) /
                    1.8f
                ).coerceIn(0f, 1f)
        val clarityScore =
            (
                (axisEvidence - minAxisEvidence) /
                    (1f - minAxisEvidence)
                ).coerceIn(0f, 1f)

        val confidence =
            (
                0.54f +
                    spanScore * 0.20f +
                    speedScore * 0.12f +
                    clarityScore * 0.10f +
                    if (crossedCenter) {
                        0.04f
                    } else {
                        0f
                    }
                ).coerceIn(0f, 1f)

        return Candidate(
            signal = signal,
            confidence = confidence,
            speedPalmsPerSecond = palmSpeed,
            primary = primary,
            secondary = secondary,
            crossedCenter = crossedCenter
        )
    }

    private fun crossedHorizontalCenter(
        startX: Float,
        endX: Float
    ): Boolean {
        val leftGate = centerX - gateHalfWidth
        val rightGate = centerX + gateHalfWidth
        return (
            startX <= leftGate &&
                endX >= rightGate
            ) ||
            (
                startX >= rightGate &&
                    endX <= leftGate
                )
    }

    private fun crossedVerticalCenter(
        startY: Float,
        endY: Float
    ): Boolean {
        val topGate = centerY - gateHalfWidth
        val bottomGate = centerY + gateHalfWidth
        return (
            startY <= topGate &&
                endY >= bottomGate
            ) ||
            (
                startY >= bottomGate &&
                    endY <= topGate
                )
    }

    private fun previousPoints(): List<Point> =
        points.toList().dropLast(1)

    private fun prune(now: Long) {
        while (
            points.isNotEmpty() &&
            now - points.first().timeMs > windowMs
        ) {
            points.removeFirst()
        }
    }
}
