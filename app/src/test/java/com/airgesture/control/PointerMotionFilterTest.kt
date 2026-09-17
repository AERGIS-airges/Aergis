package com.airgesture.control

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PointerMotionFilterTest {

    @Test
    fun stationaryMicroJitterIsStronglyDamped() {
        val filter = PointerMotionFilter()
        val first = filter.update(.500f, .500f, 0L, .95f)
        val jitter = filter.update(.502f, .500f, 33L, .95f)

        assertTrue(abs(jitter.first - first.first) < .001f)
    }

    @Test
    fun alternatingLandmarkShimmerStaysInsideTightPrecisionBand() {
        val filter = PointerMotionFilter()
        val raw =
            listOf(
                .500f,
                .506f,
                .494f,
                .505f,
                .495f,
                .504f,
                .496f,
                .503f,
                .497f,
                .500f
            )

        val outputs =
            raw.mapIndexed { index, x ->
                filter.update(
                    rawX = x,
                    rawY = .500f,
                    timestampMs = index * 33L,
                    confidence = .95f
                ).first
            }

        val span =
            (outputs.maxOrNull() ?: 0f) -
                (outputs.minOrNull() ?: 0f)
        assertTrue(span < .003f)
    }

    @Test
    fun consistentSmallTravelRegainsResponsivenessAfterCoherence() {
        val filter = PointerMotionFilter()
        filter.update(.500f, .500f, 0L, .95f)

        val outputs =
            listOf(.503f, .506f, .509f, .512f, .515f)
                .mapIndexed { index, rawX ->
                    filter.update(
                        rawX = rawX,
                        rawY = .500f,
                        timestampMs = (index + 1) * 33L,
                        confidence = .95f
                    ).first
                }

        assertTrue(outputs.zipWithNext().all { (a, b) -> b > a })
        assertTrue(outputs.last() > .510f)
        assertTrue(outputs.last() <= .515f)
    }

    @Test
    fun isolatedMediumOffsetDoesNotReceiveOldAggressiveSnap() {
        val filter = PointerMotionFilter()
        filter.update(.500f, .500f, 0L, .95f)

        val offset =
            filter.update(
                rawX = .520f,
                rawY = .500f,
                timestampMs = 33L,
                confidence = .95f
            )

        assertTrue(offset.first > .500f)
        assertTrue(offset.first < .515f)
    }

    @Test
    fun singleFrameLargeSpikeIsStepLimited() {
        val filter = PointerMotionFilter()
        filter.update(.50f, .50f, 0L, .95f)

        val spike = filter.update(.90f, .50f, 33L, .95f)

        assertTrue(spike.first > .50f)
        assertTrue(spike.first <= .535f)
    }

    @Test
    fun sustainedLargeMoveConvergesAfterConfirmation() {
        val filter = PointerMotionFilter()
        filter.update(.35f, .50f, 0L, .95f)

        val firstLarge = filter.update(.78f, .50f, 40L, .95f)
        val confirmed = filter.update(.78f, .50f, 80L, .95f)

        assertTrue(firstLarge.first < .42f)
        assertTrue(confirmed.first > .74f)
        assertTrue(confirmed.first <= .78f)
    }

    @Test
    fun spikeAndImmediateReturnDoesNotWhipCursorAcrossScreen() {
        val filter = PointerMotionFilter()
        filter.update(.50f, .50f, 0L, .95f)

        val spike = filter.update(.90f, .50f, 33L, .95f)
        val returned = filter.update(.50f, .50f, 66L, .95f)

        assertTrue(spike.first <= .535f)
        assertTrue(returned.first in .50f..spike.first)
    }

    @Test
    fun resultAgeNeverProjectsBeyondMeasuredFingertip() {
        val fresh = PointerMotionFilter()
        val aged = PointerMotionFilter()

        fresh.update(.30f, .50f, 0L, .95f)
        aged.update(.30f, .50f, 0L, .95f)

        val freshResult =
            fresh.update(
                rawX = .50f,
                rawY = .50f,
                timestampMs = 40L,
                confidence = .95f,
                resultAgeMs = 0L
            )
        val agedResult =
            aged.update(
                rawX = .50f,
                rawY = .50f,
                timestampMs = 40L,
                confidence = .95f,
                resultAgeMs = 160L
            )

        assertTrue(abs(freshResult.first - agedResult.first) < .0001f)
        assertTrue(agedResult.first <= .50f)
    }

    @Test
    fun directionReversalFollowsLatestMeasurementWithoutPrediction() {
        val filter = PointerMotionFilter()

        filter.update(.30f, .50f, 0L, .95f, 120L)
        filter.update(.55f, .50f, 50L, .95f, 120L)
        filter.update(.55f, .50f, 100L, .95f, 120L)
        val reversed = filter.update(.35f, .50f, 150L, .95f, 120L)

        assertTrue(reversed.first >= .35f)
        assertTrue(reversed.first < .55f)
    }

    @Test
    fun lowResultRateConfirmsSustainedTravelWithoutPrediction() {
        val filter = PointerMotionFilter()

        filter.update(.40f, .50f, 0L, .90f)
        val staged = filter.update(.66f, .50f, 100L, .90f)
        val confirmed = filter.update(.66f, .50f, 200L, .90f)

        assertTrue(staged.first < .45f)
        assertTrue(confirmed.first > .63f)
        assertTrue(confirmed.first <= .66f)
    }

    @Test
    fun slowFingertipArticulationMovesContinuouslyInsteadOfSticking() {
        val filter = PointerMotionFilter()
        filter.update(.500f, .500f, 0L, .95f)

        val outputs =
            listOf(.503f, .506f, .509f, .512f, .515f)
                .mapIndexed { index, rawX ->
                    filter.update(
                        rawX = rawX,
                        rawY = .500f,
                        timestampMs = (index + 1) * 33L,
                        confidence = .95f
                    ).first
                }

        assertTrue(outputs.zipWithNext().all { (a, b) -> b > a })
        assertTrue(outputs.last() > .510f)
        assertTrue(outputs.last() <= .515f)
    }

    @Test
    fun equivalentMotionIsStableAcrossDifferentResultRates() {
        fun run(samples: Int, frameMs: Long): Float {
            val filter = PointerMotionFilter()
            var output = .40f
            for (index in 0..samples) {
                val progress = index.toFloat() / samples.toFloat()
                output =
                    filter.update(
                        rawX = .40f + .30f * progress,
                        rawY = .50f,
                        timestampMs = index * frameMs,
                        confidence = .95f
                    ).first
            }
            return output
        }

        val about30Fps = run(samples = 10, frameMs = 33L)
        val about60Fps = run(samples = 20, frameMs = 16L)

        assertTrue(abs(about30Fps - about60Fps) < .02f)
        assertTrue(about30Fps > .68f)
        assertTrue(about60Fps > .68f)
    }

    @Test
    fun smootherNeverOvershootsLatestMeasuredFingertip() {
        val filter = PointerMotionFilter()
        filter.update(.30f, .50f, 0L, .95f)

        val right = filter.update(.60f, .50f, 40L, .95f)
        assertTrue(right.first in .30f..60f)

        val rightConfirmed = filter.update(.60f, .50f, 80L, .95f)
        val left = filter.update(.42f, .50f, 120L, .95f)
        assertTrue(left.first in .42f..rightConfirmed.first)
    }
}
