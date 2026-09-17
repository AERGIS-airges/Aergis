package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionalNavigationGestureTest {

    private enum class Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        CAMERA
    }

    private fun doubleGun(
        direction: Direction
    ): List<PoseLandmark> {
        val points =
            MutableList(21) {
                PoseLandmark(.50f, .72f, 0f)
            }

        points[0] = PoseLandmark(.50f, .82f, 0f)
        points[1] = PoseLandmark(.43f, .70f, 0f)

        points[5] = PoseLandmark(.44f, .58f, 0f)
        points[9] = PoseLandmark(.50f, .56f, 0f)
        points[13] = PoseLandmark(.56f, .58f, 0f)
        points[17] = PoseLandmark(.62f, .62f, 0f)

        // Thumb extended sideways.
        points[2] = PoseLandmark(.38f, .67f, 0f)
        points[3] = PoseLandmark(.31f, .63f, 0f)
        points[4] = PoseLandmark(.24f, .58f, 0f)

        fun straightFinger(
            mcp: Int,
            pip: Int,
            dip: Int,
            tip: Int,
            direction: Direction
        ) {
            val start = points[mcp]
            val dx: Float
            val dy: Float
            val dz: Float

            when (direction) {
                Direction.UP -> {
                    dx = 0f
                    dy = -.085f
                    dz = 0f
                }
                Direction.DOWN -> {
                    dx = 0f
                    dy = .070f
                    dz = 0f
                }
                Direction.LEFT -> {
                    dx = -.070f
                    dy = 0f
                    dz = 0f
                }
                Direction.RIGHT -> {
                    dx = .070f
                    dy = 0f
                    dz = 0f
                }
                Direction.CAMERA -> {
                    dx = .006f
                    dy = -.004f
                    dz = -.085f
                }
            }

            points[pip] =
                PoseLandmark(
                    start.x + dx,
                    start.y + dy,
                    start.z + dz
                )
            points[dip] =
                PoseLandmark(
                    start.x + dx * 2f,
                    start.y + dy * 2f,
                    start.z + dz * 2f
                )
            points[tip] =
                PoseLandmark(
                    start.x + dx * 3f,
                    start.y + dy * 3f,
                    start.z + dz * 3f
                )
        }

        straightFinger(5, 6, 7, 8, direction)
        straightFinger(9, 10, 11, 12, direction)

        // Ring + pinky folded.
        points[14] = PoseLandmark(.56f, .54f, 0f)
        points[15] = PoseLandmark(.595f, .59f, 0f)
        points[16] = PoseLandmark(.575f, .635f, 0f)

        points[18] = PoseLandmark(.62f, .57f, 0f)
        points[19] = PoseLandmark(.655f, .62f, 0f)
        points[20] = PoseLandmark(.635f, .665f, 0f)

        return points
    }

    @Test
    fun doubleGunUpMapsToScrollUpSignal() {
        val result =
            LandmarkPoseRecognizer().recognize(
                doubleGun(Direction.UP)
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_DOUBLE_GUN_UP,
            result?.category
        )
        assertTrue((result?.score ?: 0f) >= .78f)
    }

    @Test
    fun doubleGunDownMapsToScrollDownSignal() {
        val result =
            LandmarkPoseRecognizer().recognize(
                doubleGun(Direction.DOWN)
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_DOUBLE_GUN_DOWN,
            result?.category
        )
    }

    @Test
    fun mirroredImageLeftMapsToUserScrollRightSignal() {
        val result =
            LandmarkPoseRecognizer().recognize(
                doubleGun(Direction.LEFT)
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_DOUBLE_GUN_RIGHT,
            result?.category
        )
    }

    @Test
    fun mirroredImageRightMapsToUserScrollLeftSignal() {
        val result =
            LandmarkPoseRecognizer().recognize(
                doubleGun(Direction.RIGHT)
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_DOUBLE_GUN_LEFT,
            result?.category
        )
    }

    @Test
    fun doubleGunPointedAtCameraIsNeutral() {
        val result =
            LandmarkPoseRecognizer().recognize(
                doubleGun(Direction.CAMERA)
            )

        assertNull(result)
    }

    @Test
    fun navigationCategoriesResolveToDedicatedSignals() {
        assertEquals(
            GestureSignal.NAV_SCROLL_UP,
            LandmarkPoseRecognizer.signalForCategory(
                LandmarkPoseRecognizer
                    .CATEGORY_DOUBLE_GUN_UP
            )
        )
        assertEquals(
            GestureSignal.NAV_SCROLL_RIGHT,
            LandmarkPoseRecognizer.signalForCategory(
                LandmarkPoseRecognizer
                    .CATEGORY_DOUBLE_GUN_RIGHT
            )
        )
    }
}
