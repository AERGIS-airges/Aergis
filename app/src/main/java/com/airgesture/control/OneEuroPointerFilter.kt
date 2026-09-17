package com.airgesture.control

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Canonical One Euro Filter challenger for 2D pointer coordinates.
 *
 * This class is deliberately independent from the production
 * [PointerMotionFilter]. R8 uses it as an A/B challenger only; creating this
 * filter does not change the live cursor path.
 *
 * The implementation follows the original One Euro structure:
 * 1. low-pass the coordinate derivative;
 * 2. derive an adaptive position cutoff from filtered speed;
 * 3. low-pass position with that adaptive cutoff.
 *
 * Coordinates are normalized to 0..1, so beta remains explicitly tunable.
 * No forward prediction is performed and the filtered output is always a
 * convex interpolation between the previous output and latest measurement.
 */
class OneEuroPointerFilter(
    private val minCutoffHz: Float = 1.0f,
    private val beta: Float = 0.01f,
    private val derivativeCutoffHz: Float = 1.0f
) {
    private var filteredX: Float? = null
    private var filteredY: Float? = null
    private var previousRawX: Float? = null
    private var previousRawY: Float? = null
    private var filteredDx = 0f
    private var filteredDy = 0f
    private var lastTimestampMs: Long? = null

    init {
        require(minCutoffHz > 0f && minCutoffHz.isFinite())
        require(beta >= 0f && beta.isFinite())
        require(derivativeCutoffHz > 0f && derivativeCutoffHz.isFinite())
    }

    fun update(
        rawX: Float,
        rawY: Float,
        timestampMs: Long
    ): Pair<Float, Float> {
        val oldFilteredX = filteredX
        val oldFilteredY = filteredY
        val oldTimestamp = lastTimestampMs

        val safeX =
            rawX.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: oldFilteredX
                ?: 0.5f
        val safeY =
            rawY.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: oldFilteredY
                ?: 0.5f

        if (
            oldFilteredX == null ||
            oldFilteredY == null ||
            oldTimestamp == null
        ) {
            filteredX = safeX
            filteredY = safeY
            previousRawX = safeX
            previousRawY = safeY
            filteredDx = 0f
            filteredDy = 0f
            lastTimestampMs = timestampMs
            return safeX to safeY
        }

        val dtSeconds =
            (timestampMs - oldTimestamp)
                .coerceIn(8L, 120L) /
                1000f

        val oldRawX = previousRawX ?: safeX
        val oldRawY = previousRawY ?: safeY
        val rawDx = (safeX - oldRawX) / dtSeconds
        val rawDy = (safeY - oldRawY) / dtSeconds

        val derivativeAlpha =
            alphaForCutoff(
                cutoffHz = derivativeCutoffHz,
                dtSeconds = dtSeconds
            )
        filteredDx +=
            (rawDx - filteredDx) * derivativeAlpha
        filteredDy +=
            (rawDy - filteredDy) * derivativeAlpha

        val filteredSpeed = hypot(filteredDx, filteredDy)
        val adaptiveCutoff =
            (minCutoffHz + beta * filteredSpeed)
                .coerceAtLeast(0.001f)
        val positionAlpha =
            alphaForCutoff(
                cutoffHz = adaptiveCutoff,
                dtSeconds = dtSeconds
            )

        val nextX =
            oldFilteredX +
                (safeX - oldFilteredX) * positionAlpha
        val nextY =
            oldFilteredY +
                (safeY - oldFilteredY) * positionAlpha

        filteredX = nextX.coerceIn(0f, 1f)
        filteredY = nextY.coerceIn(0f, 1f)
        previousRawX = safeX
        previousRawY = safeY
        lastTimestampMs = timestampMs

        return filteredX!! to filteredY!!
    }

    fun current(): Pair<Float, Float>? {
        val x = filteredX ?: return null
        val y = filteredY ?: return null
        return x to y
    }

    fun reset() {
        filteredX = null
        filteredY = null
        previousRawX = null
        previousRawY = null
        filteredDx = 0f
        filteredDy = 0f
        lastTimestampMs = null
    }

    private fun alphaForCutoff(
        cutoffHz: Float,
        dtSeconds: Float
    ): Float =
        (
            1.0 -
                exp(
                    -2.0 *
                        PI *
                        cutoffHz.toDouble() *
                        dtSeconds.toDouble()
                )
            ).toFloat()
            .coerceIn(0f, 1f)
}
