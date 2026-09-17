package com.airgesture.control

import kotlin.math.abs
import kotlin.math.sqrt

data class WorldGeometryValidation(
    val reliable: Boolean,
    val handScale: Float = 0f,
    val boneSignature: List<Float> = emptyList(),
    val reason: String = "unavailable"
)

object WorldHandGeometryValidator {
    private val bonePairs = listOf(
        1 to 2, 2 to 3, 3 to 4,
        5 to 6, 6 to 7, 7 to 8,
        9 to 10, 10 to 11, 11 to 12,
        13 to 14, 14 to 15, 15 to 16,
        17 to 18, 18 to 19, 19 to 20
    )

    private val structuralPairs = listOf(
        0 to 5, 0 to 9, 0 to 17,
        5 to 9, 9 to 13, 13 to 17, 5 to 17
    )

    fun validate(
        world: List<PoseLandmark>?,
        image: List<PoseLandmark>? = null,
        imageAspectRatio: Float = 1f
    ): WorldGeometryValidation {
        val worldOnly = validateWorldOnly(world)
        if (!worldOnly.reliable) return worldOnly

        if (image != null) {
            val imageScale = imageHandScale(image, imageAspectRatio)
            if (!imageScale.isFinite() || imageScale <= 0.0001f) {
                return worldOnly.copy(
                    reliable = false,
                    reason = "synchronized image geometry collapsed/non-finite"
                )
            }

            if (!imageWorldStructuresAgree(
                    image = image,
                    world = world!!.take(21),
                    worldScale = worldOnly.handScale,
                    aspectRatio = imageAspectRatio
                )
            ) {
                // A valid but differently projected image is expected when the
                // hand rotates or flexes. Do not veto plausible 3D evidence.
                // A collapsed/invalid image was rejected above; the temporal
                // guard still rejects abrupt changes in the world signature.
                return worldOnly.copy(
                    reason = "world geometry plausible despite image projection disagreement"
                )
            }
        }

        return worldOnly.copy(reason = "world geometry plausible")
    }

    fun validateWorldOnly(
        world: List<PoseLandmark>?
    ): WorldGeometryValidation {
        if (world == null || world.size < 21 || world.take(21).any(::nonFinite)) {
            return WorldGeometryValidation(
                reliable = false,
                reason = "world landmarks missing/non-finite"
            )
        }

        val points = world.take(21)
        val scale = worldHandScale(points)
        if (!scale.isFinite() || scale !in MIN_WORLD_HAND_SCALE_M..MAX_WORLD_HAND_SCALE_M) {
            return WorldGeometryValidation(
                reliable = false,
                handScale = scale,
                reason = "world palm scale implausible"
            )
        }

        val signature = bonePairs.map { (a, b) -> distance3(points[a], points[b]) / scale }
        if (signature.any {
                !it.isFinite() ||
                    it < MIN_BONE_TO_HAND_SCALE ||
                    it > MAX_BONE_TO_HAND_SCALE
            }
        ) {
            return WorldGeometryValidation(
                reliable = false,
                handScale = scale,
                boneSignature = signature,
                reason = "world bone proportions implausible"
            )
        }

        val palmSpan = distance3(points[5], points[17]) / scale
        if (palmSpan !in MIN_PALM_SPAN_RATIO..MAX_PALM_SPAN_RATIO) {
            return WorldGeometryValidation(
                reliable = false,
                handScale = scale,
                boneSignature = signature,
                reason = "world palm span collapsed/outlier"
            )
        }

        return WorldGeometryValidation(
            reliable = true,
            handScale = scale,
            boneSignature = signature,
            reason = "world-only geometry plausible"
        )
    }

    fun imageGeometry(image: List<PoseLandmark>, aspectRatio: Float): List<PoseLandmark> {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it in 0.25f..4f } ?: 1f
        return image.map { PoseLandmark(it.x * safeAspect, it.y, 0f) }
    }

    fun imageHandScale(image: List<PoseLandmark>, aspectRatio: Float): Float {
        if (image.size < 21) return 0f
        val corrected = imageGeometry(image, aspectRatio)
        return (
            distance3(corrected[5], corrected[17]) +
                distance3(corrected[0], corrected[9])
            ) * 0.5f
    }

    fun worldHandScale(world: List<PoseLandmark>): Float {
        if (world.size < 21) return 0f
        return (
            distance3(world[5], world[17]) +
                distance3(world[0], world[9])
            ) * 0.5f
    }

    private fun imageWorldStructuresAgree(
        image: List<PoseLandmark>,
        world: List<PoseLandmark>,
        worldScale: Float,
        aspectRatio: Float
    ): Boolean {
        if (image.size < 21 || image.take(21).any(::nonFinite)) return false

        val corrected = imageGeometry(image, aspectRatio)
        val imageScale = (
            distance3(corrected[5], corrected[17]) +
                distance3(corrected[0], corrected[9])
            ) * 0.5f
        if (!imageScale.isFinite() || imageScale <= 0.0001f) return false

        var agreements = 0
        structuralPairs.forEach { (a, b) ->
            val imageRatio = distance3(corrected[a], corrected[b]) / imageScale
            val worldRatio = distance3(world[a], world[b]) / worldScale
            if (imageRatio.isFinite() && worldRatio.isFinite() && imageRatio > 0.0001f) {
                val ratio = worldRatio / imageRatio
                if (ratio in MIN_IMAGE_WORLD_RATIO..MAX_IMAGE_WORLD_RATIO) agreements++
            }
        }
        return agreements >= MIN_STRUCTURE_AGREEMENTS
    }

    private fun nonFinite(point: PoseLandmark): Boolean =
        !point.x.isFinite() || !point.y.isFinite() || !point.z.isFinite()

    private fun distance3(a: PoseLandmark, b: PoseLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private const val MIN_WORLD_HAND_SCALE_M = 0.012f
    private const val MAX_WORLD_HAND_SCALE_M = 0.30f
    private const val MIN_BONE_TO_HAND_SCALE = 0.035f
    private const val MAX_BONE_TO_HAND_SCALE = 1.20f
    private const val MIN_PALM_SPAN_RATIO = 0.45f
    private const val MAX_PALM_SPAN_RATIO = 1.85f
    private const val MIN_IMAGE_WORLD_RATIO = 0.22f
    private const val MAX_IMAGE_WORLD_RATIO = 4.50f
    private const val MIN_STRUCTURE_AGREEMENTS = 5
}

class WorldGeometryTemporalGuard(
    private val resetGapMs: Long = 650L,
    private val maxMeanRelativeDrift: Float = 0.42f,
    private val maxSingleRelativeDrift: Float = 1.15f
) {
    private var lastTimestampMs: Long? = null
    private var lastSignature: List<Float>? = null

    fun accept(timestampMs: Long, validation: WorldGeometryValidation): Boolean {
        if (!validation.reliable) return false

        val signature = validation.boneSignature
        if (signature.isEmpty()) return false

        val previous = lastSignature
        val previousTime = lastTimestampMs
        if (
            previous == null ||
            previousTime == null ||
            timestampMs < previousTime ||
            timestampMs - previousTime > resetGapMs ||
            previous.size != signature.size
        ) {
            lastSignature = signature
            lastTimestampMs = timestampMs
            return true
        }

        val drifts = signature.zip(previous).map { (current, old) ->
            abs(current - old) / old.coerceAtLeast(0.035f)
        }
        val mean = drifts.average().toFloat()
        val max = drifts.maxOrNull() ?: 0f
        if (mean > maxMeanRelativeDrift || max > maxSingleRelativeDrift) return false

        lastSignature = signature
        lastTimestampMs = timestampMs
        return true
    }

    fun reset() {
        lastTimestampMs = null
        lastSignature = null
    }
}
