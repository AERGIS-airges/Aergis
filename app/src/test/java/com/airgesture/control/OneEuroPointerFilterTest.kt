package com.airgesture.control

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OneEuroPointerFilterTest {

    @Test
    fun stationaryNoiseIsDampedWithoutOvershoot() {
        val filter = OneEuroPointerFilter()
        val first = filter.update(.500f, .500f, 0L)
        val next = filter.update(.504f, .500f, 33L)

        assertTrue(next.first > first.first)
        assertTrue(next.first < .504f)
    }

    @Test
    fun outputAlwaysStaysBetweenPreviousOutputAndLatestMeasurement() {
        val filter = OneEuroPointerFilter(
            minCutoffHz = 1f,
            beta = .15f,
            derivativeCutoffHz = 1f
        )
        val first = filter.update(.30f, .50f, 0L)
        val right = filter.update(.70f, .50f, 33L)
        val latestMeasurement = .40f
        val next = filter.update(latestMeasurement, .50f, 66L)

        assertTrue(
            right.first in
                min(first.first, .70f)..max(first.first, .70f)
        )
        assertTrue(
            next.first in
                min(right.first, latestMeasurement)..
                    max(right.first, latestMeasurement)
        )
    }

    @Test
    fun fasterMotionRaisesResponsivenessWhenBetaIsPositive() {
        val fixed = OneEuroPointerFilter(
            minCutoffHz = 1f,
            beta = 0f,
            derivativeCutoffHz = 1f
        )
        val adaptive = OneEuroPointerFilter(
            minCutoffHz = 1f,
            beta = .5f,
            derivativeCutoffHz = 1f
        )

        fixed.update(.30f, .50f, 0L)
        adaptive.update(.30f, .50f, 0L)

        val fixedMove = fixed.update(.80f, .50f, 33L)
        val adaptiveMove = adaptive.update(.80f, .50f, 33L)

        assertTrue(adaptiveMove.first > fixedMove.first)
        assertTrue(adaptiveMove.first <= .80f)
    }

    @Test
    fun nonFiniteSampleCannotPoisonState() {
        val filter = OneEuroPointerFilter()
        val stable = filter.update(.45f, .55f, 0L)
        val bad = filter.update(Float.NaN, Float.NaN, 33L)

        assertTrue(bad.first.isFinite())
        assertTrue(bad.second.isFinite())
        assertTrue(abs(bad.first - stable.first) < .0001f)
        assertTrue(abs(bad.second - stable.second) < .0001f)
    }

    @Test
    fun resetStartsFromNextMeasurementExactly() {
        val filter = OneEuroPointerFilter()
        filter.update(.20f, .30f, 0L)
        filter.update(.80f, .70f, 33L)
        filter.reset()

        val restarted = filter.update(.61f, .42f, 200L)

        assertTrue(abs(restarted.first - .61f) < .0001f)
        assertTrue(abs(restarted.second - .42f) < .0001f)
    }
}
