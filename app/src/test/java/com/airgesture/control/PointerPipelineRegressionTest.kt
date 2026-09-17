package com.airgesture.control

import org.junit.Assert.assertTrue
import org.junit.Test

class PointerPipelineRegressionTest {

    private fun p(
        x: Float,
        y: Float,
        z: Float = 0f
    ) = AimLandmark(x, y, z)

    private fun fingertipAt(
        x: Float,
        y: Float = .26f
    ) = PointerAimInput(
        indexTip = p(x, y)
    )

    @Test
    fun horizontalDirectionRemainsMonotonicThroughAimCalibrationAndFilter() {
        val estimator = PointerAimEstimator()
        val tracker = PointerTracker()

        val leftAim =
            estimator.estimate(
                fingertipAt(.30f)
            )
        val left =
            tracker.update(
                PointerFrame(
                    timestampMs = 0L,
                    handPresent = true,
                    aimX = leftAim.x,
                    aimY = leftAim.y,
                    aimConfidence =
                        leftAim.confidence,
                    aimStable = leftAim.stable
                )
            )

        val rightAim =
            estimator.estimate(
                fingertipAt(.70f)
            )
        val stagedRight =
            tracker.update(
                PointerFrame(
                    timestampMs = 50L,
                    handPresent = true,
                    aimX = rightAim.x,
                    aimY = rightAim.y,
                    aimConfidence =
                        rightAim.confidence,
                    aimStable = rightAim.stable
                )
            )
        val confirmedRight =
            tracker.update(
                PointerFrame(
                    timestampMs = 100L,
                    handPresent = true,
                    aimX = rightAim.x,
                    aimY = rightAim.y,
                    aimConfidence =
                        rightAim.confidence,
                    aimStable = rightAim.stable
                )
            )

        assertTrue(leftAim.x < rightAim.x)
        assertTrue(left.x < stagedRight.x)
        assertTrue(stagedRight.x < confirmedRight.x)
        assertTrue(
            confirmedRight.x - left.x > .40f
        )
    }

    @Test
    fun quickDirectionReversalFollowsLatestFingertipWithoutOvershoot() {
        val estimator = PointerAimEstimator()
        val tracker = PointerTracker()

        fun update(
            time: Long,
            x: Float
        ): PointerDecision {
            val aim =
                estimator.estimate(
                    fingertipAt(x)
                )
            return tracker.update(
                PointerFrame(
                    timestampMs = time,
                    handPresent = true,
                    aimX = aim.x,
                    aimY = aim.y,
                    aimConfidence =
                        aim.confidence,
                    aimStable = aim.stable
                )
            )
        }

        update(0L, .35f)
        val stagedRight = update(45L, .75f)
        val right = update(90L, .75f)
        val stagedLeft = update(135L, .28f)
        val left = update(180L, .28f)

        // R18 deliberately step-limits the first large reversal sample.
        // Therefore the first leftward sample may equal the prior rightward
        // position; the second coherent sample must move left. The pointer
        // must never overshoot the newest measured/mapped fingertip target.
        assertTrue(stagedRight.x < right.x)
        assertTrue(
            "right.x=${right.x} must not overshoot newest mapped measurement .859375",
            right.x <= .859375f
        )
        assertTrue(
            "stagedLeft.x=${stagedLeft.x} must not overshoot prior right.x=${right.x}; " +
                "left.x=${left.x}, stagedRight.x=${stagedRight.x}",
            stagedLeft.x <= right.x
        )
        assertTrue(
            "left.x=${left.x} must move left after the reversal; " +
                "stagedLeft.x=${stagedLeft.x}, right.x=${right.x}",
            left.x < right.x
        )
        assertTrue(left.x < .30f)
        assertTrue(left.x >= 0f)
        assertTrue(left.x <= 1f)
    }
}
