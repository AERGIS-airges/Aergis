package com.airgesture.control

data class EffectivePose(
    val category: String,
    val score: Float
)

/**
 * Chooses between MediaPipe's learned category and independent landmark
 * geometry without pretending their confidence scores are calibrated against
 * one another.
 *
 * Policy:
 * - agreement can confirm a category but never boosts beyond the strongest
 *   source;
 * - weak/generic learned output may be recovered by a sufficiently strong
 *   geometry result;
 * - a strong specific learned-vs-geometry disagreement abstains instead of
 *   comparing incomparable score magnitudes;
 * - the only strong-specific override is explicit world-backed physical
 *   contradiction (currently false learned Thumb Up/Down).
 *
 * Numeric per-class calibration remains an evidence task for a held-out
 * real-user corpus. Until then, abstention is safer than arbitrary fusion.
 */
object PoseCategoryArbiter {
    private val genericModelCategories =
        setOf(
            "None",
            "Open_Palm",
            "Pointing_Up"
        )

    private val learnedThumbCategories =
        setOf(
            LandmarkPoseRecognizer.CATEGORY_THUMB_UP,
            LandmarkPoseRecognizer.CATEGORY_THUMB_DOWN
        )

    private val strongThumbContradictions =
        setOf(
            LandmarkPoseRecognizer.CATEGORY_CLOSED_FIST,
            LandmarkPoseRecognizer.CATEGORY_POINTING_UP,
            LandmarkPoseRecognizer.CATEGORY_VICTORY,
            LandmarkPoseRecognizer.CATEGORY_ROCK_SIGN,
            LandmarkPoseRecognizer.CATEGORY_FOUR_FINGERS,
            LandmarkPoseRecognizer.CATEGORY_THREE_FINGERS,
            LandmarkPoseRecognizer.CATEGORY_PINKY_UP
        )

    fun choose(
        modelCategory: String,
        modelScore: Float,
        custom: LandmarkPoseResult?,
        physicalThumb: ThumbExtensionEvidence? = null
    ): EffectivePose {
        val safeModelCategory =
            modelCategory.ifBlank { "None" }
        val safeModelScore =
            modelScore
                .takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 0f

        val physicalWorldThumbScore =
            physicalThumb
                ?.worldScore
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
        val learnedThumbPhysicallyImpossible =
            safeModelCategory in
                learnedThumbCategories &&
                physicalThumb?.worldBacked == true &&
                physicalWorldThumbScore != null &&
                physicalWorldThumbScore <=
                    MAX_WORLD_BACKED_FOLDED_THUMB_SCORE

        if (
            custom == null ||
            !custom.score.isFinite()
        ) {
            return if (
                learnedThumbPhysicallyImpossible
            ) {
                abstain()
            } else {
                EffectivePose(
                    category = safeModelCategory,
                    score = safeModelScore
                )
            }
        }

        val landmarkCategory =
            custom.category.ifBlank { "None" }
        val landmarkScore =
            custom.score.coerceIn(0f, 1f)
        val landmarkSignal =
            LandmarkPoseRecognizer
                .signalForCategory(
                    landmarkCategory
                )
        val modelSignal =
            LandmarkPoseRecognizer
                .signalForCategory(
                    safeModelCategory
                )
        val navigationSignal =
            landmarkSignal in setOf(
                GestureSignal.NAV_SCROLL_UP,
                GestureSignal.NAV_SCROLL_DOWN,
                GestureSignal.NAV_SCROLL_LEFT,
                GestureSignal.NAV_SCROLL_RIGHT
            )
        val requiredLandmarkScore =
            if (navigationSignal) {
                0.68f
            } else {
                0.70f
            }

        if (landmarkScore < requiredLandmarkScore) {
            return if (
                learnedThumbPhysicallyImpossible
            ) {
                abstain()
            } else {
                EffectivePose(
                    category = safeModelCategory,
                    score = safeModelScore
                )
            }
        }

        if (
            landmarkCategory.equals(
                safeModelCategory,
                ignoreCase = true
            ) ||
            (
                landmarkSignal != null &&
                    landmarkSignal == modelSignal
                )
        ) {
            // Correlated sources must not manufacture extra certainty.
            return EffectivePose(
                category = landmarkCategory,
                score =
                    maxOf(
                        safeModelScore,
                        landmarkScore
                    )
            )
        }

        val modelIsGeneric =
            safeModelCategory in
                genericModelCategories
        val modelIsWeak =
            safeModelScore <
                WEAK_MODEL_SCORE

        val worldBackedThumbContradiction =
            custom.worldBacked &&
                safeModelCategory in
                    learnedThumbCategories &&
                landmarkCategory in
                    strongThumbContradictions &&
                landmarkScore >=
                    MIN_STRONG_THUMB_CONTRADICTION_SCORE

        if (
            worldBackedThumbContradiction
        ) {
            return EffectivePose(
                category = landmarkCategory,
                score = landmarkScore
            )
        }

        if (learnedThumbPhysicallyImpossible) {
            // Physical evidence disproves the learned thumb label, but unless
            // a world-backed alternate pose also wins, "unknown" is safer than
            // inventing a replacement category from 2D confidence.
            return abstain()
        }

        if (
            modelIsWeak ||
            modelIsGeneric
        ) {
            return EffectivePose(
                category = landmarkCategory,
                score = landmarkScore
            )
        }

        // Strong, specific disagreement with no calibrated comparison and no
        // validated physical contradiction. Do not pick a winner by numeric
        // score; suppress the action until evidence agrees.
        return abstain()
    }

    private fun abstain(): EffectivePose =
        EffectivePose(
            category = "None",
            score = 0f
        )

    private const val WEAK_MODEL_SCORE = 0.46f
    private const val MIN_STRONG_THUMB_CONTRADICTION_SCORE = 0.76f
    private const val MAX_WORLD_BACKED_FOLDED_THUMB_SCORE = 0.42f
}
