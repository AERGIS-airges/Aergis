package com.airgesture.control

import kotlin.math.sqrt

/**
 * Scale-normalized palm skeleton used only as a continuity identity cue.
 *
 * Finger articulation is deliberately excluded: changing from Pointing to a
 * fist or Victory must not make the same physical hand look like a new hand.
 * The signature uses wrist/MCP geometry and can be derived from validated
 * metric world landmarks or aspect-corrected image landmarks.
 */
object HandIdentitySignature {
    private val pairs =
        listOf(
            0 to 5,
            0 to 9,
            0 to 13,
            0 to 17,
            5 to 9,
            9 to 13,
            13 to 17,
            5 to 17
        )

    fun fromGeometry(
        landmarks: List<PoseLandmark>
    ): List<Float> {
        if (
            landmarks.size < 21 ||
            landmarks.take(21).any {
                !it.x.isFinite() ||
                    !it.y.isFinite() ||
                    !it.z.isFinite()
            }
        ) {
            return emptyList()
        }

        val scale =
            (
                distance(
                    landmarks[5],
                    landmarks[17]
                ) +
                    distance(
                        landmarks[0],
                        landmarks[9]
                    )
                ) * 0.5f

        if (
            !scale.isFinite() ||
            scale <= 0.0001f
        ) {
            return emptyList()
        }

        return pairs.map { (a, b) ->
            distance(
                landmarks[a],
                landmarks[b]
            ) / scale
        }
    }

    fun fromImage(
        landmarks: List<PoseLandmark>,
        aspectRatio: Float
    ): List<Float> =
        fromGeometry(
            WorldHandGeometryValidator
                .imageGeometry(
                    image = landmarks,
                    aspectRatio = aspectRatio
                )
        )

    fun distance(
        a: List<Float>,
        b: List<Float>
    ): Float? {
        if (
            a.isEmpty() ||
            b.isEmpty() ||
            a.size != b.size
        ) {
            return null
        }

        val differences =
            a.zip(b).map { (left, right) ->
                kotlin.math.abs(
                    left - right
                ) /
                    maxOf(
                        kotlin.math.abs(right),
                        0.08f
                    )
            }
        return differences
            .average()
            .toFloat()
            .takeIf { it.isFinite() }
    }

    private fun distance(
        a: PoseLandmark,
        b: PoseLandmark
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(
            dx * dx +
                dy * dy +
                dz * dz
        )
    }
}
