package com.airgesture.control

/**
 * Broad sanity bounds for MediaPipe hand geometry.
 *
 * MediaPipe can continue emitting plausible hand landmarks when the wrist or
 * palm moves outside the camera image while one or more fingertips remain
 * visible. Those off-frame normalized coordinates are legitimate and must not
 * be rejected merely because they are outside 0..1.
 *
 * These limits are intentionally much wider than the display bounds while
 * still rejecting obvious collapsed/corrupt coordinates. Candidate center and
 * scale receive their own tighter limits below.
 */
object LandmarkGeometryGuard {
    const val MIN_XY = -1.25f
    const val MAX_XY = 2.25f
    const val MIN_Z = -2.0f
    const val MAX_Z = 2.0f

    const val MIN_CANDIDATE_CENTER_XY = -0.80f
    const val MAX_CANDIDATE_CENTER_XY = 1.80f

    const val MIN_RAW_HAND_SCALE = 0.022f
    const val MAX_RAW_HAND_SCALE = 0.70f

    fun landmarkPlausible(
        x: Float,
        y: Float,
        z: Float
    ): Boolean =
        x.isFinite() &&
            y.isFinite() &&
            z.isFinite() &&
            x in MIN_XY..MAX_XY &&
            y in MIN_XY..MAX_XY &&
            z in MIN_Z..MAX_Z

    fun candidatePlausible(
        centerX: Float,
        centerY: Float,
        rawScale: Float
    ): Boolean =
        centerX.isFinite() &&
            centerY.isFinite() &&
            rawScale.isFinite() &&
            centerX in
                MIN_CANDIDATE_CENTER_XY..
                    MAX_CANDIDATE_CENTER_XY &&
            centerY in
                MIN_CANDIDATE_CENTER_XY..
                    MAX_CANDIDATE_CENTER_XY &&
            rawScale in
                MIN_RAW_HAND_SCALE..
                MAX_RAW_HAND_SCALE
}
