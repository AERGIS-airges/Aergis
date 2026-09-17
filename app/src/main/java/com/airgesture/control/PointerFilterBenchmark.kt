package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.sqrt

data class PointerFilterBenchmarkSnapshot(
    val samples: Long,
    val stationarySamples: Long,
    val deliberateMotionSamples: Long,
    val rawStationaryRmsStep: Float,
    val productionStationaryRmsStep: Float,
    val challengerStationaryRmsStep: Float,
    val productionMeanMotionError: Float,
    val challengerMeanMotionError: Float,
    val rawPathLength: Float,
    val productionPathLength: Float,
    val challengerPathLength: Float
)

/**
 * Evidence collector for production PointerMotionFilter vs One Euro challenger.
 *
 * This intentionally does not auto-select a winner. Raw MediaPipe coordinates
 * are measurements rather than ground truth, so the collector records useful
 * comparative evidence without pretending that lower distance-to-raw is always
 * better. Real-device selection still requires user-perceived latency, jitter,
 * overshoot and false-jump testing on the reference Galaxy A54.
 */
class PointerFilterBenchmark(
    private val stationaryRawStepMax: Float = 0.006f,
    private val deliberateRawStepMin: Float = 0.012f
) {
    private var samples = 0L
    private var stationarySamples = 0L
    private var deliberateMotionSamples = 0L

    private var rawStationarySquared = 0.0
    private var productionStationarySquared = 0.0
    private var challengerStationarySquared = 0.0

    private var productionMotionErrorTotal = 0.0
    private var challengerMotionErrorTotal = 0.0

    private var rawPathLength = 0.0
    private var productionPathLength = 0.0
    private var challengerPathLength = 0.0

    private var previousRawX: Float? = null
    private var previousRawY: Float? = null
    private var previousProductionX: Float? = null
    private var previousProductionY: Float? = null
    private var previousChallengerX: Float? = null
    private var previousChallengerY: Float? = null

    init {
        require(stationaryRawStepMax >= 0f && stationaryRawStepMax.isFinite())
        require(deliberateRawStepMin > stationaryRawStepMax)
        require(deliberateRawStepMin.isFinite())
    }

    fun onSample(
        rawX: Float,
        rawY: Float,
        productionX: Float,
        productionY: Float,
        challengerX: Float,
        challengerY: Float
    ) {
        if (
            !rawX.isFinite() || !rawY.isFinite() ||
            !productionX.isFinite() || !productionY.isFinite() ||
            !challengerX.isFinite() || !challengerY.isFinite()
        ) {
            return
        }

        samples++

        val oldRawX = previousRawX
        val oldRawY = previousRawY
        val oldProductionX = previousProductionX
        val oldProductionY = previousProductionY
        val oldChallengerX = previousChallengerX
        val oldChallengerY = previousChallengerY

        if (
            oldRawX != null && oldRawY != null &&
            oldProductionX != null && oldProductionY != null &&
            oldChallengerX != null && oldChallengerY != null
        ) {
            val rawStep =
                hypot(rawX - oldRawX, rawY - oldRawY)
            val productionStep =
                hypot(
                    productionX - oldProductionX,
                    productionY - oldProductionY
                )
            val challengerStep =
                hypot(
                    challengerX - oldChallengerX,
                    challengerY - oldChallengerY
                )

            rawPathLength += rawStep.toDouble()
            productionPathLength += productionStep.toDouble()
            challengerPathLength += challengerStep.toDouble()

            if (rawStep <= stationaryRawStepMax) {
                stationarySamples++
                val rawStepD = rawStep.toDouble()
                val productionStepD = productionStep.toDouble()
                val challengerStepD = challengerStep.toDouble()
                rawStationarySquared += rawStepD * rawStepD
                productionStationarySquared +=
                    productionStepD * productionStepD
                challengerStationarySquared +=
                    challengerStepD * challengerStepD
            }

            if (rawStep >= deliberateRawStepMin) {
                deliberateMotionSamples++
                productionMotionErrorTotal +=
                    hypot(
                        productionX - rawX,
                        productionY - rawY
                    ).toDouble()
                challengerMotionErrorTotal +=
                    hypot(
                        challengerX - rawX,
                        challengerY - rawY
                    ).toDouble()
            }
        }

        previousRawX = rawX
        previousRawY = rawY
        previousProductionX = productionX
        previousProductionY = productionY
        previousChallengerX = challengerX
        previousChallengerY = challengerY
    }

    fun breakContinuity() {
        previousRawX = null
        previousRawY = null
        previousProductionX = null
        previousProductionY = null
        previousChallengerX = null
        previousChallengerY = null
    }

    fun reset() {
        samples = 0L
        stationarySamples = 0L
        deliberateMotionSamples = 0L
        rawStationarySquared = 0.0
        productionStationarySquared = 0.0
        challengerStationarySquared = 0.0
        productionMotionErrorTotal = 0.0
        challengerMotionErrorTotal = 0.0
        rawPathLength = 0.0
        productionPathLength = 0.0
        challengerPathLength = 0.0
        breakContinuity()
    }

    fun snapshot(): PointerFilterBenchmarkSnapshot =
        PointerFilterBenchmarkSnapshot(
            samples = samples,
            stationarySamples = stationarySamples,
            deliberateMotionSamples = deliberateMotionSamples,
            rawStationaryRmsStep =
                rms(rawStationarySquared, stationarySamples),
            productionStationaryRmsStep =
                rms(productionStationarySquared, stationarySamples),
            challengerStationaryRmsStep =
                rms(challengerStationarySquared, stationarySamples),
            productionMeanMotionError =
                mean(productionMotionErrorTotal, deliberateMotionSamples),
            challengerMeanMotionError =
                mean(challengerMotionErrorTotal, deliberateMotionSamples),
            rawPathLength = rawPathLength.toFloat(),
            productionPathLength = productionPathLength.toFloat(),
            challengerPathLength = challengerPathLength.toFloat()
        )

    fun summary(): String {
        val data = snapshot()
        if (data.samples == 0L) {
            return "Pointer filter A/B: no samples"
        }

        return "Pointer filter A/B: n=${data.samples} " +
            "still=${data.stationarySamples} " +
            "rms(raw/prod/oneEuro)=" +
            format(data.rawStationaryRmsStep) + "/" +
            format(data.productionStationaryRmsStep) + "/" +
            format(data.challengerStationaryRmsStep) + " " +
            "move=${data.deliberateMotionSamples} " +
            "err(prod/oneEuro)=" +
            format(data.productionMeanMotionError) + "/" +
            format(data.challengerMeanMotionError)
    }

    private fun rms(sumSquares: Double, count: Long): Float =
        if (count <= 0L) {
            0f
        } else {
            sqrt(sumSquares / count.toDouble()).toFloat()
        }

    private fun mean(total: Double, count: Long): Float =
        if (count <= 0L) 0f
        else (total / count.toDouble()).toFloat()

    private fun format(value: Float): String =
        String.format(
            java.util.Locale.US,
            "%.4f",
            value
        )
}
