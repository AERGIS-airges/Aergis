package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Causal fingertip estimator for the MediaPipe index-tip (#8) measurement.
 *
 * Separates normal motion from isolated large innovations and from
 * subpixel landmark shimmer. A single large innovation is step-limited; a
 * second coherent observation promotes the new fingertip quickly. Small
 * travel is admitted progressively only when the landmark keeps moving in a
 * consistent direction; alternating micro-motion is strongly damped.
 * No future sample or result-age prediction is used.
 */
class PointerKinematicFilter(
    private val nominalMeasurementNoise: Float = 0.00035f,
    private val processAccelerationNoise: Float = 0.55f,
    private val maxVelocity: Float = 3.5f,
    private val extremeInnovation: Float = 0.20f,
    private val isolatedStepLimit: Float = 0.035f,
    private val confirmationDistance: Float = 0.035f
) {
    private var x: Float? = null
    private var y: Float? = null
    private var vx = 0f
    private var vy = 0f
    private var lastTimestampMs: Long? = null
    private var previousMeasurementX: Float? = null
    private var previousMeasurementY: Float? = null
    private var largeMeasurementStreak = 0
    private var previousSmallDirectionX = 0
    private var previousSmallDirectionY = 0
    private var smallMotionStreak = 0

    fun update(
        measuredX: Float,
        measuredY: Float,
        timestampMs: Long,
        confidence: Float = 1f
    ): Pair<Float, Float> {
        if (!measuredX.isFinite() || !measuredY.isFinite()) {
            return current() ?: (0.5f to 0.5f)
        }

        val mx = measuredX.coerceIn(0f, 1f)
        val my = measuredY.coerceIn(0f, 1f)
        val previousX = x
        val previousY = y
        val previousTimestamp = lastTimestampMs

        if (previousX == null || previousY == null || previousTimestamp == null) {
            x = mx
            y = my
            vx = 0f
            vy = 0f
            lastTimestampMs = timestampMs
            previousMeasurementX = mx
            previousMeasurementY = my
            largeMeasurementStreak = 0
            smallMotionStreak = 0
            previousSmallDirectionX = 0
            previousSmallDirectionY = 0
            return mx to my
        }

        // Live-stream results must be strictly timestamp-ordered. A late or
        // duplicate callback must never advance the estimator clock: treating
        // equal timestamps as an 8 ms sample would manufacture motion and can
        // inflate velocity before the next genuinely newer result arrives.
        if (timestampMs <= previousTimestamp) {
            return previousX to previousY
        }

        val dt =
            ((timestampMs - previousTimestamp).coerceIn(8L, 120L) / 1000f)
                .coerceIn(0.008f, 0.120f)
        val predictedX = previousX + vx * dt
        val predictedY = previousY + vy * dt
        val innovationX = mx - predictedX
        val innovationY = my - predictedY
        val innovation = hypot(innovationX, innovationY)

        val previousMeasuredX = previousMeasurementX ?: mx
        val previousMeasuredY = previousMeasurementY ?: my
        val measurementDeltaX = mx - previousMeasuredX
        val measurementDeltaY = my - previousMeasuredY

        val sameLargeMeasurement =
            hypot(measurementDeltaX, measurementDeltaY) <= confirmationDistance &&
                innovation > extremeInnovation

        largeMeasurementStreak = when {
            innovation <= extremeInnovation -> 0
            sameLargeMeasurement -> largeMeasurementStreak + 1
            else -> 1
        }

        val confidence01 = confidence.coerceIn(0.2f, 1f)
        val measurementNoise = nominalMeasurementNoise / confidence01
        val speed = hypot(vx, vy)
        val processScale = 1f + min(speed * 0.35f, 2.5f)
        val q = processAccelerationNoise * processScale
        val normalizedQ = q * dt * dt * dt / 3f
        val baseGain =
            (normalizedQ / (normalizedQ + measurementNoise))
                .coerceIn(0.55f, 0.94f)
        val velocityGain =
            (normalizedQ / (normalizedQ + measurementNoise + 0.0001f))
                .coerceIn(0.08f, 0.72f)

        val smallTravel =
            innovation < 0.020f &&
                hypot(measurementDeltaX, measurementDeltaY) < 0.020f
        val directionX = measurementDeltaX.compareTo(0f)
        val directionY = measurementDeltaY.compareTo(0f)

        var alpha: Float
        var beta: Float
        if (innovation > extremeInnovation && largeMeasurementStreak == 1) {
            alpha = (isolatedStepLimit / innovation.coerceAtLeast(0.0001f))
                .coerceIn(0.025f, 0.22f)
            beta = 0.03f
            smallMotionStreak = 0
            previousSmallDirectionX = 0
            previousSmallDirectionY = 0
        } else if (innovation > extremeInnovation && largeMeasurementStreak >= 2) {
            alpha = 0.92f
            beta = 0.45f
            smallMotionStreak = 0
            previousSmallDirectionX = 0
            previousSmallDirectionY = 0
        } else if (smallTravel) {
            val hadPreviousDirection =
                previousSmallDirectionX != 0 || previousSmallDirectionY != 0
            val directionChanged =
                hadPreviousDirection &&
                    (directionX != previousSmallDirectionX ||
                        directionY != previousSmallDirectionY)

            if (directionChanged) {
                smallMotionStreak = 1
                alpha = 0.05f
                beta = 0.01f
            } else {
                smallMotionStreak =
                    if (directionX == previousSmallDirectionX &&
                        directionY == previousSmallDirectionY &&
                        (directionX != 0 || directionY != 0)
                    ) {
                        smallMotionStreak + 1
                    } else {
                        1
                    }
                alpha = if (smallMotionStreak == 1) 0.25f else 0.80f
                beta = if (smallMotionStreak == 1) 0.03f else 0.35f
            }

            previousSmallDirectionX = directionX
            previousSmallDirectionY = directionY
        } else {
            alpha = baseGain
            beta = velocityGain
            smallMotionStreak = 0
            previousSmallDirectionX = 0
            previousSmallDirectionY = 0
        }

        // Uncertain landmarks should not create visible cursor shimmer. This
        // only affects micro-travel; meaningful motion and confirmed large
        // movement retain their existing response characteristics.
        if (confidence01 < 0.65f && smallTravel) {
            val damping =
                (0.28f + confidence01 * 0.42f)
                    .coerceIn(0.28f, 0.55f)
            alpha *= damping
            beta *= damping
        }

        val updatedX = predictedX + innovationX * alpha
        val updatedY = predictedY + innovationY * alpha
        val measuredVelocityX = innovationX / dt
        val measuredVelocityY = innovationY / dt
        vx = (vx + (measuredVelocityX - vx) * beta)
            .coerceIn(-maxVelocity, maxVelocity)
        vy = (vy + (measuredVelocityY - vy) * beta)
            .coerceIn(-maxVelocity, maxVelocity)

        x = clampBetween(updatedX, previousX, mx)
        y = clampBetween(updatedY, previousY, my)
        lastTimestampMs = timestampMs
        previousMeasurementX = mx
        previousMeasurementY = my

        return x!! to y!!
    }

    fun current(): Pair<Float, Float>? =
        if (x != null && y != null) x!! to y!! else null

    fun velocity(): Pair<Float, Float> = vx to vy

    fun reset() {
        x = null
        y = null
        vx = 0f
        vy = 0f
        lastTimestampMs = null
        previousMeasurementX = null
        previousMeasurementY = null
        largeMeasurementStreak = 0
        smallMotionStreak = 0
        previousSmallDirectionX = 0
        previousSmallDirectionY = 0
    }

    private fun clampBetween(value: Float, a: Float, b: Float): Float =
        value.coerceIn(min(a, b), max(a, b)).coerceIn(0f, 1f)
}
