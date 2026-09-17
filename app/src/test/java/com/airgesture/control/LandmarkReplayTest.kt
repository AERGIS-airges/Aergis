package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkReplayTest {
    private fun closedFist(): List<PoseLandmark> {
        val points =
            MutableList(21) {
                PoseLandmark(.5f, .8f, 0f)
            }

        points[0] =
            PoseLandmark(.50f, .82f, 0f)
        points[1] =
            PoseLandmark(.43f, .70f, 0f)
        points[5] =
            PoseLandmark(.44f, .58f, 0f)
        points[9] =
            PoseLandmark(.50f, .56f, 0f)
        points[13] =
            PoseLandmark(.56f, .58f, 0f)
        points[17] =
            PoseLandmark(.62f, .62f, 0f)

        fun fold(
            pip: Int,
            dip: Int,
            tip: Int,
            x: Float
        ) {
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

        fold(6, 7, 8, .44f)
        fold(10, 11, 12, .50f)
        fold(14, 15, 16, .56f)
        fold(18, 19, 20, .62f)

        points[2] =
            PoseLandmark(.44f, .69f, 0f)
        points[3] =
            PoseLandmark(.47f, .69f, 0f)
        points[4] =
            PoseLandmark(.49f, .68f, 0f)

        return points
    }

    private fun metricWorld(
        image: List<PoseLandmark>
    ): List<PoseLandmark> =
        image.map {
            PoseLandmark(
                x = (it.x - .50f) * .35f,
                y = (it.y - .82f) * .35f,
                z = it.z * .35f
            )
        }

    @Test
    fun agcr1RoundTripPreservesLandmarksAndLabels() {
        val image = closedFist()
        val frame =
            LandmarkReplayFrame(
                timestampMs = 1_234L,
                groundTruth =
                    LandmarkPoseRecognizer
                        .CATEGORY_CLOSED_FIST,
                modelCategory = "Closed_Fist",
                modelScore = .88f,
                imageAspectRatio = 1.3333f,
                imageLandmarks = image,
                worldLandmarks =
                    metricWorld(image),
                handedness = "Left",
                handednessScore = .93f
            )

        val decoded =
            LandmarkReplayCodec.decode(
                LandmarkReplayCodec.encode(
                    frame
                )
            )

        assertEquals(
            frame.timestampMs,
            decoded?.timestampMs
        )
        assertEquals(
            frame.groundTruth,
            decoded?.groundTruth
        )
        assertEquals(
            21,
            decoded?.imageLandmarks?.size
        )
        assertEquals(
            21,
            decoded?.worldLandmarks?.size
        )
        assertEquals(
            "Left",
            decoded?.handedness
        )
    }

    @Test
    fun malformedOrWrongVersionCorpusLineIsRejected() {
        assertNull(
            LandmarkReplayCodec.decode(
                "AGCR2\t1\tbad"
            )
        )
        assertNull(
            LandmarkReplayCodec.decode(
                "AGCR1\t1\ttruth\tNone\t0.2\t1.0\tLeft\t0.9\t1,2\t-"
            )
        )
    }

    @Test
    fun unlabeledCaptureCannotCountAsAcceptanceEvidence() {
        val image = closedFist()
        val report =
            LandmarkReplayEngine()
                .replay(
                    listOf(
                        LandmarkReplayFrame(
                            timestampMs = 10L,
                            groundTruth = null,
                            modelCategory = "None",
                            modelScore = .20f,
                            imageAspectRatio = 1f,
                            imageLandmarks = image,
                            worldLandmarks =
                                metricWorld(image)
                        )
                    )
                )

        assertEquals(0, report.labeledCount)
        assertNull(report.accuracy)
    }

    @Test
    fun labeledClosedFistReplaysDeterministically() {
        val image = closedFist()
        val truth =
            LandmarkPoseRecognizer
                .CATEGORY_CLOSED_FIST
        val frame =
            LandmarkReplayFrame(
                timestampMs = 20L,
                groundTruth = truth,
                modelCategory = "None",
                modelScore = .20f,
                imageAspectRatio = 1f,
                imageLandmarks = image,
                worldLandmarks =
                    metricWorld(image)
            )

        val first =
            LandmarkReplayEngine()
                .replay(listOf(frame))
        val second =
            LandmarkReplayEngine()
                .replay(listOf(frame))

        assertEquals(
            truth,
            first.outcomes.single()
                .effectiveCategory
        )
        assertEquals(
            first.outcomes,
            second.outcomes
        )
        assertEquals(1, first.labeledCount)
        assertEquals(1, first.correctCount)
        assertTrue(
            (first.accuracy ?: 0f) >= 1f
        )
    }

    @Test
    fun commentsAndBlankLinesAreIgnoredByCorpusParser() {
        val image = closedFist()
        val encoded =
            LandmarkReplayCodec.encode(
                LandmarkReplayFrame(
                    timestampMs = 30L,
                    groundTruth = "Closed_Fist",
                    modelCategory =
                        "Closed_Fist",
                    modelScore = .90f,
                    imageAspectRatio = 1f,
                    imageLandmarks = image,
                    worldLandmarks = null
                )
            )

        val decoded =
            LandmarkReplayCodec.decodeAll(
                "# header\n\n$encoded\n"
            )

        assertEquals(1, decoded.size)
    }
}
