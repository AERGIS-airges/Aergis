package com.airgesture.control

enum class VisionInputMode {
    DIRECT_MEDIA_IMAGE,
    BITMAP
}

/**
 * Keeps the lower-copy path isolated to Air Pointer latency mode and makes
 * compatibility fallback explicit.
 *
 * Gesture-only mode deliberately retains the previously validated Bitmap path.
 * A session-level circuit breaker can also permanently disable direct media
 * after one incompatibility/error so the app degrades instead of repeatedly
 * failing the vision pipeline.
 */
object VisionInputPolicy {
    fun choose(
        pointerActive: Boolean,
        mediaImageAvailable: Boolean,
        directMediaAllowed: Boolean = true
    ): VisionInputMode =
        if (
            pointerActive &&
            mediaImageAvailable &&
            directMediaAllowed
        ) {
            VisionInputMode.DIRECT_MEDIA_IMAGE
        } else {
            VisionInputMode.BITMAP
        }
}
