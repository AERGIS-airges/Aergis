package com.airgesture.control

data class LandmarkReplayFrame(
    val timestampMs: Long,
    val groundTruth: String?,
    val modelCategory: String,
    val modelScore: Float,
    val imageAspectRatio: Float,
    val imageLandmarks: List<PoseLandmark>,
    val worldLandmarks: List<PoseLandmark>?,
    val handedness: String = "Unknown",
    val handednessScore: Float = 0f
)

data class LandmarkReplayOutcome(
    val timestampMs: Long,
    val groundTruth: String?,
    val modelCategory: String,
    val customCategory: String?,
    val effectiveCategory: String,
    val effectiveScore: Float,
    val correct: Boolean?
)

data class LandmarkReplayReport(
    val outcomes: List<LandmarkReplayOutcome>
) {
    val labeledCount: Int
        get() =
            outcomes.count {
                !it.groundTruth.isNullOrBlank()
            }

    val correctCount: Int
        get() =
            outcomes.count {
                it.correct == true
            }

    val accuracy: Float?
        get() =
            labeledCount
                .takeIf { it > 0 }
                ?.let {
                    correctCount.toFloat() /
                        it.toFloat()
                }
}

/**
 * Versioned, local-only landmark corpus format.
 *
 * One UTF-8 line per frame:
 * AGCR1 <tab> timestamp <tab> truth <tab> modelCategory <tab> modelScore
 * <tab> aspect <tab> handedness <tab> handednessScore
 * <tab> image63 <tab> world63-or-
 *
 * Labels are percent-escaped so the format remains line-oriented and easy to
 * inspect/edit without an additional JSON dependency.
 */
object LandmarkReplayCodec {
    private const val PREFIX = "AGCR1"
    private const val LANDMARK_VALUES = 63

    fun encode(frame: LandmarkReplayFrame): String {
        require(frame.imageLandmarks.size == 21) {
            "AGCR1 requires exactly 21 image landmarks"
        }
        require(
            frame.worldLandmarks == null ||
                frame.worldLandmarks.size == 21
        ) {
            "AGCR1 world landmarks must be absent or exactly 21"
        }

        return listOf(
            PREFIX,
            frame.timestampMs.toString(),
            escape(frame.groundTruth.orEmpty()),
            escape(frame.modelCategory),
            frame.modelScore.toString(),
            frame.imageAspectRatio.toString(),
            escape(frame.handedness),
            frame.handednessScore.toString(),
            encodePoints(frame.imageLandmarks),
            frame.worldLandmarks
                ?.let(::encodePoints)
                ?: "-"
        ).joinToString("	")
    }

    fun decode(line: String): LandmarkReplayFrame? {
        val parts = line.split('	')
        if (
            parts.size != 10 ||
            parts[0] != PREFIX
        ) {
            return null
        }

        val timestamp =
            parts[1].toLongOrNull()
                ?: return null
        val modelScore =
            parts[4].toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?: return null
        val aspect =
            parts[5].toFloatOrNull()
                ?.takeIf {
                    it.isFinite() &&
                        it in 0.25f..4f
                }
                ?: return null
        val handednessScore =
            parts[7].toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?: return null
        val image =
            decodePoints(parts[8])
                ?: return null
        val world =
            if (parts[9] == "-") {
                null
            } else {
                decodePoints(parts[9])
                    ?: return null
            }

        return LandmarkReplayFrame(
            timestampMs = timestamp,
            groundTruth =
                unescape(parts[2])
                    .takeIf { it.isNotBlank() },
            modelCategory =
                unescape(parts[3])
                    .ifBlank { "None" },
            modelScore =
                modelScore.coerceIn(0f, 1f),
            imageAspectRatio = aspect,
            imageLandmarks = image,
            worldLandmarks = world,
            handedness =
                unescape(parts[6])
                    .ifBlank { "Unknown" },
            handednessScore =
                handednessScore
                    .coerceIn(0f, 1f)
        )
    }

    fun decodeAll(text: String): List<LandmarkReplayFrame> =
        text.lineSequence()
            .map(String::trim)
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("#")
            }
            .mapNotNull(::decode)
            .toList()

    private fun encodePoints(
        points: List<PoseLandmark>
    ): String =
        points.flatMap {
            listOf(it.x, it.y, it.z)
        }.joinToString(",")

    private fun decodePoints(
        value: String
    ): List<PoseLandmark>? {
        val numbers =
            value.split(',')
                .map {
                    it.toFloatOrNull()
                        ?.takeIf(Float::isFinite)
                        ?: return null
                }
        if (numbers.size != LANDMARK_VALUES) {
            return null
        }

        return (0 until 21).map { index ->
            val offset = index * 3
            PoseLandmark(
                x = numbers[offset],
                y = numbers[offset + 1],
                z = numbers[offset + 2]
            )
        }
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '%' -> append("%25")
                    '\t' -> append("%09")
                    '\n' -> append("%0A")
                    '\r' -> append("%0D")
                    else -> append(char)
                }
            }
        }

    private fun unescape(value: String): String =
        value
            .replace("%0D", "\r")
            .replace("%0A", "\n")
            .replace("%09", "\t")
            .replace("%25", "%")
}

class LandmarkReplayEngine(
    private val recognizer: LandmarkPoseRecognizer =
        LandmarkPoseRecognizer()
) {
    fun replay(
        frames: List<LandmarkReplayFrame>
    ): LandmarkReplayReport {
        val outcomes =
            frames.map { frame ->
                val custom =
                    recognizer.recognize(
                        landmarks =
                            frame.imageLandmarks,
                        worldLandmarks =
                            frame.worldLandmarks,
                        imageAspectRatio =
                            frame.imageAspectRatio
                    )
                val thumb =
                    recognizer
                        .thumbExtensionEvidence(
                            landmarks =
                                frame.imageLandmarks,
                            worldLandmarks =
                                frame.worldLandmarks,
                            imageAspectRatio =
                                frame.imageAspectRatio
                        )
                val effective =
                    PoseCategoryArbiter.choose(
                        modelCategory =
                            frame.modelCategory,
                        modelScore =
                            frame.modelScore,
                        custom = custom,
                        physicalThumb = thumb
                    )
                val truth =
                    frame.groundTruth
                        ?.takeIf {
                            it.isNotBlank()
                        }

                LandmarkReplayOutcome(
                    timestampMs =
                        frame.timestampMs,
                    groundTruth = truth,
                    modelCategory =
                        frame.modelCategory,
                    customCategory =
                        custom?.category,
                    effectiveCategory =
                        effective.category,
                    effectiveScore =
                        effective.score,
                    correct =
                        truth?.let {
                            effective.category
                                .equals(
                                    it,
                                    ignoreCase = true
                                )
                        }
                )
            }

        return LandmarkReplayReport(outcomes)
    }
}
