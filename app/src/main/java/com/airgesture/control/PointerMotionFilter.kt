package com.airgesture.control

/**
 * Production pointer smoother.
 *
 * R18 replaces the old fixed-cutoff exponential smoother with the causal
 * adaptive alpha-beta/Kalman-family estimator in PointerKinematicFilter.
 * Landmark 8 remains the sole positional measurement. The estimator adapts to
 * measured cadence and confidence, rejects isolated innovations, and never
 * emits beyond the newest measured fingertip.
 */
class PointerMotionFilter(
    private val kinematic: PointerKinematicFilter = PointerKinematicFilter()
) {
    private val oneEuroChallenger = OneEuroPointerFilter()
    private val filterBenchmark = PointerFilterBenchmark()
    private var diagnosticsPublishCounter = 0L
    private var lastTimestampMs: Long? = null

    fun update(
        rawX: Float,
        rawY: Float,
        timestampMs: Long,
        confidence: Float,
        resultAgeMs: Long = 0L
    ): Pair<Float, Float> {
        // Keep the complete smoothing/benchmark stack monotonic. The
        // kinematic estimator already rejects stale samples, but allowing a
        // duplicate/out-of-order callback through the challenger or telemetry
        // path can still mutate their internal state and skew diagnostics.
        val previousTimestamp = lastTimestampMs
        if (previousTimestamp != null && timestampMs <= previousTimestamp) {
            return kinematic.current() ?: (0.5f to 0.5f)
        }
        lastTimestampMs = timestampMs

        val safeX = rawX.takeIf { it.isFinite() } ?: kinematic.current()?.first ?: 0.5f
        val safeY = rawY.takeIf { it.isFinite() } ?: kinematic.current()?.second ?: 0.5f

        val production =
            kinematic.update(
                measuredX = safeX,
                measuredY = safeY,
                timestampMs = timestampMs,
                confidence = confidence
            )

        val challenger =
            oneEuroChallenger.update(
                rawX = safeX,
                rawY = safeY,
                timestampMs = timestampMs
            )
        filterBenchmark.onSample(
            rawX = safeX,
            rawY = safeY,
            productionX = production.first,
            productionY = production.second,
            challengerX = challenger.first,
            challengerY = challenger.second
        )
        diagnosticsPublishCounter++
        if (
            diagnosticsPublishCounter == 1L ||
            diagnosticsPublishCounter % 30L == 0L
        ) {
            PointerFilterDiagnostics.publish(
                timestampMs = timestampMs,
                summary = filterBenchmark.summary(),
                snapshot = filterBenchmark.snapshot()
            )
        }

        // Intentionally not used for prediction. Retained as telemetry only.
        @Suppress("UNUSED_VARIABLE")
        val measuredResultAgeMs = resultAgeMs
        return production
    }

    fun current(): Pair<Float, Float>? = kinematic.current()

    fun filterBenchmarkSnapshot(): PointerFilterBenchmarkSnapshot =
        filterBenchmark.snapshot()

    fun filterBenchmarkSummary(): String =
        filterBenchmark.summary()

    fun reset() {
        kinematic.reset()
        diagnosticsPublishCounter = 0L
        lastTimestampMs = null
        oneEuroChallenger.reset()
        filterBenchmark.reset()
        PointerFilterDiagnostics.reset()
    }
}
