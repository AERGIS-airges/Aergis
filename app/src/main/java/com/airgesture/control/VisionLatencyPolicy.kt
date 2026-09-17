package com.airgesture.control

/**
 * Latest-state latency policy for camera inference.
 *
 * Pointer mode must prefer freshness over throughput: queued or very old
 * results make the cursor visibly follow where the hand used to be. However,
 * a single external in-flight frame also creates an avoidable bubble between
 * one MediaPipe result callback and the next camera frame. LIVE_STREAM already
 * owns internal frame dropping, so pointer mode permits exactly two submitted
 * frames: one being processed plus at most one fresher waiting candidate.
 * This keeps the queue bounded while reducing result-cadence gaps.
 *
 * Gesture mode uses the same bounded depth and keeps a wider result-age
 * envelope because its temporal recognizer benefits from recall but must still
 * never execute arbitrarily stale input.
 */
object VisionLatencyPolicy {
    const val POINTER_MAX_IN_FLIGHT = 2
    const val GESTURE_MAX_IN_FLIGHT = 2

    const val POINTER_HARD_MAX_RESULT_AGE_MS = 220L
    const val GESTURE_HARD_MAX_RESULT_AGE_MS = 450L

    fun maxInFlight(pointerActive: Boolean): Int =
        if (pointerActive) {
            POINTER_MAX_IN_FLIGHT
        } else {
            GESTURE_MAX_IN_FLIGHT
        }

    fun hardMaxResultAgeMs(
        pointerActive: Boolean
    ): Long =
        if (pointerActive) {
            POINTER_HARD_MAX_RESULT_AGE_MS
        } else {
            GESTURE_HARD_MAX_RESULT_AGE_MS
        }

    fun resultIsFreshEnough(
        pointerActive: Boolean,
        resultAgeMs: Long
    ): Boolean =
        resultAgeMs >= 0L &&
            resultAgeMs <=
                hardMaxResultAgeMs(pointerActive)
}
