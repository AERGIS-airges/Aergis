package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class PoseCategoryArbiterTest {

    @Test
    fun strongSpecificVictoryConflictAbstainsWithoutCalibration() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Victory",
                modelScore = .97f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_THREE_FINGERS,
                        score = .86f
                    )
            )

        assertEquals("None", chosen.category)
        assertEquals(0f, chosen.score, .001f)
    }

    @Test
    fun strongSpecificConflictAbstainsWithoutCalibratedComparison() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .72f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CALL_ME,
                        score = .90f
                    )
            )

        assertEquals("None", chosen.category)
        assertEquals(0f, chosen.score, .001f)
    }

    @Test
    fun specificCustomPoseCanOverrideGenericOpenPalm() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Open_Palm",
                modelScore = .96f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_FOUR_FINGERS,
                        score = .88f,
                        worldBacked = true
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_FOUR_FINGERS,
            chosen.category
        )
    }

    @Test
    fun agreeingModelAndLandmarksDoNotManufactureExtraConfidence() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Closed_Fist",
                modelScore = .66f,
                custom =
                    LandmarkPoseResult(
                        category = "Closed_Fist",
                        score = .82f
                    )
            )

        assertEquals("Closed_Fist", chosen.category)
        assertEquals(.82f, chosen.score, .001f)
    }

    @Test
    fun strongLandmarksRecoverWeakModelClassification() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "None",
                modelScore = .31f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_VICTORY,
                        score = .83f
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_VICTORY,
            chosen.category
        )
    }

    @Test
    fun strongClosedFistGeometryVetoesFalseThumbModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .97f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CLOSED_FIST,
                        score = .88f,
                        worldBacked = true
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_CLOSED_FIST,
            chosen.category
        )
    }

    @Test
    fun strongPinkyGeometryVetoesFalseThumbModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .94f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_PINKY_UP,
                        score = .78f,
                        worldBacked = true
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_PINKY_UP,
            chosen.category
        )
    }

    @Test
    fun unresolvedSpecificConflictAbstainsEvenWhenGeometryIsLower() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .94f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CLOSED_FIST,
                        score = .74f
                    )
            )

        assertEquals("None", chosen.category)
    }

    @Test
    fun strongClosedFistGeometryVetoesFalseThumbDownModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Down",
                modelScore = .96f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CLOSED_FIST,
                        score = .84f,
                        worldBacked = true
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_CLOSED_FIST,
            chosen.category
        )
    }

    @Test
    fun worldBackedFoldedThumbVetoesAmbiguousThumbModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .98f,
                custom = null,
                physicalThumb =
                    ThumbExtensionEvidence(
                        score = .55f,
                        worldBacked = true,
                        worldScore = .24f
                    )
            )

        assertEquals("None", chosen.category)
        assertEquals(0f, chosen.score, .001f)
    }

    @Test
    fun imageOnlyThumbEvidenceCannotPhysicallyVetoModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .93f,
                custom = null,
                physicalThumb =
                    ThumbExtensionEvidence(
                        score = .24f,
                        worldBacked = false,
                        worldScore = null
                    )
            )

        assertEquals("Thumb_Up", chosen.category)
    }

    @Test
    fun worldBackedExtendedThumbPreservesRealThumbModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .91f,
                custom = null,
                physicalThumb =
                    ThumbExtensionEvidence(
                        score = .82f,
                        worldBacked = true,
                        worldScore = .86f
                    )
            )

        assertEquals("Thumb_Up", chosen.category)
    }

    @Test
    fun weakCompetingPoseCannotResurrectPhysicallyImpossibleThumb() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Down",
                modelScore = .96f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CLOSED_FIST,
                        score = .72f
                    ),
                physicalThumb =
                    ThumbExtensionEvidence(
                        score = .48f,
                        worldBacked = true,
                        worldScore = .18f
                    )
            )

        assertEquals("None", chosen.category)
    }

    @Test
    fun strongModelIsPreservedWhenNoCompetingGeometryExists() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Victory",
                modelScore = .97f,
                custom = null
            )

        assertEquals("Victory", chosen.category)
        assertEquals(.97f, chosen.score, .001f)
    }

    @Test
    fun imageOnlyHighClosedFistCannotPhysicallyOverrideThumbModel() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Thumb_Up",
                modelScore = .97f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_CLOSED_FIST,
                        score = .91f,
                        worldBacked = false
                    )
            )

        assertEquals("None", chosen.category)
        assertEquals(0f, chosen.score, .001f)
    }

    @Test
    fun weakLearnedModelCanStillBeRecoveredByGeometry() {
        val chosen =
            PoseCategoryArbiter.choose(
                modelCategory = "Victory",
                modelScore = .30f,
                custom =
                    LandmarkPoseResult(
                        category =
                            LandmarkPoseRecognizer
                                .CATEGORY_THREE_FINGERS,
                        score = .84f
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_THREE_FINGERS,
            chosen.category
        )
    }

}
