package com.airgesture.control

/**
 * Adaptive camera-to-MediaPipe submission pacing.
 *
 * R18 treats the camera as a high-rate source and lets MediaPipe's LIVE_STREAM
 * scheduler perform the final inference admission. Pointer mode therefore has
 * an 8 ms (~125 Hz) submission ceiling rather than an artificial 16 ms
 * (~62.5 Hz) ceiling. The pacer does not claim that inference itself will run
 * at 125 Hz; it simply prevents Aergis from throttling a capable camera before
 * the native vision pipeline gets a chance to consume fresh frames.
 *
 * Backoff remains adaptive. If measured inference latency or result yield
 * collapses, submission pressure is reduced to avoid stale-frame churn.
 */
class VisionFramePacer(
    private val normalIntervalMs: Long = 30L,
    private val moderateIntervalMs: Long = 38L,
    private val overloadedIntervalMs: Long = 48L,
    private val pointerNormalIntervalMs: Long = 8L,
    private val pointerModerateIntervalMs: Long = 12L,
    private val pointerOverloadedIntervalMs: Long = 20L,
    private val warmupSubmittedFrames: Long = 12L
) {
    fun targetIntervalMs(
        snapshot: VisionPerformanceSnapshot,
        pointerActive: Boolean = false
    ): Long {
        if (snapshot.submittedFrames < warmupSubmittedFrames) {
            return if (pointerActive) pointerNormalIntervalMs else normalIntervalMs
        }

        val severelyOverloaded =
            snapshot.analysisToResultLatencyMs >= 140f ||
                snapshot.resultYieldPercent <= 55f
        if (severelyOverloaded) {
            return if (pointerActive) pointerOverloadedIntervalMs else overloadedIntervalMs
        }

        val moderatelyOverloaded =
            snapshot.analysisToResultLatencyMs >= 95f ||
                snapshot.resultYieldPercent <= 72f
        if (moderatelyOverloaded) {
            return if (pointerActive) pointerModerateIntervalMs else moderateIntervalMs
        }

        return if (pointerActive) pointerNormalIntervalMs else normalIntervalMs
    }
}
