package com.airgesture.control

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

data class LandmarkPoseResult(
    val category: String,
    val score: Float,
    val worldBacked: Boolean = false
)

data class ThumbExtensionEvidence(
    val score: Float,
    val worldBacked: Boolean,
    val worldScore: Float? = null
)

/**
 * Geometry recognizer used as an independent fallback beside MediaPipe's
 * learned gesture classifier.
 *
 * It intentionally recognizes both custom app poses and the common built-in
 * shapes. Recognition remains multi-evidence: finger extension/fold state,
 * reach, direction where relevant, and ambiguity margin all contribute.
 */
class LandmarkPoseRecognizer(
    private val minAcceptScore: Float = 0.70f,
    private val minAmbiguityMargin: Float = 0.035f
) {
    fun recognize(
        landmarks: List<PoseLandmark>,
        worldLandmarks: List<PoseLandmark>? = null,
        imageAspectRatio: Float = 1f
    ): LandmarkPoseResult? {
        if (!validHand(landmarks)) return null

        val imageGeometry =
            WorldHandGeometryValidator
                .imageGeometry(
                    image = landmarks,
                    aspectRatio = imageAspectRatio
                )

        // MediaPipe emits two synchronized 21-point hand maps:
        // normalized image landmarks for screen direction, and world landmarks
        // for rotation-resistant 3D geometry. World geometry is preferred for
        // finger bend/extension so a side-rotated fist cannot masquerade as a
        // thumb gesture merely because part of the thumb projects outside the
        // palm in 2D.
        val worldValidation =
            WorldHandGeometryValidator
                .validate(
                    world = worldLandmarks,
                    image = landmarks,
                    imageAspectRatio =
                        imageAspectRatio
                )
        val worldGeometry =
            worldLandmarks
                ?.takeIf {
                    worldValidation.reliable
                }
        val imageScale =
            handScale(imageGeometry)
                .coerceAtLeast(0.035f)
        val worldScale =
            worldGeometry
                ?.let {
                    worldValidation.handScale
                }
                ?.coerceAtLeast(0.0001f)

        fun fingerScore(
            mcp: Int,
            pip: Int,
            dip: Int,
            tip: Int
        ): Float {
            val imageScore =
                fingerExtendedScore(
                    landmarks = imageGeometry,
                    scale = imageScale,
                    minScale = 0.035f,
                    mcp = mcp,
                    pip = pip,
                    dip = dip,
                    tip = tip
                )
            val world = worldGeometry
                ?: return imageScore
            val metricScale = worldScale
                ?: return imageScore
            val worldScore =
                fingerExtendedScore(
                    landmarks = world,
                    scale = metricScale,
                    minScale = 0.0001f,
                    mcp = mcp,
                    pip = pip,
                    dip = dip,
                    tip = tip
                )
            return dualSpaceEvidence(
                imageScore = imageScore,
                worldScore = worldScore,
                worldWeight = WORLD_FINGER_WEIGHT
            )
        }

        val index = fingerScore(5, 6, 7, 8)
        val middle = fingerScore(9, 10, 11, 12)
        val ring = fingerScore(13, 14, 15, 16)
        val pinky = fingerScore(17, 18, 19, 20)

        val thumb =
            thumbExtensionEvidence(
                landmarks = landmarks,
                worldLandmarks = worldGeometry,
                imageAspectRatio =
                    imageAspectRatio
            )?.score ?: 0f

        fun pinchScore(
            points: List<PoseLandmark>,
            scale: Float
        ): Float =
            (
                1f -
                    normalized(
                        distance(
                            points[4],
                            points[8]
                        ) / scale,
                        0.18f,
                        0.62f
                    )
                ).coerceIn(0f, 1f)

        val imagePinch =
            pinchScore(
                imageGeometry,
                imageScale
            )
        val pinch =
            worldGeometry?.let { world ->
                dualSpaceEvidence(
                    imageScore = imagePinch,
                    worldScore =
                        pinchScore(
                            world,
                            worldScale
                                ?: imageScale
                        ),
                    worldWeight =
                        WORLD_PINCH_WEIGHT
                )
            } ?: imagePinch

        val indexFold = 1f - index
        val middleFold = 1f - middle
        val ringFold = 1f - ring
        val pinkyFold = 1f - pinky
        val thumbFold = 1f - thumb

        val indexUp =
            directionEvidence(
                from = imageGeometry[5],
                to = imageGeometry[8],
                targetX = 0f,
                targetY = -1f
            )
        val thumbUp =
            directionEvidence(
                from = imageGeometry[2],
                to = imageGeometry[4],
                targetX = 0f,
                targetY = -1f
            )
        val thumbDown =
            directionEvidence(
                from = imageGeometry[2],
                to = imageGeometry[4],
                targetX = 0f,
                targetY = 1f
            )

        val doubleGunPoseScore =
            patternScore(
                thumb,
                index,
                middle,
                ringFold,
                pinkyFold
            )
        val directionalNavigation =
            directionalNavigationResult(
                landmarks = imageGeometry,
                scale = imageScale,
                poseScore = doubleGunPoseScore,
                thumbScore = thumb,
                indexScore = index,
                middleScore = middle,
                ringFoldScore = ringFold,
                pinkyFoldScore = pinkyFold
            )

        val candidates =
            buildList {
                directionalNavigation?.let(::add)

                // Common MediaPipe categories: geometry fallback makes a
                // momentary classifier confidence dip recoverable.
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_OPEN_PALM,
                        score =
                            patternScore(
                                thumb,
                                index,
                                middle,
                                ring,
                                pinky
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_CLOSED_FIST,
                        score =
                            patternScore(
                                thumbFold,
                                indexFold,
                                middleFold,
                                ringFold,
                                pinkyFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_POINTING_UP,
                        score =
                            patternScore(
                                index,
                                middleFold,
                                ringFold,
                                pinkyFold,
                                thumbFold,
                                indexUp
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_VICTORY,
                        score =
                            patternScore(
                                index,
                                middle,
                                ringFold,
                                pinkyFold,
                                thumbFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_I_LOVE_YOU,
                        score =
                            thumbRequiredPatternScore(
                                thumbExtension = thumb,
                                index,
                                pinky,
                                middleFold,
                                ringFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_THUMB_UP,
                        score =
                            thumbGestureScore(
                                thumbExtension = thumb,
                                direction = thumbUp,
                                indexFold = indexFold,
                                middleFold = middleFold,
                                ringFold = ringFold,
                                pinkyFold = pinkyFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_THUMB_DOWN,
                        score =
                            thumbGestureScore(
                                thumbExtension = thumb,
                                direction = thumbDown,
                                indexFold = indexFold,
                                middleFold = middleFold,
                                ringFold = ringFold,
                                pinkyFold = pinkyFold
                            )
                    )
                )

                // App-specific poses.
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_OK_SIGN,
                        score =
                            patternScore(
                                pinch,
                                1f - index,
                                middle,
                                ring,
                                pinky
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_CALL_ME,
                        score =
                            thumbRequiredPatternScore(
                                thumbExtension = thumb,
                                pinky,
                                indexFold,
                                middleFold,
                                ringFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_ROCK_SIGN,
                        score =
                            patternScore(
                                index,
                                pinky,
                                middleFold,
                                ringFold,
                                thumbFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_L_SHAPE,
                        score =
                            thumbRequiredPatternScore(
                                thumbExtension = thumb,
                                index,
                                middleFold,
                                ringFold,
                                pinkyFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_FOUR_FINGERS,
                        score =
                            patternScore(
                                index,
                                middle,
                                ring,
                                pinky,
                                thumbFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_THREE_FINGERS,
                        score =
                            patternScore(
                                index,
                                middle,
                                ring,
                                pinkyFold,
                                thumbFold
                            )
                    )
                )
                add(
                    LandmarkPoseResult(
                        category = CATEGORY_PINKY_UP,
                        score =
                            patternScore(
                                pinky,
                                indexFold,
                                middleFold,
                                ringFold,
                                thumbFold
                            )
                    )
                )
            }

        val ranked =
            candidates.sortedByDescending {
                it.score
            }
        val best =
            ranked.firstOrNull()
                ?: return null
        val runnerUp =
            ranked.getOrNull(1)?.score ?: 0f
        val margin =
            best.score - runnerUp

        return best.takeIf {
            it.score >= minAcceptScore &&
                margin >= minAmbiguityMargin
        }?.copy(
            worldBacked =
                worldGeometry != null
        )
    }

    fun thumbExtensionEvidence(
        landmarks: List<PoseLandmark>,
        worldLandmarks: List<PoseLandmark>? = null,
        imageAspectRatio: Float = 1f
    ): ThumbExtensionEvidence? {
        if (!validHand(landmarks)) return null

        val imageGeometry =
            WorldHandGeometryValidator
                .imageGeometry(
                    image = landmarks,
                    aspectRatio = imageAspectRatio
                )
        val imageScale =
            handScale(imageGeometry)
                .coerceAtLeast(0.035f)
        val imageThumb =
            thumbExtendedScore(
                imageGeometry,
                imageScale
            )
        val worldValidation =
            WorldHandGeometryValidator
                .validate(
                    world = worldLandmarks,
                    image = landmarks,
                    imageAspectRatio =
                        imageAspectRatio
                )
        val world =
            worldLandmarks
                ?.takeIf {
                    worldValidation.reliable
                }
                ?: return ThumbExtensionEvidence(
                    score = imageThumb,
                    worldBacked = false,
                    worldScore = null
                )
        val worldScale =
            worldValidation.handScale
                .coerceAtLeast(0.0001f)

        val metricThumb =
            thumbExtendedScore(
                world,
                worldScale
            )
        return ThumbExtensionEvidence(
            score =
                dualSpaceEvidence(
                    imageScore = imageThumb,
                    worldScore = metricThumb,
                    worldWeight = WORLD_THUMB_WEIGHT
                ),
            worldBacked = true,
            worldScore = metricThumb
        )
    }

    private fun directionalNavigationResult(
        landmarks: List<PoseLandmark>,
        scale: Float,
        poseScore: Float,
        thumbScore: Float,
        indexScore: Float,
        middleScore: Float,
        ringFoldScore: Float,
        pinkyFoldScore: Float
    ): LandmarkPoseResult? {
        if (
            poseScore < 0.70f ||
            thumbScore < 0.68f ||
            indexScore < 0.72f ||
            middleScore < 0.72f ||
            ringFoldScore < 0.68f ||
            pinkyFoldScore < 0.68f
        ) {
            return null
        }

        val indexDx =
            landmarks[8].x - landmarks[5].x
        val indexDy =
            landmarks[8].y - landmarks[5].y
        val middleDx =
            landmarks[12].x - landmarks[9].x
        val middleDy =
            landmarks[12].y - landmarks[9].y

        val indexLength =
            hypot(indexDx, indexDy)
        val middleLength =
            hypot(middleDx, middleDy)
        val projectedReach =
            (
                (indexLength + middleLength) *
                    0.5f
                ) / scale.coerceAtLeast(0.035f)

        if (
            indexLength < 0.0001f ||
            middleLength < 0.0001f ||
            projectedReach < 0.38f
        ) {
            return null
        }

        val indexDirX = indexDx / indexLength
        val indexDirY = indexDy / indexLength
        val middleDirX = middleDx / middleLength
        val middleDirY = middleDy / middleLength

        val alignment =
            (
                indexDirX * middleDirX +
                    indexDirY * middleDirY
                ).coerceIn(-1f, 1f)
        if (alignment < 0.58f) return null

        val combinedX = indexDirX + middleDirX
        val combinedY = indexDirY + middleDirY
        val combinedLength =
            hypot(combinedX, combinedY)
        if (combinedLength < 0.0001f) {
            return null
        }

        val directionX = combinedX / combinedLength
        val directionY = combinedY / combinedLength
        val primary =
            max(abs(directionX), abs(directionY))
        val secondary =
            min(abs(directionX), abs(directionY))
        val dominance =
            primary / max(secondary, 0.04f)

        if (dominance < 1.12f) {
            return null
        }

        val category =
            if (abs(directionY) > abs(directionX)) {
                if (directionY < 0f) {
                    CATEGORY_DOUBLE_GUN_UP
                } else {
                    CATEGORY_DOUBLE_GUN_DOWN
                }
            } else {
                // The recognizer sees mirrored front-camera coordinates, so
                // horizontal navigation follows the same user-perspective
                // convention as broad swipes.
                if (directionX < 0f) {
                    CATEGORY_DOUBLE_GUN_RIGHT
                } else {
                    CATEGORY_DOUBLE_GUN_LEFT
                }
            }

        val alignmentScore =
            normalized(
                alignment,
                0.58f,
                0.98f
            )
        val dominanceScore =
            normalized(
                dominance,
                1.12f,
                2.20f
            )
        val planarScore =
            normalized(
                projectedReach,
                0.38f,
                1.25f
            )

        val confidence =
            (
                poseScore * 0.66f +
                    alignmentScore * 0.15f +
                    dominanceScore * 0.11f +
                    planarScore * 0.08f
                ).coerceIn(0f, 1f)

        return LandmarkPoseResult(
            category = category,
            score = confidence
        )
    }

    private fun fingerExtendedScore(
        landmarks: List<PoseLandmark>,
        scale: Float,
        minScale: Float,
        mcp: Int,
        pip: Int,
        dip: Int,
        tip: Int
    ): Float {
        val path =
            distance(landmarks[mcp], landmarks[pip]) +
                distance(landmarks[pip], landmarks[dip]) +
                distance(landmarks[dip], landmarks[tip])
        val chord =
            distance(landmarks[mcp], landmarks[tip])
        val straightness =
            if (path > 0.0001f) {
                (chord / path).coerceIn(0f, 1f)
            } else {
                0f
            }

        val chordReach =
            chord /
                scale.coerceAtLeast(
                    minScale
                )

        val straightScore =
            normalized(
                straightness,
                0.79f,
                0.965f
            )
        val reachScore =
            normalized(
                chordReach,
                0.46f,
                1.10f
            )

        return (
            straightScore * 0.70f +
                reachScore * 0.30f
            ).coerceIn(0f, 1f)
    }

    private fun thumbExtendedScore(
        landmarks: List<PoseLandmark>,
        scale: Float
    ): Float {
        val safeScale =
            scale.coerceAtLeast(0.0001f)
        val path =
            distance(landmarks[1], landmarks[2]) +
                distance(landmarks[2], landmarks[3]) +
                distance(landmarks[3], landmarks[4])
        val chord =
            distance(landmarks[1], landmarks[4])
        val straightness =
            if (path > 0.0001f) {
                (chord / path).coerceIn(0f, 1f)
            } else {
                0f
            }

        val palmCenter =
            averagePoint(
                landmarks[0],
                landmarks[5],
                landmarks[9],
                landmarks[13],
                landmarks[17]
            )
        val chordReach =
            chord / safeScale
        val indexSeparation =
            distance(
                landmarks[4],
                landmarks[5]
            ) / safeScale
        val palmClearance =
            distance(
                landmarks[4],
                palmCenter
            ) / safeScale
        val mcpStraight =
            jointExtensionScore(
                previous = landmarks[1],
                joint = landmarks[2],
                next = landmarks[3]
            )
        val ipStraight =
            jointExtensionScore(
                previous = landmarks[2],
                joint = landmarks[3],
                next = landmarks[4]
            )
        val jointExtension =
            (mcpStraight + ipStraight) * 0.5f

        // A true extended thumb needs more than a visible tip. It must escape
        // the palm volume, have meaningful 3D reach, and remain sufficiently
        // uncurled across both thumb joints. This suppresses exposed thumb
        // knuckles on a fist and side-on fist projections.
        return (
            normalized(
                straightness,
                0.80f,
                0.985f
            ) * 0.16f +
                normalized(
                    chordReach,
                    0.46f,
                    1.05f
                ) * 0.22f +
                normalized(
                    indexSeparation,
                    0.40f,
                    0.98f
                ) * 0.17f +
                normalized(
                    palmClearance,
                    0.52f,
                    1.15f
                ) * 0.23f +
                jointExtension * 0.22f
            ).coerceIn(0f, 1f)
    }

    private fun thumbRequiredPatternScore(
        thumbExtension: Float,
        vararg evidence: Float
    ): Float {
        if (
            thumbExtension <
                MIN_THUMB_EXTENSION_FOR_THUMB_REQUIRED_POSE
        ) {
            return 0f
        }
        return patternScore(
            thumbExtension,
            *evidence
        )
    }

    private fun thumbGestureScore(
        thumbExtension: Float,
        direction: Float,
        indexFold: Float,
        middleFold: Float,
        ringFold: Float,
        pinkyFold: Float
    ): Float {
        if (
            thumbExtension <
                MIN_THUMB_EXTENSION_FOR_THUMB_GESTURE
        ) {
            return 0f
        }

        return patternScore(
            thumbExtension,
            indexFold,
            middleFold,
            ringFold,
            pinkyFold,
            direction
        )
    }

    private fun jointExtensionScore(
        previous: PoseLandmark,
        joint: PoseLandmark,
        next: PoseLandmark
    ): Float {
        val ax = previous.x - joint.x
        val ay = previous.y - joint.y
        val az = previous.z - joint.z
        val bx = next.x - joint.x
        val by = next.y - joint.y
        val bz = next.z - joint.z
        val aLength =
            sqrt(ax * ax + ay * ay + az * az)
        val bLength =
            sqrt(bx * bx + by * by + bz * bz)
        if (
            aLength < 0.000001f ||
            bLength < 0.000001f
        ) {
            return 0f
        }

        val cosine =
            (
                (ax * bx + ay * by + az * bz) /
                    (aLength * bLength)
                ).coerceIn(-1f, 1f)

        // Straight finger segments point in opposite directions from the
        // joint, so cosine approaches -1. Convert that to 1.0 extension.
        return normalized(
            -cosine,
            0.30f,
            0.96f
        )
    }

    private fun averagePoint(
        vararg points: PoseLandmark
    ): PoseLandmark {
        if (points.isEmpty()) {
            return PoseLandmark(0f, 0f, 0f)
        }
        val count = points.size.toFloat()
        return PoseLandmark(
            x = points.sumOf { it.x.toDouble() }.toFloat() / count,
            y = points.sumOf { it.y.toDouble() }.toFloat() / count,
            z = points.sumOf { it.z.toDouble() }.toFloat() / count
        )
    }

    private fun validHand(
        landmarks: List<PoseLandmark>
    ): Boolean =
        landmarks.size >= 21 &&
            landmarks.take(21).none {
                !it.x.isFinite() ||
                    !it.y.isFinite() ||
                    !it.z.isFinite()
            }

    private fun handScale(
        landmarks: List<PoseLandmark>
    ): Float =
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

    private fun dualSpaceEvidence(
        imageScore: Float,
        worldScore: Float,
        worldWeight: Float
    ): Float {
        val safeWeight =
            worldWeight.coerceIn(0f, 1f)
        return (
            imageScore.coerceIn(0f, 1f) *
                (1f - safeWeight) +
                worldScore.coerceIn(0f, 1f) *
                safeWeight
            ).coerceIn(0f, 1f)
    }

    private fun directionEvidence(
        from: PoseLandmark,
        to: PoseLandmark,
        targetX: Float,
        targetY: Float
    ): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy)
        if (length < 0.0001f) return 0f

        val dot =
            (
                dx / length * targetX +
                    dy / length * targetY
                ).coerceIn(-1f, 1f)

        return normalized(
            dot,
            0.30f,
            0.90f
        )
    }

    private fun patternScore(
        vararg evidence: Float
    ): Float {
        if (evidence.isEmpty()) return 0f

        val bounded =
            evidence.map {
                it.coerceIn(0f, 1f)
            }
        val average =
            bounded.average().toFloat()
        val weakest =
            bounded.minOrNull() ?: 0f

        return (
            average * 0.76f +
                weakest * 0.24f
            ).coerceIn(0f, 1f)
    }

    private fun normalized(
        value: Float,
        low: Float,
        high: Float
    ): Float {
        if (high <= low) return 0f
        return (
            (value - low) /
                (high - low)
            ).coerceIn(0f, 1f)
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

    companion object {
        private const val MIN_THUMB_EXTENSION_FOR_THUMB_GESTURE = 0.68f
        private const val MIN_THUMB_EXTENSION_FOR_THUMB_REQUIRED_POSE = 0.60f
        private const val WORLD_FINGER_WEIGHT = 0.68f
        private const val WORLD_THUMB_WEIGHT = 0.78f
        private const val WORLD_PINCH_WEIGHT = 0.62f

        const val CATEGORY_OPEN_PALM = "Open_Palm"
        const val CATEGORY_CLOSED_FIST = "Closed_Fist"
        const val CATEGORY_POINTING_UP = "Pointing_Up"
        const val CATEGORY_THUMB_UP = "Thumb_Up"
        const val CATEGORY_THUMB_DOWN = "Thumb_Down"
        const val CATEGORY_VICTORY = "Victory"
        const val CATEGORY_I_LOVE_YOU = "ILoveYou"

        const val CATEGORY_L_SHAPE =
            "Custom_L_Shape"
        const val CATEGORY_FOUR_FINGERS =
            "Custom_Four_Fingers"
        const val CATEGORY_PINKY_UP =
            "Custom_Pinky_Up"
        const val CATEGORY_OK_SIGN =
            "Custom_OK_Sign"
        const val CATEGORY_CALL_ME =
            "Custom_Call_Me"
        const val CATEGORY_ROCK_SIGN =
            "Custom_Rock_Sign"
        const val CATEGORY_THREE_FINGERS =
            "Custom_Three_Fingers"
        const val CATEGORY_DOUBLE_GUN_UP =
            "Custom_Double_Gun_Up"
        const val CATEGORY_DOUBLE_GUN_DOWN =
            "Custom_Double_Gun_Down"
        const val CATEGORY_DOUBLE_GUN_LEFT =
            "Custom_Double_Gun_Left"
        const val CATEGORY_DOUBLE_GUN_RIGHT =
            "Custom_Double_Gun_Right"

        // Only custom categories belong in pointer's custom-conflict set.
        // Common fallback categories are handled by the same rules as their
        // MediaPipe equivalents.
        val categories = setOf(
            CATEGORY_L_SHAPE,
            CATEGORY_FOUR_FINGERS,
            CATEGORY_PINKY_UP,
            CATEGORY_OK_SIGN,
            CATEGORY_CALL_ME,
            CATEGORY_ROCK_SIGN,
            CATEGORY_THREE_FINGERS,
            CATEGORY_DOUBLE_GUN_UP,
            CATEGORY_DOUBLE_GUN_DOWN,
            CATEGORY_DOUBLE_GUN_LEFT,
            CATEGORY_DOUBLE_GUN_RIGHT
        )

        fun signalForCategory(
            category: String
        ): GestureSignal? =
            when (category) {
                CATEGORY_OPEN_PALM ->
                    GestureSignal.OPEN_PALM
                CATEGORY_CLOSED_FIST ->
                    GestureSignal.CLOSED_FIST
                CATEGORY_POINTING_UP ->
                    GestureSignal.POINTING_UP
                CATEGORY_THUMB_UP ->
                    GestureSignal.THUMB_UP
                CATEGORY_THUMB_DOWN ->
                    GestureSignal.THUMB_DOWN
                CATEGORY_VICTORY ->
                    GestureSignal.VICTORY
                CATEGORY_I_LOVE_YOU ->
                    GestureSignal.I_LOVE_YOU
                CATEGORY_L_SHAPE ->
                    GestureSignal.L_SHAPE
                CATEGORY_FOUR_FINGERS ->
                    GestureSignal.FOUR_FINGERS
                CATEGORY_PINKY_UP ->
                    GestureSignal.PINKY_UP
                CATEGORY_OK_SIGN ->
                    GestureSignal.OK_SIGN
                CATEGORY_CALL_ME ->
                    GestureSignal.CALL_ME
                CATEGORY_ROCK_SIGN ->
                    GestureSignal.ROCK_SIGN
                CATEGORY_THREE_FINGERS ->
                    GestureSignal.THREE_FINGERS
                CATEGORY_DOUBLE_GUN_UP ->
                    GestureSignal.NAV_SCROLL_UP
                CATEGORY_DOUBLE_GUN_DOWN ->
                    GestureSignal.NAV_SCROLL_DOWN
                CATEGORY_DOUBLE_GUN_LEFT ->
                    GestureSignal.NAV_SCROLL_LEFT
                CATEGORY_DOUBLE_GUN_RIGHT ->
                    GestureSignal.NAV_SCROLL_RIGHT
                else -> null
            }
    }
}
