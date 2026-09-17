package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.sqrt

data class PointerTipContactResult(
    val normalizedSeparation: Float,
    val reliable: Boolean,
    val source: String
)

/**
 * Measures the physical relation required by the pointer-click contract:
 * index fingertip #8 to middle fingertip #12.
 *
 * R11 adds an intent-safety requirement: proximity alone is not enough. Both
 * index and middle fingers must also be sufficiently unfolded/straight for the
 * contact measurement to be considered actionable. This keeps a relaxed,
 * naturally curled hand from turning ordinary finger proximity into a click.
 *
 * Trusted world landmarks are preferred for contact because their 3D distance
 * remains meaningful when the hand rotates or the wrist bends. Unlike the
 * general world-geometry gate, contact does not require image/world structural
 * agreement: that projection check can reject valid 3D geometry precisely when
 * the camera view changes due to hand orientation.
 */
object PointerTipContactEstimator {
    private const val MIN_CLICK_FINGER_STRAIGHTNESS = 0.72f

    fun estimate(
        imageHand: List<AimLandmark>,
        imageHandScale: Float,
        imageAspectRatio: Float,
        trustedWorldHand: List<AimLandmark>? = null,
        worldHandScale: Float? = null
    ): PointerTipContactResult {
        val worldScale =
            worldHandScale
                ?.takeIf { it.isFinite() && it > 0.0001f }
        val world =
            trustedWorldHand
                ?.takeIf {
                    it.size >= 21 &&
                        worldScale != null &&
                        clickFingerLandmarksFinite(it)
                }

        if (world != null && worldScale != null) {
            val separation =
                distance3(world[8], world[12]) / worldScale
            val postureReliable =
                fingerStraightness3(
                    world,
                    mcp = 5,
                    pip = 6,
                    dip = 7,
                    tip = 8
                ) >= MIN_CLICK_FINGER_STRAIGHTNESS &&
                    fingerStraightness3(
                        world,
                        mcp = 9,
                        pip = 10,
                        dip = 11,
                        tip = 12
                    ) >= MIN_CLICK_FINGER_STRAIGHTNESS

            return result(
                normalizedSeparation = separation,
                postureReliable = postureReliable,
                source = "world #8↔#12 + unfolded index/middle"
            )
        }

        if (
            imageHand.size < 21 ||
            !imageHandScale.isFinite() ||
            imageHandScale <= 0.0001f ||
            !clickFingerLandmarksFinite(imageHand)
        ) {
            return PointerTipContactResult(
                normalizedSeparation = Float.POSITIVE_INFINITY,
                reliable = false,
                source = "unavailable"
            )
        }

        val aspect =
            imageAspectRatio
                .takeIf { it.isFinite() && it in 0.25f..4f }
                ?: 1f
        val dx = (imageHand[8].x - imageHand[12].x) * aspect
        val dy = imageHand[8].y - imageHand[12].y
        val separation = hypot(dx, dy) / imageHandScale
        val postureReliable =
            fingerStraightness2(
                imageHand,
                aspect = aspect,
                mcp = 5,
                pip = 6,
                dip = 7,
                tip = 8
            ) >= MIN_CLICK_FINGER_STRAIGHTNESS &&
                fingerStraightness2(
                    imageHand,
                    aspect = aspect,
                    mcp = 9,
                    pip = 10,
                    dip = 11,
                    tip = 12
                ) >= MIN_CLICK_FINGER_STRAIGHTNESS

        return result(
            normalizedSeparation = separation,
            postureReliable = postureReliable,
            source = "image #8↔#12 + unfolded index/middle"
        )
    }

    private fun result(
        normalizedSeparation: Float,
        postureReliable: Boolean,
        source: String
    ): PointerTipContactResult {
        val separationReliable =
            normalizedSeparation.isFinite() &&
                normalizedSeparation >= 0f &&
                normalizedSeparation <= 8f
        return PointerTipContactResult(
            normalizedSeparation =
                if (separationReliable) {
                    normalizedSeparation
                } else {
                    Float.POSITIVE_INFINITY
                },
            reliable = separationReliable && postureReliable,
            source =
                if (postureReliable) {
                    source
                } else {
                    "$source • relaxed/curled geometry suppressed"
                }
        )
    }

    private fun clickFingerLandmarksFinite(
        points: List<AimLandmark>
    ): Boolean =
        listOf(5, 6, 7, 8, 9, 10, 11, 12)
            .all { index ->
                index < points.size && finite(points[index])
            }

    private fun fingerStraightness2(
        points: List<AimLandmark>,
        aspect: Float,
        mcp: Int,
        pip: Int,
        dip: Int,
        tip: Int
    ): Float {
        val chord = distance2(points[mcp], points[tip], aspect)
        val path =
            distance2(points[mcp], points[pip], aspect) +
                distance2(points[pip], points[dip], aspect) +
                distance2(points[dip], points[tip], aspect)
        if (!chord.isFinite() || !path.isFinite() || path <= 0.0001f) {
            return 0f
        }
        return (chord / path).coerceIn(0f, 1f)
    }

    private fun fingerStraightness3(
        points: List<AimLandmark>,
        mcp: Int,
        pip: Int,
        dip: Int,
        tip: Int
    ): Float {
        val chord = distance3(points[mcp], points[tip])
        val path =
            distance3(points[mcp], points[pip]) +
                distance3(points[pip], points[dip]) +
                distance3(points[dip], points[tip])
        if (!chord.isFinite() || !path.isFinite() || path <= 0.0001f) {
            return 0f
        }
        return (chord / path).coerceIn(0f, 1f)
    }

    private fun distance2(
        a: AimLandmark,
        b: AimLandmark,
        aspect: Float
    ): Float {
        val dx = (a.x - b.x) * aspect
        val dy = a.y - b.y
        return hypot(dx, dy)
    }

    private fun finite(point: AimLandmark): Boolean =
        point.x.isFinite() &&
            point.y.isFinite() &&
            point.z.isFinite()

    private fun distance3(
        a: AimLandmark,
        b: AimLandmark
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
