package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerFilterDiagnosticsTest {

    @Test
    fun defaultsMakeShadowOnlyContractExplicit() {
        PointerFilterDiagnostics.reset()

        val state = PointerFilterDiagnostics.current()

        assertEquals("R7 PointerMotionFilter", state.productionControl)
        assertTrue(state.challenger.contains("shadow only"))
        assertEquals("Pointer filter A/B: no samples", state.summary)
        assertNull(state.snapshot)
    }

    @Test
    fun publishedSnapshotCanBeReadWithoutSelectingAFilter() {
        PointerFilterDiagnostics.reset()
        val snapshot =
            PointerFilterBenchmarkSnapshot(
                samples = 42L,
                stationarySamples = 18L,
                deliberateMotionSamples = 12L,
                rawStationaryRmsStep = .004f,
                productionStationaryRmsStep = .001f,
                challengerStationaryRmsStep = .002f,
                productionMeanMotionError = .011f,
                challengerMeanMotionError = .014f,
                rawPathLength = 1.4f,
                productionPathLength = 1.2f,
                challengerPathLength = 1.1f
            )

        PointerFilterDiagnostics.publish(
            timestampMs = 1_234L,
            summary = "Pointer filter A/B: synthetic",
            snapshot = snapshot
        )

        val state = PointerFilterDiagnostics.current()
        assertEquals(1_234L, state.updatedAtMs)
        assertEquals("R7 PointerMotionFilter", state.productionControl)
        assertNotNull(state.snapshot)
        assertEquals(42L, state.snapshot?.samples)
        assertEquals(.001f, state.snapshot?.productionStationaryRmsStep ?: -1f, .0001f)
    }

    @Test
    fun productionMotionFilterPublishesRealShadowEvidence() {
        PointerFilterDiagnostics.reset()
        val filter = PointerMotionFilter()

        filter.update(.50f, .50f, 100L, .95f)

        val state = PointerFilterDiagnostics.current()
        assertNotNull(state.snapshot)
        assertEquals(1L, state.snapshot?.samples)
        assertTrue(state.summary.startsWith("Pointer filter A/B:"))
    }
}
