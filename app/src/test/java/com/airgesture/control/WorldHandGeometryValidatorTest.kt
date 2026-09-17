package com.airgesture.control

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldHandGeometryValidatorTest {
    private fun p(
        x: Float,
        y: Float,
        z: Float = 0f
    ) = PoseLandmark(x, y, z)

    private fun metricHand(): List<PoseLandmark> {
        val h = MutableList(21) { p(0f, 0f, 0f) }
        h[0] = p(0f, .052f, .004f)
        h[1] = p(-.030f, .030f, .002f)
        h[2] = p(-.047f, .020f, .001f)
        h[3] = p(-.063f, .009f, 0f)
        h[4] = p(-.079f, -.003f, -.002f)
        h[5] = p(-.036f, .002f, 0f)
        h[6] = p(-.038f, -.026f, -.002f)
        h[7] = p(-.039f, -.052f, -.004f)
        h[8] = p(-.040f, -.078f, -.006f)
        h[9] = p(-.011f, -.004f, 0f)
        h[10] = p(-.011f, -.034f, -.002f)
        h[11] = p(-.011f, -.062f, -.004f)
        h[12] = p(-.011f, -.090f, -.006f)
        h[13] = p(.016f, .001f, 0f)
        h[14] = p(.018f, -.028f, -.001f)
        h[15] = p(.020f, -.054f, -.003f)
        h[16] = p(.022f, -.078f, -.005f)
        h[17] = p(.039f, .009f, .001f)
        h[18] = p(.043f, -.016f, 0f)
        h[19] = p(.047f, -.038f, -.002f)
        h[20] = p(.051f, -.059f, -.004f)
        return h
    }

    private fun project(
        world: List<PoseLandmark>,
        gain: Float = 4f
    ): List<PoseLandmark> =
        world.map {
            p(
                .50f + it.x * gain,
                .58f + it.y * gain,
                0f
            )
        }

    private fun scale(
        points: List<PoseLandmark>,
        factor: Float
    ): List<PoseLandmark> =
        points.map {
            p(
                it.x * factor,
                it.y * factor,
                it.z * factor
            )
        }

    private fun rotate(
        points: List<PoseLandmark>,
        rollDegrees: Float = 0f,
        pitchDegrees: Float = 0f,
        yawDegrees: Float = 0f
    ): List<PoseLandmark> {
        val roll =
            Math.toRadians(
                rollDegrees.toDouble()
            ).toFloat()
        val pitch =
            Math.toRadians(
                pitchDegrees.toDouble()
            ).toFloat()
        val yaw =
            Math.toRadians(
                yawDegrees.toDouble()
            ).toFloat()

        return points.map { source ->
            var x = source.x
            var y = source.y
            var z = source.z

            run {
                val nx =
                    x * cos(roll) -
                        y * sin(roll)
                val ny =
                    x * sin(roll) +
                        y * cos(roll)
                x = nx
                y = ny
            }
            run {
                val ny =
                    y * cos(pitch) -
                        z * sin(pitch)
                val nz =
                    y * sin(pitch) +
                        z * cos(pitch)
                y = ny
                z = nz
            }
            run {
                val nx =
                    x * cos(yaw) +
                        z * sin(yaw)
                val nz =
                    -x * sin(yaw) +
                        z * cos(yaw)
                x = nx
                z = nz
            }

            p(x, y, z)
        }
    }

    @Test
    fun plausibleMetricHandIsWorldBacked() {
        val world = metricHand()
        val result =
            WorldHandGeometryValidator
                .validate(
                    world = world,
                    image = project(world)
                )

        assertTrue(result.reliable)
        assertTrue(result.handScale in .03f..12f)
        assertTrue(result.boneSignature.isNotEmpty())
    }

    @Test
    fun collapsedFiniteWorldHandIsUnknownNotFoldedEvidence() {
        val collapsed =
            List(21) {
                p(.01f, .01f, .01f)
            }

        val result =
            WorldHandGeometryValidator
                .validate(
                    world = collapsed,
                    image = project(metricHand())
                )

        assertFalse(result.reliable)
    }

    @Test
    fun finiteWorldOutlierIsRejected() {
        val outlier =
            metricHand()
                .toMutableList()
                .apply {
                    this[8] =
                        p(-.04f, -.35f, -.01f)
                }

        assertFalse(
            WorldHandGeometryValidator
                .validate(
                    world = outlier,
                    image = project(metricHand())
                )
                .reliable
        )
    }

    @Test
    fun uniformMetricScaleDoesNotChangeReliability() {
        for (factor in listOf(.55f, 1f, 1.55f)) {
            val world =
                scale(
                    metricHand(),
                    factor
                )
            assertTrue(
                WorldHandGeometryValidator
                    .validate(
                        world = world,
                        image = project(world)
                    )
                    .reliable
            )
        }
    }

    @Test
    fun worldGeometrySurvivesReal3dRollPitchYawTransforms() {
        val cases =
            listOf(
                Triple(35f, 0f, 0f),
                Triple(0f, 35f, 0f),
                Triple(0f, 0f, 45f),
                Triple(25f, -25f, 35f)
            )

        cases.forEach { (roll, pitch, yaw) ->
            val world =
                rotate(
                    metricHand(),
                    rollDegrees = roll,
                    pitchDegrees = pitch,
                    yawDegrees = yaw
                )
            val result =
                WorldHandGeometryValidator
                    .validate(
                        world = world,
                        image = project(world)
                    )
            assertTrue(result.reliable)
        }
    }

    @Test
    fun imageWorldStructuralMismatchIsRejected() {
        val world = metricHand()
        val collapsedImage =
            List(21) {
                p(.5f, .5f, 0f)
            }

        assertFalse(
            WorldHandGeometryValidator
                .validate(
                    world = world,
                    image = collapsedImage
                )
                .reliable
        )
    }

    @Test
    fun temporalGuardAcceptsRotationButRejectsSuddenBoneMutation() {
        val guard =
            WorldGeometryTemporalGuard()

        val firstWorld = metricHand()
        val first =
            WorldHandGeometryValidator
                .validate(
                    world = firstWorld,
                    image = project(firstWorld)
                )
        assertTrue(guard.accept(1_000L, first))

        val rotated =
            rotate(
                firstWorld,
                rollDegrees = 30f,
                pitchDegrees = 20f,
                yawDegrees = -30f
            )
        val rotatedValidation =
            WorldHandGeometryValidator
                .validate(
                    world = rotated,
                    image = project(rotated)
                )
        assertTrue(
            guard.accept(
                1_050L,
                rotatedValidation
            )
        )

        val mutated =
            firstWorld
                .toMutableList()
                .apply {
                    this[8] =
                        p(-.040f, -.128f, -.006f)
                }
        val mutatedValidation =
            WorldHandGeometryValidator
                .validate(
                    world = mutated,
                    image = project(mutated)
                )
        assertTrue(mutatedValidation.reliable)
        assertFalse(
            guard.accept(
                1_100L,
                mutatedValidation
            )
        )
    }
}
