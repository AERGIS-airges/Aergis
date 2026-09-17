package com.airgesture.control

data class CanonicalVisionLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Converts MediaPipe's landmark coordinate space into the app's
 * portrait, selfie-mirrored/user-perspective coordinate space.
 *
 * Important: ImageProcessingOptions.rotationDegrees rotates the image for
 * MediaPipe inference, but task results are expressed in the underlying
 * unrotated input-frame coordinate system. Therefore rotation must be applied
 * to the returned coordinates before the front-camera horizontal mirror.
 *
 * This is the coordinate contract used by:
 * - pointer X/Y
 * - gesture geometry
 * - control-hand continuity
 * - calibration
 * - debug skeleton overlay
 */
object VisionCoordinateMapper {
    fun canonicalLandmark(
        rawX: Float,
        rawY: Float,
        rawZ: Float,
        rotationDegrees: Int
    ): CanonicalVisionLandmark {
        if (
            !rawX.isFinite() ||
            !rawY.isFinite()
        ) {
            return CanonicalVisionLandmark(
                x = rawX,
                y = rawY,
                z = rawZ
            )
        }

        val rotation =
            normalizeRotation(rotationDegrees)

        val rotatedX: Float
        val rotatedY: Float

        when (rotation) {
            0 -> {
                rotatedX = rawX
                rotatedY = rawY
            }

            90 -> {
                // Clockwise: left/top in the raw sensor buffer becomes
                // top/right in the portrait-corrected image.
                rotatedX = 1f - rawY
                rotatedY = rawX
            }

            180 -> {
                rotatedX = 1f - rawX
                rotatedY = 1f - rawY
            }

            270 -> {
                rotatedX = rawY
                rotatedY = 1f - rawX
            }

            else ->
                error(
                    "Unreachable rotation: $rotation"
                )
        }

        // Front-camera user perspective: mirror only after the sensor/image
        // rotation has put X and Y into portrait display orientation.
        return CanonicalVisionLandmark(
            x = 1f - rotatedX,
            y = rotatedY,
            z = rawZ
        )
    }

    /**
     * Legacy 0-degree convenience retained for compatibility with focused
     * tests/callers. New camera-result code should use canonicalLandmark() so
     * it cannot accidentally ignore sensor/display rotation.
     */
    fun canonicalX(rawX: Float): Float =
        if (rawX.isFinite()) {
            1f - rawX
        } else {
            rawX
        }

    fun normalizeRotation(
        rotationDegrees: Int
    ): Int {
        val normalized =
            (
                rotationDegrees %
                    FULL_ROTATION_DEGREES +
                    FULL_ROTATION_DEGREES
                ) %
                FULL_ROTATION_DEGREES

        require(
            normalized %
                QUARTER_ROTATION_DEGREES ==
                0
        ) {
            "Rotation must be a multiple of 90 degrees: $rotationDegrees"
        }

        return normalized
    }

    /**
     * The previous pipeline mirrored pixels before MediaPipe saw them.
     * Removing that pixel mirror requires swapping Left/Right labels to
     * preserve the app's existing user-perspective hand preference semantics.
     * Rotation itself does not change chirality.
     */
    fun canonicalHandedness(
        rawLabel: String
    ): String =
        when {
            rawLabel.equals(
                "Left",
                ignoreCase = true
            ) ->
                "Right"

            rawLabel.equals(
                "Right",
                ignoreCase = true
            ) ->
                "Left"

            else -> rawLabel
        }

    private const val QUARTER_ROTATION_DEGREES =
        90
    private const val FULL_ROTATION_DEGREES =
        360
}
