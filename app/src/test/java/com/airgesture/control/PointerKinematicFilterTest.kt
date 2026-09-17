package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PointerKinematicFilterTest {
    @Test
    fun stationaryJitterIsDamped() {
        val filter = PointerKinematicFilter()
        val first = filter.update(.5f, .5f, 0L)
        val outputs = listOf(.502f, .498f, .501f, .499f, .500f)
            .mapIndexed { index, x -> filter.update(x, .5f, (index + 1) * 16L).first }

        assertTrue(outputs.maxOrNull()!! - outputs.minOrNull()!! < .003f)
        assertTrue(abs(outputs.last() - first.first) < .003f)
    }

    @Test
    fun sustainedMotionRemainsMonotonicAndCausal() {
        val filter = PointerKinematicFilter()
        filter.update(.2f, .5f, 0L)
        val measurements = listOf(.28f, .36f, .44f, .52f, .60f, .68f)
        val outputs = measurements.mapIndexed { index, x ->
            filter.update(x, .5f, (index + 1) * 16L).first
        }

        assertTrue(outputs.zipWithNext().all { (a, b) -> b >= a })
        outputs.zip(measurements).forEach { (output, measurement) ->
            assertTrue(output in .2f..measurement)
        }
        assertTrue(outputs.last() > .55f)
    }

    @Test
    fun isolatedTeleportIsBounded() {
        val filter = PointerKinematicFilter()
        filter.update(.5f, .5f, 0L)
        val spike = filter.update(.95f, .5f, 16L)
        assertTrue(spike.first > .5f)
        assertTrue(spike.first < .95f)

        val returned = filter.update(.5f, .5f, 32L)
        assertTrue(returned.first in .5f..spike.first)
    }

    @Test
    fun resultCadenceChangesDoNotChangeEndpointMaterially() {
        fun run(stepMs: Long): Float {
            val filter = PointerKinematicFilter()
            filter.update(.2f, .5f, 0L)
            var output = .2f
            for (i in 1..20) {
                val t = i / 20f
                output = filter.update(.2f + .5f * t, .5f, i * stepMs).first
            }
            return output
        }

        val at30 = run(33L)
        val at60 = run(16L)
        assertTrue(abs(at30 - at60) < .06f)
        assertTrue(at30 <= .7f && at60 <= .7f)
    }

    @Test
    fun lowConfidenceMeasurementIsTrustedLess() {
        val strong = PointerKinematicFilter()
        val weak = PointerKinematicFilter()
        strong.update(.5f, .5f, 0L)
        weak.update(.5f, .5f, 0L)

        val strongOutput = strong.update(.6f, .5f, 16L, confidence = 1f).first
        val weakOutput = weak.update(.6f, .5f, 16L, confidence = .2f).first
        assertTrue(strongOutput >= weakOutput)
    }

    @Test
    fun lowConfidenceMicroTravelIsDampedMoreThanHighConfidenceTravel() {
        val high = PointerKinematicFilter()
        val low = PointerKinematicFilter()
        high.update(.500f, .500f, 0L, .95f)
        low.update(.500f, .500f, 0L, .95f)

        val highOutput = high.update(.508f, .500f, 33L, .95f)
        val lowOutput = low.update(.508f, .500f, 33L, .45f)

        assertTrue(highOutput.first > lowOutput.first)
        assertTrue(lowOutput.first < .504f)
    }

    @Test
    fun meaningfulTravelRemainsResponsiveAtLowConfidence() {
        val filter = PointerKinematicFilter()
        filter.update(.300f, .500f, 0L, .95f)

        val output = filter.update(.450f, .500f, 33L, .45f)

        assertTrue(output.first > .330f)
        assertTrue(output.first <= .450f)
    }

    @Test
    fun outOfOrderTimestampCannotRollEstimatorClockBackward() {
        val filter = PointerKinematicFilter()
        filter.update(.5f, .5f, 0L)
        val accepted = filter.update(.6f, .5f, 16L)

        val stale = filter.update(.95f, .5f, 8L)
        assertEquals(accepted.first, stale.first, 0f)
        assertEquals(accepted.second, stale.second, 0f)

        val next = filter.update(.7f, .5f, 24L)
        assertTrue(next.first > accepted.first)
        assertTrue(next.first <= .7f)
    }

    @Test
    fun duplicateTimestampCannotAdvanceEstimatorOrVelocity() {
        val filter = PointerKinematicFilter()
        filter.update(.5f, .5f, 0L)
        val accepted = filter.update(.6f, .5f, 16L)
        val acceptedVelocity = filter.velocity()

        val duplicate = filter.update(.95f, .5f, 16L)
        assertEquals(accepted.first, duplicate.first, 0f)
        assertEquals(accepted.second, duplicate.second, 0f)
        assertEquals(acceptedVelocity.first, filter.velocity().first, 0f)
        assertEquals(acceptedVelocity.second, filter.velocity().second, 0f)

        val next = filter.update(.7f, .5f, 32L)
        assertTrue(next.first > accepted.first)
        assertTrue(next.first <= .7f)
    }

    @Test
    fun sustainedLargeTravelStillConfirmsQuickly() {
        val filter = PointerKinematicFilter()
        filter.update(.350f, .500f, 0L, .95f)

        val staged = filter.update(.780f, .500f, 40L, .95f)
        val confirmed = filter.update(.780f, .500f, 80L, .95f)

        assertTrue(staged.first < .42f)
        assertTrue(confirmed.first > .74f)
        assertTrue(confirmed.first <= .780f)
    }

    @Test
    fun staleTimestampIsQuarantinedBeforePointerFilterStack() {
        val filter = PointerMotionFilter()
        val accepted = filter.update(.50f, .50f, 16L, confidence = 1f)
        val stale = filter.update(.95f, .50f, 8L, confidence = 1f)

        assertEquals(accepted.first, stale.first, 0f)
        assertEquals(accepted.second, stale.second, 0f)

        val next = filter.update(.60f, .50f, 32L, confidence = 1f)
        assertTrue(next.first > accepted.first)
        assertTrue(next.first <= .60f)
    }
}
