package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerMotionFilterShadowBenchmarkTest {

    @Test
    fun liveProductionFilterCollectsShadowEvidenceWithoutChangingOutputContract() {
        val filter = PointerMotionFilter()

        val outputs = listOf(.500f, .502f, .499f, .520f, .560f, .610f)
            .mapIndexed { index, x ->
                filter.update(
                    rawX = x,
                    rawY = .50f,
                    timestampMs = index * 33L,
                    confidence = .95f
                )
            }

        val evidence = filter.filterBenchmarkSnapshot()

        assertEquals(outputs.size.toLong(), evidence.samples)
        assertTrue(evidence.stationarySamples >= 2L)
        assertTrue(evidence.deliberateMotionSamples >= 2L)

        outputs.forEach { output ->
            assertTrue(output.first in 0f..1f)
            assertTrue(output.second in 0f..1f)
        }
        assertTrue(
            filter.filterBenchmarkSummary()
                .startsWith("Pointer filter A/B:")
        )
    }

    @Test
    fun resetClearsProductionAndShadowStateTogether() {
        val filter = PointerMotionFilter()
        filter.update(.25f, .50f, 0L, .95f)
        filter.update(.75f, .50f, 33L, .95f)

        filter.reset()

        val fresh = filter.update(.61f, .42f, 200L, .95f)
        val evidence = filter.filterBenchmarkSnapshot()

        assertEquals(.61f, fresh.first, .0001f)
        assertEquals(.42f, fresh.second, .0001f)
        assertEquals(1L, evidence.samples)
        assertEquals(0L, evidence.stationarySamples)
        assertEquals(0L, evidence.deliberateMotionSamples)
    }
}
