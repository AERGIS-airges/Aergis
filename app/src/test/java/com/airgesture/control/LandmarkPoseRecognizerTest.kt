package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkPoseRecognizerTest {
    private enum class Finger {
        INDEX,
        MIDDLE,
        RING,
        PINKY
    }

    private fun hand(
        extended: Set<Finger>,
        thumbExtended: Boolean = false,
        okSign: Boolean = false
    ): List<PoseLandmark> {
        val points =
            MutableList(21) {
                PoseLandmark(.5f, .8f, 0f)
            }

        points[0] = PoseLandmark(.50f, .82f, 0f)
        points[1] = PoseLandmark(.43f, .70f, 0f)
        points[5] = PoseLandmark(.44f, .58f, 0f)
        points[9] = PoseLandmark(.50f, .56f, 0f)
        points[13] = PoseLandmark(.56f, .58f, 0f)
        points[17] = PoseLandmark(.62f, .62f, 0f)

        fun setFinger(
            mcp: Int,
            pip: Int,
            dip: Int,
            tip: Int,
            x: Float,
            isExtended: Boolean
        ) {
            if (isExtended) {
                points[pip] =
                    PoseLandmark(x, .47f, 0f)
                points[dip] =
                    PoseLandmark(x, .36f, 0f)
                points[tip] =
                    PoseLandmark(x, .25f, 0f)
            } else {
                points[pip] =
                    PoseLandmark(x, .53f, 0f)
                points[dip] =
                    PoseLandmark(
                        x + .035f,
                        .59f,
                        0f
                    )
                points[tip] =
                    PoseLandmark(
                        x + .015f,
                        .635f,
                        0f
                    )
            }
        }

        setFinger(
            5, 6, 7, 8, .44f,
            Finger.INDEX in extended
        )
        setFinger(
            9, 10, 11, 12, .50f,
            Finger.MIDDLE in extended
        )
        setFinger(
            13, 14, 15, 16, .56f,
            Finger.RING in extended
        )
        setFinger(
            17, 18, 19, 20, .62f,
            Finger.PINKY in extended
        )

        if (thumbExtended) {
            points[2] =
                PoseLandmark(.38f, .67f, 0f)
            points[3] =
                PoseLandmark(.31f, .63f, 0f)
            points[4] =
                PoseLandmark(.24f, .58f, 0f)
        } else {
            points[2] =
                PoseLandmark(.44f, .69f, 0f)
            points[3] =
                PoseLandmark(.47f, .69f, 0f)
            points[4] =
                PoseLandmark(.49f, .68f, 0f)
        }

        if (okSign) {
            points[2] =
                PoseLandmark(.41f, .67f, 0f)
            points[3] =
                PoseLandmark(.37f, .63f, 0f)
            points[4] =
                PoseLandmark(.34f, .58f, 0f)

            points[6] =
                PoseLandmark(.42f, .51f, 0f)
            points[7] =
                PoseLandmark(.39f, .54f, 0f)
            points[8] =
                PoseLandmark(.345f, .585f, 0f)
        }

        return points
    }

    private fun metricWorld(
        imagePoints: List<PoseLandmark>,
        scaleMetersPerNormalizedUnit: Float = .35f
    ): List<PoseLandmark> =
        imagePoints.map {
            PoseLandmark(
                x =
                    (it.x - .50f) *
                        scaleMetersPerNormalizedUnit,
                y =
                    (it.y - .82f) *
                        scaleMetersPerNormalizedUnit,
                z =
                    it.z *
                        scaleMetersPerNormalizedUnit
            )
        }

    @Test
    fun recognizesFourFingers() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    setOf(
                        Finger.INDEX,
                        Finger.MIDDLE,
                        Finger.RING,
                        Finger.PINKY
                    )
                )
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_FOUR_FINGERS,
            result?.category
        )
        assertTrue((result?.score ?: 0f) >= .78f)
    }

    @Test
    fun recognizesThreeFingers() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    setOf(
                        Finger.INDEX,
                        Finger.MIDDLE,
                        Finger.RING
                    )
                )
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_THREE_FINGERS,
            result?.category
        )
    }

    @Test
    fun recognizesPinkyUp() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(setOf(Finger.PINKY))
            )

        assertEquals(
            LandmarkPoseRecognizer
                .CATEGORY_PINKY_UP,
            result?.category
        )
    }

    @Test
    fun recognizesCallMe() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    extended =
                        setOf(Finger.PINKY),
                    thumbExtended = true
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_CALL_ME,
            result?.category
        )
    }

    @Test
    fun recognizesRockSignWithoutThumb() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    setOf(
                        Finger.INDEX,
                        Finger.PINKY
                    )
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_ROCK_SIGN,
            result?.category
        )
    }

    @Test
    fun recognizesCombinedLOrFingerGunPose() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    extended =
                        setOf(Finger.INDEX),
                    thumbExtended = true
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_L_SHAPE,
            result?.category
        )
    }

    @Test
    fun recognizesOkSignByThumbIndexLoop() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    extended =
                        setOf(
                            Finger.MIDDLE,
                            Finger.RING,
                            Finger.PINKY
                        ),
                    okSign = true
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_OK_SIGN,
            result?.category
        )
    }

    @Test
    fun recognizesOpenPalmAsClassifierFallback() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    extended =
                        setOf(
                            Finger.INDEX,
                            Finger.MIDDLE,
                            Finger.RING,
                            Finger.PINKY
                        ),
                    thumbExtended = true
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_OPEN_PALM,
            result?.category
        )
    }

    @Test
    fun recognizesClosedFistAsClassifierFallback() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(emptySet())
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_CLOSED_FIST,
            result?.category
        )
    }

    @Test
    fun recognizesVictoryAsClassifierFallback() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    setOf(
                        Finger.INDEX,
                        Finger.MIDDLE
                    )
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_VICTORY,
            result?.category
        )
    }

    @Test
    fun recognizesILoveYouAsClassifierFallback() {
        val result =
            LandmarkPoseRecognizer().recognize(
                hand(
                    extended =
                        setOf(
                            Finger.INDEX,
                            Finger.PINKY
                        ),
                    thumbExtended = true
                )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_I_LOVE_YOU,
            result?.category
        )
    }

    @Test
    fun ambiguityMarginCanRejectOtherwiseValidCustomPose() {
        val strict =
            LandmarkPoseRecognizer(
                minAcceptScore = .70f,
                minAmbiguityMargin = 1f
            )

        val result =
            strict.recognize(
                hand(
                    extended =
                        setOf(Finger.PINKY)
                )
            )

        assertNull(result)
    }


    @Test
    fun worldGeometryRejectsThumbLikeProjectionOfClosedFist() {
        val projection =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }

        val recognizer = LandmarkPoseRecognizer()
        val projectionOnly =
            recognizer.recognize(projection)
        val withWorldGeometry =
            recognizer.recognize(
                landmarks = projection,
                worldLandmarks =
                    metricWorld(
                        hand(emptySet())
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_THUMB_UP,
            projectionOnly?.category
        )
        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_CLOSED_FIST,
            withWorldGeometry?.category
        )
        assertTrue(
            (withWorldGeometry?.score ?: 0f) >= .76f
        )
    }

    @Test
    fun worldGeometrySeparatesPinkyUpFromThumbLikeProjection() {
        val projection =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }

        val result =
            LandmarkPoseRecognizer().recognize(
                landmarks = projection,
                worldLandmarks =
                    metricWorld(
                        hand(
                            extended =
                                setOf(Finger.PINKY)
                        )
                    )
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_PINKY_UP,
            result?.category
        )
        assertTrue(
            (result?.score ?: 0f) >= .76f
        )
    }

    @Test
    fun worldGeometryPreservesTrueThumbUp() {
        val trueThumbUp =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }

        val result =
            LandmarkPoseRecognizer().recognize(
                landmarks = trueThumbUp,
                worldLandmarks =
                    metricWorld(trueThumbUp)
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_THUMB_UP,
            result?.category
        )
    }

    @Test
    fun invalidWorldGeometryFallsBackToImageGeometry() {
        val trueThumbUp =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }
        val invalidWorld =
            trueThumbUp
                .toMutableList()
                .apply {
                    this[4] =
                        PoseLandmark(Float.NaN, 0f, 0f)
                }

        val result =
            LandmarkPoseRecognizer().recognize(
                landmarks = trueThumbUp,
                worldLandmarks = invalidWorld
            )

        assertEquals(
            LandmarkPoseRecognizer.CATEGORY_THUMB_UP,
            result?.category
        )
    }

    @Test
    fun foldedWorldThumbProducesWorldBackedLowExtensionEvidence() {
        val projection =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }

        val evidence =
            LandmarkPoseRecognizer()
                .thumbExtensionEvidence(
                    landmarks = projection,
                    worldLandmarks =
                        metricWorld(
                            hand(emptySet())
                        )
                )

        assertTrue(evidence?.worldBacked == true)
        assertTrue((evidence?.worldScore ?: 1f) <= .42f)
        assertTrue((evidence?.score ?: 0f) > (evidence?.worldScore ?: 1f))
    }

    @Test
    fun trueWorldThumbProducesHighExtensionEvidence() {
        val trueThumbUp =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }

        val evidence =
            LandmarkPoseRecognizer()
                .thumbExtensionEvidence(
                    landmarks = trueThumbUp,
                    worldLandmarks =
                        metricWorld(trueThumbUp)
                )

        assertTrue(evidence?.worldBacked == true)
        assertTrue((evidence?.worldScore ?: 0f) >= .68f)
        assertTrue((evidence?.score ?: 0f) >= .68f)
    }

    @Test
    fun invalidWorldThumbEvidenceIsExplicitlyImageOnly() {
        val trueThumbUp =
            hand(emptySet())
                .toMutableList()
                .apply {
                    this[1] =
                        PoseLandmark(.46f, .70f, 0f)
                    this[2] =
                        PoseLandmark(.46f, .64f, 0f)
                    this[3] =
                        PoseLandmark(.46f, .54f, 0f)
                    this[4] =
                        PoseLandmark(.46f, .40f, 0f)
                }
        val invalidWorld =
            trueThumbUp
                .toMutableList()
                .apply {
                    this[4] =
                        PoseLandmark(Float.NaN, 0f, 0f)
                }

        val evidence =
            LandmarkPoseRecognizer()
                .thumbExtensionEvidence(
                    landmarks = trueThumbUp,
                    worldLandmarks = invalidWorld
                )

        assertTrue(evidence?.worldBacked == false)
        assertNull(evidence?.worldScore)
        assertTrue((evidence?.score ?: 0f) >= .68f)
    }

}
