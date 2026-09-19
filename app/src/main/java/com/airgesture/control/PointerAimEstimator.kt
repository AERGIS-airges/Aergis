package com.airgesture.control

data class AimLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

data class PointerAimInput(
    /** MediaPipe INDEX_FINGER_TIP — landmark 8. */
    val indexTip: AimLandmark
)

data class PointerAimResult(
    val x: Float,
    val y: Float,
    val confidence: Float,
    /** Retained for compatibility; click logic no longer consumes straightness. */
    val straightness: Float = 1f,
    val rayX: Float,
    val rayY: Float,
    val stable: Boolean = true,
    val nearestFingerSeparation: Float = Float.POSITIVE_INFINITY,
    val reasoning: String = "Index fingertip #8 direct tracking"
)

/**
 * Pointer positional estimator with a deliberately tiny input surface.
 *
 * The estimator receives exactly one anatomical point: MediaPipe landmark 8.
 * Palm center, wrist, thumb, middle finger and other hand geometry are absent
 * from this API, so none of them can influence pointer X/Y accidentally.
 *
 * Click recognition lives in a separate index-tip/middle-tip contact channel.
 */
class PointerAimEstimator(
    private val acceptedCoordinateMargin: Float = 0.20f
) {
    private var lastValidTipX: Float? = null
    private var lastValidTipY: Float? = null

    init {
        require(
            acceptedCoordinateMargin.isFinite() &&
                acceptedCoordinateMargin >= 0f &&
                acceptedCoordinateMargin <= 1f
        ) {
            "Accepted pointer-coordinate margin must be finite and in [0, 1]"
        }
    }

    fun estimate(input: PointerAimInput): PointerAimResult {
        val tip = input.indexTip
        if (nonFinite(tip)) {
            return invalidResult(
                "Index fingertip #8 unavailable • holding last valid fingertip"
            )
        }

        // Reject semantically impossible detector output before it reaches the
        // smoothing pipeline. A small margin tolerates camera-edge landmarks;
        // values beyond it are treated as a lost sample, not clamped motion.
        if (
            tip.x < -acceptedCoordinateMargin ||
            tip.x > 1f + acceptedCoordinateMargin ||
            tip.y < -acceptedCoordinateMargin ||
            tip.y > 1f + acceptedCoordinateMargin
        ) {
            return invalidResult(
                "Index fingertip #8 outside camera bounds • holding last valid fingertip"
            )
        }

        // The estimator owns the normalized-space boundary. Downstream code
        // receives only canonical [0, 1] position measurements.
        val targetX = tip.x.coerceIn(0f, 1f)
        val targetY = tip.y.coerceIn(0f, 1f)
        lastValidTipX = targetX
        lastValidTipY = targetY

        return PointerAimResult(
            x = targetX,
            y = targetY,
            confidence = 1f,
            straightness = 1f,
            rayX = targetX,
            rayY = targetY,
            stable = true,
            reasoning =
                "Pointer X/Y = MediaPipe index fingertip landmark 8 only"
        )
    }

    fun reset() {
        lastValidTipX = null
        lastValidTipY = null
    }

    private fun invalidResult(reason: String): PointerAimResult {
        val holdX = lastValidTipX ?: 0.5f
        val holdY = lastValidTipY ?: 0.5f
        return PointerAimResult(
            x = holdX,
            y = holdY,
            confidence = 0f,
            straightness = 1f,
            rayX = holdX,
            rayY = holdY,
            stable = false,
            reasoning = reason
        )
    }

    private fun nonFinite(point: AimLandmark): Boolean =
        !point.x.isFinite() ||
            !point.y.isFinite() ||
            !point.z.isFinite()
}
