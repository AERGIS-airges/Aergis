package com.airgesture.control

data class VisionPerformanceSnapshot(
    val submittedFps: Float = 0f,
    val resultFps: Float = 0f,
    val analysisToResultLatencyMs: Float = 0f,
    val resultYieldPercent: Float = 0f,
    val throttlePercent: Float = 0f,
    val staleDropPercent: Float = 0f,
    val lastResultAgeMs: Float = 0f,
    val submittedFrames: Long = 0L,
    val resultFrames: Long = 0L,
    val staleDroppedResults: Long = 0L
)

class VisionPerformanceMonitor(
    private val emaAlpha: Float = 0.18f
) {
    private var totalFrames = 0L
    private var submittedFrames = 0L
    private var throttledFrames = 0L
    private var resultFrames = 0L
    private var staleDroppedResults = 0L

    private var lastSubmittedAtMs: Long? = null
    private var lastResultAtMs: Long? = null

    private var submittedIntervalEmaMs = 0f
    private var resultIntervalEmaMs = 0f
    private var analysisToResultEmaMs = 0f
    private var lastResultAgeMs = 0f

    @Synchronized
    fun onCameraFrame(
        timestampMs: Long,
        submitted: Boolean
    ) {
        totalFrames++

        if (!submitted) {
            throttledFrames++
            return
        }

        submittedFrames++
        val previous = lastSubmittedAtMs
        if (previous != null && timestampMs > previous) {
            submittedIntervalEmaMs = ema(
                current = submittedIntervalEmaMs,
                sample = (timestampMs - previous).toFloat()
            )
        }
        lastSubmittedAtMs = timestampMs
    }

    @Synchronized
    fun onResult(
        sourceTimestampMs: Long,
        nowMs: Long
    ) {
        resultFrames++

        val previous = lastResultAtMs
        if (previous != null && nowMs > previous) {
            resultIntervalEmaMs = ema(
                current = resultIntervalEmaMs,
                sample = (nowMs - previous).toFloat()
            )
        }
        lastResultAtMs = nowMs

        val latency =
            (nowMs - sourceTimestampMs)
                .coerceAtLeast(0L)
                .toFloat()
        lastResultAgeMs = latency
        analysisToResultEmaMs = ema(
            current = analysisToResultEmaMs,
            sample = latency
        )
    }

    @Synchronized
    fun onStaleResultDropped() {
        staleDroppedResults++
    }

    @Synchronized
    fun snapshot(): VisionPerformanceSnapshot {
        val submittedFps =
            intervalToFps(submittedIntervalEmaMs)
        val resultFps =
            intervalToFps(resultIntervalEmaMs)

        val resultYield =
            when {
                submittedFps > 0f &&
                    resultFps > 0f ->
                    resultFps /
                        submittedFps * 100f
                submittedFrames > 0L ->
                    resultFrames.toFloat() /
                        submittedFrames.toFloat() *
                        100f
                else -> 0f
            }

        val throttle =
            if (totalFrames > 0L) {
                throttledFrames.toFloat() /
                    totalFrames.toFloat() * 100f
            } else {
                0f
            }

        val staleDrop =
            if (resultFrames > 0L) {
                staleDroppedResults.toFloat() /
                    resultFrames.toFloat() * 100f
            } else {
                0f
            }

        return VisionPerformanceSnapshot(
            submittedFps = submittedFps,
            resultFps = resultFps,
            analysisToResultLatencyMs = analysisToResultEmaMs,
            resultYieldPercent =
                resultYield.coerceIn(0f, 100f),
            throttlePercent =
                throttle.coerceIn(0f, 100f),
            staleDropPercent =
                staleDrop.coerceIn(0f, 100f),
            lastResultAgeMs = lastResultAgeMs,
            submittedFrames = submittedFrames,
            resultFrames = resultFrames,
            staleDroppedResults = staleDroppedResults
        )
    }

    @Synchronized
    fun reset() {
        totalFrames = 0L
        submittedFrames = 0L
        throttledFrames = 0L
        resultFrames = 0L
        staleDroppedResults = 0L
        lastSubmittedAtMs = null
        lastResultAtMs = null
        submittedIntervalEmaMs = 0f
        resultIntervalEmaMs = 0f
        analysisToResultEmaMs = 0f
        lastResultAgeMs = 0f
    }

    private fun ema(
        current: Float,
        sample: Float
    ): Float =
        if (current <= 0f) {
            sample
        } else {
            current + (sample - current) *
                emaAlpha.coerceIn(0.01f, 1f)
        }

    private fun intervalToFps(intervalMs: Float): Float =
        if (intervalMs > 0f) {
            (1000f / intervalMs).coerceIn(0f, 120f)
        } else {
            0f
        }
}
