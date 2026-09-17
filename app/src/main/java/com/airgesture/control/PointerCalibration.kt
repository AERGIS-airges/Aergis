package com.airgesture.control

import kotlin.math.abs
import kotlin.math.pow

enum class PointerCalibrationOrientation {
    PORTRAIT,
    LANDSCAPE
}

data class PointerCalibrationContext(
    val handPreference: ControlHandPreference,
    val orientation: PointerCalibrationOrientation
) {
    val storageKey: String
        get() =
            handPreference.name.lowercase() +
                "_" +
                orientation.name.lowercase()

    companion object {
        fun forOrientation(
            handPreference: ControlHandPreference,
            landscape: Boolean
        ): PointerCalibrationContext =
            PointerCalibrationContext(
                handPreference =
                    handPreference,
                orientation =
                    if (landscape) {
                        PointerCalibrationOrientation
                            .LANDSCAPE
                    } else {
                        PointerCalibrationOrientation
                            .PORTRAIT
                    }
            )
    }
}

data class PointerCalibration(
    val minX: Float = DEFAULT_MIN_X,
    val maxX: Float = DEFAULT_MAX_X,
    val minY: Float = DEFAULT_MIN_Y,
    val maxY: Float = DEFAULT_MAX_Y,
    val curveX: Float = DEFAULT_CURVE_X,
    val curveY: Float = DEFAULT_CURVE_Y
) {
    fun sanitized(): PointerCalibration {
        val horizontal = sanitizePair(
            rawMin = minX,
            rawMax = maxX,
            defaultMin = DEFAULT_MIN_X,
            defaultMax = DEFAULT_MAX_X
        )
        val vertical = sanitizePair(
            rawMin = minY,
            rawMax = maxY,
            defaultMin = DEFAULT_MIN_Y,
            defaultMax = DEFAULT_MAX_Y
        )

        return PointerCalibration(
            minX = horizontal.first,
            maxX = horizontal.second,
            minY = vertical.first,
            maxY = vertical.second,
            curveX = sanitizeCurve(curveX, DEFAULT_CURVE_X),
            curveY = sanitizeCurve(curveY, DEFAULT_CURVE_Y)
        )
    }

    /**
     * Mapping methods assume this instance has already been sanitized.
     * Runtime tracker/store boundaries enforce that invariant.
     *
     * The first stage is an affine reach calibration. The second stage is a
     * symmetric signed-power response around screen center. A curve > 1 gives
     * sublinear movement near the center for precision, then progressively
     * increases gain toward the edges so the full display remains reachable.
     */
    fun mapX(rawX: Float): Float =
        mapAxis(rawX, minX, maxX, curveX)

    fun mapY(rawY: Float): Float =
        mapAxis(rawY, minY, maxY, curveY)

    private fun sanitizePair(
        rawMin: Float,
        rawMax: Float,
        defaultMin: Float,
        defaultMax: Float
    ): Pair<Float, Float> {
        var low =
            rawMin
                .takeIf { it.isFinite() }
                ?.coerceIn(MIN_BOUND, MAX_BOUND)
                ?: defaultMin
        var high =
            rawMax
                .takeIf { it.isFinite() }
                ?.coerceIn(MIN_BOUND, MAX_BOUND)
                ?: defaultMax

        if (low > high) {
            val swap = low
            low = high
            high = swap
        }

        if (high - low < MIN_SPAN) {
            val center = (low + high) * 0.5f
            low =
                (center - MIN_SPAN * 0.5f)
                    .coerceAtLeast(MIN_BOUND)
            high = low + MIN_SPAN

            if (high > MAX_BOUND) {
                high = MAX_BOUND
                low = high - MIN_SPAN
            }
        }

        return low to high
    }

    private fun sanitizeCurve(
        raw: Float,
        fallback: Float
    ): Float =
        raw.takeIf { it.isFinite() }
            ?.coerceIn(MIN_CURVE, MAX_CURVE)
            ?: fallback

    private fun mapAxis(
        value: Float,
        minValue: Float,
        maxValue: Float,
        curve: Float
    ): Float {
        val linear =
            ((value - minValue) / (maxValue - minValue))
                .coerceIn(0f, 1f)

        val signed = linear * 2f - 1f
        val magnitude =
            abs(signed)
                .toDouble()
                .pow(curve.toDouble())
                .toFloat()
        val curved =
            if (signed < 0f) {
                -magnitude
            } else {
                magnitude
            }

        return (0.5f + curved * 0.5f)
            .coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_MIN_X = 0.10f
        const val DEFAULT_MAX_X = 0.90f
        const val DEFAULT_MIN_Y = 0.08f
        const val DEFAULT_MAX_Y = 0.92f
        const val DEFAULT_CURVE_X = 1.00f
        const val DEFAULT_CURVE_Y = 1.00f

        const val MIN_BOUND = -0.20f
        const val MAX_BOUND = 1.20f
        const val MIN_SPAN = 0.20f
        const val MIN_CURVE = 0.75f
        const val MAX_CURVE = 1.80f

        val DEFAULT = PointerCalibration()
    }
}
