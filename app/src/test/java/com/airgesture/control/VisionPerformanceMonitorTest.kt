package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPerformanceMonitorTest {

    @Test
    fun measuresFrameRatesLatencyYieldAndThrottle() {
        val monitor = VisionPerformanceMonitor(emaAlpha = 1f)

        monitor.onCameraFrame(0, submitted = true)
        monitor.onCameraFrame(25, submitted = false)
        monitor.onCameraFrame(50, submitted = true)
        monitor.onResult(
            sourceTimestampMs = 0,
            nowMs = 60
        )
        monitor.onResult(
            sourceTimestampMs = 50,
            nowMs = 110
        )

        val snapshot = monitor.snapshot()

        assertEquals(20f, snapshot.submittedFps, .01f)
        assertEquals(20f, snapshot.resultFps, .01f)
        assertEquals(60f, snapshot.analysisToResultLatencyMs, .01f)
        assertEquals(100f, snapshot.resultYieldPercent, .01f)
        assertTrue(snapshot.throttlePercent > 30f)
        assertTrue(snapshot.throttlePercent < 34f)
    }

    @Test
    fun staleResultDropsAreMeasuredSeparatelyFromAnalysisToResultLatency() {
        val monitor =
            VisionPerformanceMonitor(
                emaAlpha = 1f
            )

        monitor.onResult(
            sourceTimestampMs = 100L,
            nowMs = 320L
        )
        monitor.onStaleResultDropped()

        val snapshot = monitor.snapshot()

        assertEquals(
            220f,
            snapshot.lastResultAgeMs,
            .01f
        )
        assertEquals(
            100f,
            snapshot.staleDropPercent,
            .01f
        )
        assertEquals(
            1L,
            snapshot.staleDroppedResults
        )
    }

    @Test
    fun resetClearsSessionMeasurements() {
        val monitor = VisionPerformanceMonitor()
        monitor.onCameraFrame(0, submitted = true)
        monitor.onResult(0, 40)

        monitor.reset()
        val snapshot = monitor.snapshot()

        assertEquals(0L, snapshot.submittedFrames)
        assertEquals(0L, snapshot.resultFrames)
        assertEquals(0f, snapshot.analysisToResultLatencyMs, .01f)
        assertEquals(0f, snapshot.lastResultAgeMs, .01f)
        assertEquals(0L, snapshot.staleDroppedResults)
    }
    @Test
    fun recentHealthyRatesRecoverYieldAfterEarlyDrops() {
        val monitor =
            VisionPerformanceMonitor(
                emaAlpha = 1f
            )

        // Early overload: several submissions, few results.
        monitor.onCameraFrame(
            0,
            submitted = true
        )
        monitor.onCameraFrame(
            20,
            submitted = true
        )
        monitor.onCameraFrame(
            40,
            submitted = true
        )
        monitor.onResult(
            sourceTimestampMs = 0,
            nowMs = 100
        )

        // Recent steady-state: submitted and result cadence both 50 ms.
        monitor.onCameraFrame(
            100,
            submitted = true
        )
        monitor.onCameraFrame(
            150,
            submitted = true
        )
        monitor.onResult(
            sourceTimestampMs = 100,
            nowMs = 160
        )
        monitor.onResult(
            sourceTimestampMs = 150,
            nowMs = 210
        )

        val snapshot = monitor.snapshot()

        assertEquals(
            100f,
            snapshot.resultYieldPercent,
            .01f
        )
    }


}
