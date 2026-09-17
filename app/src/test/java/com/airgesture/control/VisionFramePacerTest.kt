package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionFramePacerTest {

    @Test
    fun warmupUsesMaximumTrackingRate() {
        val pacer = VisionFramePacer()

        val interval =
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 220f,
                    resultYieldPercent = 20f,
                    submittedFrames = 4L
                )
            )

        assertEquals(30L, interval)
    }

    @Test
    fun pointerWarmupUsesR18HighCadence() {
        val pacer = VisionFramePacer()

        val interval =
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 220f,
                    resultYieldPercent = 20f,
                    submittedFrames = 4L
                ),
                pointerActive = true
            )

        assertEquals(8L, interval)
    }

    @Test
    fun healthyPipelineStaysAtNormalRate() {
        val pacer = VisionFramePacer()

        val interval =
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 62f,
                    resultYieldPercent = 94f,
                    submittedFrames = 30L
                )
            )

        assertEquals(30L, interval)
    }

    @Test
    fun moderateOverloadReducesSubmissionPressure() {
        val pacer = VisionFramePacer()

        val interval =
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 106f,
                    resultYieldPercent = 82f,
                    submittedFrames = 30L
                )
            )

        assertEquals(38L, interval)
    }

    @Test
    fun pointerModeUsesHigherCadenceWithoutRemovingOverloadBackoff() {
        val pacer = VisionFramePacer()

        assertEquals(
            8L,
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 62f,
                    resultYieldPercent = 94f,
                    submittedFrames = 30L
                ),
                pointerActive = true
            )
        )

        assertEquals(
            12L,
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 106f,
                    resultYieldPercent = 82f,
                    submittedFrames = 30L
                ),
                pointerActive = true
            )
        )

        assertEquals(
            20L,
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 160f,
                    resultYieldPercent = 48f,
                    submittedFrames = 30L
                ),
                pointerActive = true
            )
        )
    }

    @Test
    fun severeOverloadBacksOffFurther() {
        val pacer = VisionFramePacer()

        val interval =
            pacer.targetIntervalMs(
                VisionPerformanceSnapshot(
                    analysisToResultLatencyMs = 160f,
                    resultYieldPercent = 48f,
                    submittedFrames = 30L
                )
            )

        assertEquals(48L, interval)
    }
}
