package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.max

enum class ControlHandPreference(val label: String) {
    AUTO("Auto"),
    LEFT("Left"),
    RIGHT("Right")
}

data class HandCandidate(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val handedness: String,
    val handednessScore: Float,
    val identitySignature: List<Float> =
        emptyList()
)

data class ControlHandSelection(
    val index: Int,
    val handedness: String,
    val handednessScore: Float,
    val detectedCount: Int,
    val continuityLocked: Boolean
)

/**
 * Selects one physical control hand while tolerating the kinds of landmark
 * discontinuities that occur during fast front-camera motion.
 *
 * Earlier builds used a tight palm-size-normalized distance as a hard
 * continuity gate. A distant/small hand could therefore move several "palm
 * widths" between two MediaPipe results and be rejected even though its
 * screen-space movement was completely plausible. This selector combines:
 *
 *  - palm-normalized distance
 *  - absolute screen-space distance
 *  - short-horizon velocity prediction
 *  - handedness as a soft identity signal in AUTO mode
 *
 * Explicit LEFT/RIGHT preference still rejects a confidently opposite hand.
 */
class ControlHandSelector(
    initialPreference: ControlHandPreference = ControlHandPreference.AUTO,
    private val lostResetMs: Long = 650L,
    private val maxContinuityDistancePalms: Float = 3.0f,
    private val maxContinuityScreenDistance: Float = 0.34f,
    private val maxPredictionError: Float = 0.24f,
    private val maxIdentitySignatureDrift: Float = 0.28f,
    private val minMultiHandScoreMargin: Float = 0.08f,
    private val discontinuityConfirmFrames: Int = 2,
    private val multiHandAcquireConfirmFrames: Int = 2
) {
    private var preference = initialPreference
    private var previousX: Float? = null
    private var previousY: Float? = null
    private var previousScale: Float? = null
    private var previousHandedness: String? = null
    private var previousIdentitySignature:
        List<Float>? = null
    private var previousTimestampMs: Long? = null
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastSeenMs: Long? = null

    private var pendingX: Float? = null
    private var pendingY: Float? = null
    private var pendingScale: Float? = null
    private var pendingHandedness: String? = null
    private var pendingIdentitySignature:
        List<Float>? = null
    private var pendingCount = 0

    fun updatePreference(value: ControlHandPreference) {
        if (preference == value) return
        preference = value
        reset()
    }

    fun currentPreference(): ControlHandPreference = preference

    fun resetTracking() {
        reset()
    }

    fun select(
        timestampMs: Long,
        candidates: List<HandCandidate>
    ): ControlHandSelection? {
        val validCandidates =
            candidates.filter {
                it.centerX.isFinite() &&
                    it.centerY.isFinite() &&
                    it.scale.isFinite() &&
                    it.scale > 0f &&
                    it.handednessScore.isFinite()
            }

        if (validCandidates.isEmpty()) {
            expireIfLost(timestampMs)
            clearPending()
            return null
        }

        val px = previousX
        val py = previousY
        val ps = previousScale
        val ph = previousHandedness

        val eligible =
            when (preference) {
                ControlHandPreference.AUTO ->
                    validCandidates

                ControlHandPreference.LEFT ->
                    validCandidates.filter {
                        preferenceCompatible(
                            candidate = it,
                            requested = "Left",
                            previousX = px,
                            previousY = py,
                            previousScale = ps
                        )
                    }

                ControlHandPreference.RIGHT ->
                    validCandidates.filter {
                        preferenceCompatible(
                            candidate = it,
                            requested = "Right",
                            previousX = px,
                            previousY = py,
                            previousScale = ps
                        )
                    }
            }

        if (eligible.isEmpty()) {
            expireIfLost(timestampMs)
            clearPending()
            return null
        }

        if (
            px == null ||
            py == null ||
            ps == null
        ) {
            val chosen =
                eligible.maxByOrNull(::initialScore)
                    ?: return null

            if (
                eligible.size > 1 &&
                multiHandAcquireConfirmFrames > 1
            ) {
                val confirmed =
                    updatePendingAcquisition(chosen)
                if (!confirmed) {
                    return null
                }
            }

            adopt(
                candidate = chosen,
                timestampMs = timestampMs
            )
            clearPending()
            return selection(
                candidate = chosen,
                detectedCount = validCandidates.size,
                continuityLocked = false
            )
        }

        val ranked =
            eligible
                .map { candidate ->
                    candidate to
                        continuityScore(
                            candidate = candidate,
                            timestampMs = timestampMs,
                            previousX = px,
                            previousY = py,
                            previousScale = ps,
                            previousHandedness = ph
                        )
                }
                .sortedByDescending {
                    it.second
                }

        if (
            ranked.size > 1 &&
            ranked[0].second -
                ranked[1].second <
                minMultiHandScoreMargin
        ) {
            clearPending()
            return null
        }

        val chosen =
            ranked.firstOrNull()
                ?.first
                ?: return null

        if (
            isContinuous(
                candidate = chosen,
                timestampMs = timestampMs,
                previousX = px,
                previousY = py,
                previousScale = ps
            )
        ) {
            adopt(
                candidate = chosen,
                timestampMs = timestampMs
            )
            clearPending()
            return selection(
                candidate = chosen,
                detectedCount = validCandidates.size,
                continuityLocked = true
            )
        }

        // A truly discontinuous candidate still needs confirmation. This
        // protects against a detector-order glitch or a second hand suddenly
        // becoming more prominent.
        val confirmed =
            updatePendingDiscontinuity(chosen)

        if (!confirmed) {
            expireIfLost(timestampMs)
            return null
        }

        adopt(
            candidate = chosen,
            timestampMs = timestampMs
        )
        clearPending()

        return selection(
            candidate = chosen,
            detectedCount = validCandidates.size,
            continuityLocked = false
        )
    }

    private fun preferenceCompatible(
        candidate: HandCandidate,
        requested: String,
        previousX: Float?,
        previousY: Float?,
        previousScale: Float?
    ): Boolean {
        if (
            candidate.handedness.equals(
                requested,
                ignoreCase = true
            ) &&
            candidate.handednessScore >= 0.46f
        ) {
            return true
        }

        if (
            candidate.handednessScore >= 0.72f &&
            !candidate.handedness.equals(
                requested,
                ignoreCase = true
            )
        ) {
            return false
        }

        if (
            previousX == null ||
            previousY == null ||
            previousScale == null
        ) {
            return false
        }

        return isSpatiallyNearby(
            candidate = candidate,
            x = previousX,
            y = previousY,
            scale = previousScale
        )
    }

    private fun isContinuous(
        candidate: HandCandidate,
        timestampMs: Long,
        previousX: Float,
        previousY: Float,
        previousScale: Float
    ): Boolean {
        val palmDistance =
            normalizedDistance(
                candidate = candidate,
                x = previousX,
                y = previousY,
                scale = previousScale
            )
        val screenDistance =
            screenDistance(
                candidate = candidate,
                x = previousX,
                y = previousY
            )
        val predictionDistance =
            predictedDistance(
                candidate = candidate,
                timestampMs = timestampMs,
                previousX = previousX,
                previousY = previousY
            )

        val spatiallyContinuous =
            palmDistance <=
                maxContinuityDistancePalms ||
                screenDistance <=
                    maxContinuityScreenDistance ||
                predictionDistance <=
                    maxPredictionError

        val identityDistance =
            HandIdentitySignature.distance(
                previousIdentitySignature
                    .orEmpty(),
                candidate.identitySignature
            )
        val identityCompatible =
            identityDistance == null ||
                identityDistance <=
                    maxIdentitySignatureDrift

        return spatiallyContinuous &&
            identityCompatible
    }

    private fun updatePendingAcquisition(
        candidate: HandCandidate
    ): Boolean =
        updatePending(
            candidate = candidate,
            requiredFrames =
                multiHandAcquireConfirmFrames
                    .coerceAtLeast(2)
        )

    private fun updatePendingDiscontinuity(
        candidate: HandCandidate
    ): Boolean =
        updatePending(
            candidate = candidate,
            requiredFrames =
                discontinuityConfirmFrames
                    .coerceAtLeast(2)
        )

    private fun updatePending(
        candidate: HandCandidate,
        requiredFrames: Int
    ): Boolean {
        val px = pendingX
        val py = pendingY
        val ps = pendingScale
        val ph = pendingHandedness
        val pi = pendingIdentitySignature

        val identityDistance =
            HandIdentitySignature.distance(
                pi.orEmpty(),
                candidate.identitySignature
            )
        val samePending =
            px != null &&
                py != null &&
                ps != null &&
                (
                    normalizedDistance(
                        candidate = candidate,
                        x = px,
                        y = py,
                        scale = ps
                    ) <= 1.65f ||
                        screenDistance(
                            candidate = candidate,
                            x = px,
                            y = py
                        ) <= 0.18f
                    ) &&
                (
                    ph == null ||
                        candidate.handedness.equals(
                            ph,
                            ignoreCase = true
                        ) ||
                        candidate.handednessScore < 0.72f
                    ) &&
                (
                    identityDistance == null ||
                        identityDistance <=
                            maxIdentitySignatureDrift
                    )

        if (samePending) {
            pendingCount++
            // Follow the pending candidate so confirmation also tolerates
            // legitimate motion instead of comparing every frame to the
            // first pending location.
            pendingX = candidate.centerX
            pendingY = candidate.centerY
            pendingScale = candidate.scale
            if (
                candidate.identitySignature
                    .isNotEmpty()
            ) {
                pendingIdentitySignature =
                    candidate.identitySignature
            }
        } else {
            pendingX = candidate.centerX
            pendingY = candidate.centerY
            pendingScale = candidate.scale
            pendingHandedness =
                candidate.handedness
                    .takeIf {
                        candidate.handednessScore >= 0.58f
                    }
            pendingIdentitySignature =
                candidate.identitySignature
                    .takeIf {
                        it.isNotEmpty()
                    }
            pendingCount = 1
        }

        return pendingCount >= requiredFrames
    }

    private fun isSpatiallyNearby(
        candidate: HandCandidate,
        x: Float,
        y: Float,
        scale: Float
    ): Boolean =
        normalizedDistance(
            candidate = candidate,
            x = x,
            y = y,
            scale = scale
        ) <= maxContinuityDistancePalms ||
            screenDistance(
                candidate = candidate,
                x = x,
                y = y
            ) <= maxContinuityScreenDistance

    private fun normalizedDistance(
        candidate: HandCandidate,
        x: Float,
        y: Float,
        scale: Float
    ): Float {
        val averageScale =
            max(
                (candidate.scale + scale) * 0.5f,
                0.04f
            )
        return hypot(
            candidate.centerX - x,
            candidate.centerY - y
        ) / averageScale
    }

    private fun screenDistance(
        candidate: HandCandidate,
        x: Float,
        y: Float
    ): Float =
        hypot(
            candidate.centerX - x,
            candidate.centerY - y
        )

    private fun predictedDistance(
        candidate: HandCandidate,
        timestampMs: Long,
        previousX: Float,
        previousY: Float
    ): Float {
        val previousTime =
            previousTimestampMs ?: return Float.MAX_VALUE
        val dt =
            (
                (timestampMs - previousTime)
                    .coerceIn(0L, 180L) /
                    1000f
                )
        val predictedX =
            previousX + velocityX * dt
        val predictedY =
            previousY + velocityY * dt

        return hypot(
            candidate.centerX - predictedX,
            candidate.centerY - predictedY
        )
    }

    private fun initialScore(candidate: HandCandidate): Float {
        val scaleScore =
            (candidate.scale / 0.28f)
                .coerceIn(0f, 1f)
        val handednessScore =
            candidate.handednessScore
                .coerceIn(0f, 1f)

        return scaleScore * 0.70f +
            handednessScore * 0.30f
    }

    private fun continuityScore(
        candidate: HandCandidate,
        timestampMs: Long,
        previousX: Float,
        previousY: Float,
        previousScale: Float,
        previousHandedness: String?
    ): Float {
        val palmDistance =
            normalizedDistance(
                candidate = candidate,
                x = previousX,
                y = previousY,
                scale = previousScale
            )
        val screenDistance =
            screenDistance(
                candidate = candidate,
                x = previousX,
                y = previousY
            )
        val predictedDistance =
            predictedDistance(
                candidate = candidate,
                timestampMs = timestampMs,
                previousX = previousX,
                previousY = previousY
            )

        val palmContinuity =
            (
                1f -
                    palmDistance /
                    maxContinuityDistancePalms
                        .coerceAtLeast(0.1f)
                ).coerceIn(0f, 1f)
        val screenContinuity =
            (
                1f -
                    screenDistance /
                    maxContinuityScreenDistance
                        .coerceAtLeast(0.01f)
                ).coerceIn(0f, 1f)
        val predictionContinuity =
            if (predictedDistance.isFinite()) {
                (
                    1f -
                        predictedDistance /
                        maxPredictionError
                            .coerceAtLeast(0.01f)
                    ).coerceIn(0f, 1f)
            } else {
                0f
            }

        val motionContinuity =
            maxOf(
                palmContinuity,
                screenContinuity,
                predictionContinuity
            )

        val sameHandBonus =
            if (
                previousHandedness != null &&
                candidate.handedness.equals(
                    previousHandedness,
                    ignoreCase = true
                ) &&
                candidate.handednessScore >= 0.50f
            ) {
                0.14f
            } else {
                0f
            }

        val scaleScore =
            (candidate.scale / 0.30f)
                .coerceIn(0f, 1f) * 0.07f
        val classificationScore =
            candidate.handednessScore
                .coerceIn(0f, 1f) * 0.07f

        val identityDistance =
            HandIdentitySignature.distance(
                previousIdentitySignature
                    .orEmpty(),
                candidate.identitySignature
            )
        val identityBonus =
            identityDistance
                ?.let {
                    (
                        1f -
                            it /
                                maxIdentitySignatureDrift
                                    .coerceAtLeast(
                                        0.01f
                                    )
                        ).coerceIn(0f, 1f) *
                        0.18f
                }
                ?: 0f

        return motionContinuity * 0.72f +
            sameHandBonus +
            scaleScore +
            classificationScore +
            identityBonus
    }

    private fun selection(
        candidate: HandCandidate,
        detectedCount: Int,
        continuityLocked: Boolean
    ): ControlHandSelection =
        ControlHandSelection(
            index = candidate.index,
            handedness =
                candidate.handedness
                    .takeIf {
                        candidate.handednessScore >= 0.55f
                    }
                    ?.ifBlank { "Unknown" }
                    ?: "Unknown",
            handednessScore =
                candidate.handednessScore
                    .coerceIn(0f, 1f),
            detectedCount = detectedCount,
            continuityLocked = continuityLocked
        )

    private fun adopt(
        candidate: HandCandidate,
        timestampMs: Long
    ) {
        val oldX = previousX
        val oldY = previousY
        val oldTime = previousTimestampMs

        if (
            oldX != null &&
            oldY != null &&
            oldTime != null
        ) {
            val deltaMs =
                timestampMs - oldTime
            val displacement =
                hypot(
                    candidate.centerX - oldX,
                    candidate.centerY - oldY
                )

            if (
                deltaMs in 12L..250L &&
                displacement <= 0.45f
            ) {
                val dt = deltaMs / 1000f
                var sampleVX =
                    (candidate.centerX - oldX) / dt
                var sampleVY =
                    (candidate.centerY - oldY) / dt
                val speed =
                    hypot(sampleVX, sampleVY)
                if (speed > 5.0f) {
                    val scale = 5.0f / speed
                    sampleVX *= scale
                    sampleVY *= scale
                }

                velocityX +=
                    (sampleVX - velocityX) * 0.45f
                velocityY +=
                    (sampleVY - velocityY) * 0.45f
            } else {
                velocityX = 0f
                velocityY = 0f
            }
        } else {
            velocityX = 0f
            velocityY = 0f
        }

        previousX = candidate.centerX
        previousY = candidate.centerY
        previousScale = candidate.scale
        previousTimestampMs = timestampMs

        if (candidate.handednessScore >= 0.60f) {
            previousHandedness =
                candidate.handedness
        }
        if (
            candidate.identitySignature
                .isNotEmpty()
        ) {
            previousIdentitySignature =
                candidate.identitySignature
        }
        lastSeenMs = timestampMs
    }

    private fun expireIfLost(timestampMs: Long) {
        val lastSeen = lastSeenMs ?: return
        if (timestampMs - lastSeen > lostResetMs) {
            reset()
        }
    }

    private fun clearPending() {
        pendingX = null
        pendingY = null
        pendingScale = null
        pendingHandedness = null
        pendingIdentitySignature = null
        pendingCount = 0
    }

    private fun reset() {
        previousX = null
        previousY = null
        previousScale = null
        previousHandedness = null
        previousIdentitySignature = null
        previousTimestampMs = null
        velocityX = 0f
        velocityY = 0f
        lastSeenMs = null
        clearPending()
    }
}
