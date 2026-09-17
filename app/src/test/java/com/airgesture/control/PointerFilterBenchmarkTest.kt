package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerFilterBenchmarkTest {

    @Test
    fun scriptedProductionVsOneEuroComparisonProducesEvidence() {
        val production = PointerMotionFilter()
        val challenger = OneEuroPointerFilter(
            minCutoffHz = 1f,
            beta = .15f,
            derivativeCutoffHz = 1f
        )
        val benchmark = PointerFilterBenchmark()

        val raw = listOf(
            .500f,
            .502f,
            .499f,
            .501f,
            .500f,
            .520f,
            .560f,
            .610f,
            .660f,
            .700f
        )

        raw.forEachIndexed { index, x ->
            val time = index * 33L
            val prod =
                production.update(
                    rawX = x,
                    rawY = .50f,
                    timestampMs = time,
                    confidence = .95f
                )
            val oneEuro =
                challenger.update(
                    rawX = x,
                    rawY = .50f,
                    timestampMs = time
                )

            benchmark.onSample(
                rawX = x,
                rawY = .50f,
                productionX = prod.first,
                productionY = prod.second,
                challengerX = oneEuro.first,
                challengerY = oneEuro.second
            )
        }

        val result = benchmark.snapshot()

        assertEquals(raw.size.toLong(), result.samples)
        assertTrue(result.stationarySamples >= 3L)
        assertTrue(result.deliberateMotionSamples >= 3L)
        assertTrue(
            result.productionStationaryRmsStep <=
                result.rawStationaryRmsStep
        )
        assertTrue(result.productionMeanMotionError >= 0f)
        assertTrue(result.challengerMeanMotionError >= 0f)
        assertTrue(benchmark.summary().contains("Pointer filter A/B"))
    }

    @Test
    fun continuityBreakPreventsReacquisitionJumpFromPollutingMetrics() {
        val benchmark = PointerFilterBenchmark()

        benchmark.onSample(
            rawX = .20f,
            rawY = .20f,
            productionX = .20f,
            productionY = .20f,
            challengerX = .20f,
            challengerY = .20f
        )
        benchmark.breakContinuity()
        benchmark.onSample(
            rawX = .80f,
            rawY = .80f,
            productionX = .80f,
            productionY = .80f,
            challengerX = .80f,
            challengerY = .80f
        )

        val result = benchmark.snapshot()
        assertEquals(2L, result.samples)
        assertEquals(0L, result.stationarySamples)
        assertEquals(0L, result.deliberateMotionSamples)
        assertEquals(0f, result.rawPathLength, .0001f)
    }

    @Test
    fun resetClearsAllEvidence() {
        val benchmark = PointerFilterBenchmark()
        benchmark.onSample(
            rawX = .5f,
            rawY = .5f,
            productionX = .5f,
            productionY = .5f,
            challengerX = .5f,
            challengerY = .5f
        )

        benchmark.reset()

        val result = benchmark.snapshot()
        assertEquals(0L, result.samples)
        assertEquals("Pointer filter A/B: no samples", benchmark.summary())
    }
}
