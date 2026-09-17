package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPipelineBenchmarkTest {
    @Test
    fun samplesStaySeparatedByExactDelegateAndInputMode() {
        val benchmark =
            VisionPipelineBenchmark()

        val cpu =
            VisionPipelineKey(
                VisionDelegateMode.CPU,
                VisionInputMode.BITMAP
            )
        val gpu =
            VisionPipelineKey(
                VisionDelegateMode.GPU,
                VisionInputMode.BITMAP
            )

        benchmark.onResult(
            cpu,
            analysisToResultMs = 100L,
            stale = false
        )
        benchmark.onResult(
            cpu,
            analysisToResultMs = 120L,
            stale = true
        )
        benchmark.onResult(
            gpu,
            analysisToResultMs = 70L,
            stale = false
        )

        val rows = benchmark.snapshot()
        assertEquals(2, rows.size)

        val cpuRow =
            rows.first {
                it.key == cpu
            }
        assertEquals(2L, cpuRow.samples)
        assertEquals(
            110f,
            cpuRow.meanAnalysisToResultMs,
            .01f
        )
        assertEquals(
            50f,
            cpuRow.stalePercent,
            .01f
        )

        val gpuRow =
            rows.first {
                it.key == gpu
            }
        assertEquals(1L, gpuRow.samples)
        assertEquals(
            70f,
            gpuRow.meanAnalysisToResultMs,
            .01f
        )
    }

    @Test
    fun benchmarkSummaryNeverPretendsToRecommendWithoutComparison() {
        val benchmark =
            VisionPipelineBenchmark()
        benchmark.onResult(
            VisionPipelineKey(
                VisionDelegateMode.GPU,
                VisionInputMode.DIRECT_MEDIA_IMAGE
            ),
            analysisToResultMs = 82L,
            stale = false
        )

        val summary = benchmark.summary()
        assertTrue(
            summary.contains(
                "GPU/DIRECT_MEDIA_IMAGE"
            )
        )
        assertTrue(
            !summary.contains(
                "recommended",
                ignoreCase = true
            )
        )
    }

    @Test
    fun resetRemovesSessionEvidence() {
        val benchmark =
            VisionPipelineBenchmark()
        benchmark.onResult(
            VisionPipelineKey(
                VisionDelegateMode.CPU,
                VisionInputMode.BITMAP
            ),
            analysisToResultMs = 90L,
            stale = false
        )

        benchmark.reset()

        assertTrue(
            benchmark.snapshot().isEmpty()
        )
        assertEquals(
            "Pipeline benchmark: no samples",
            benchmark.summary()
        )
    }
}
