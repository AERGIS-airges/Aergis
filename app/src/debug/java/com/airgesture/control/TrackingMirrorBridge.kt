package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap

/**
 * R16 development builds intentionally suppress the old floating tracking
 * mirror. Device videos showed the debug camera surface obscuring the product
 * UI. Diagnostic frame capture can be reintroduced later as an explicit,
 * in-app opt-in viewport; it must never appear automatically over the product.
 */
object TrackingMirrorBridge {
    fun attach(service: AccessibilityService) = Unit

    fun detach(service: AccessibilityService) = Unit

    fun setActive(value: Boolean) = Unit

    fun isCorpusRecordingSupported(): Boolean = false

    fun isCorpusRecordingEnabled(): Boolean = false

    fun setCorpusRecordingEnabled(enabled: Boolean): Boolean = false

    fun corpusRecordingFileName(): String? = null

    fun publishCorpusFrame(frame: LandmarkReplayFrame) = Unit

    fun isCaptureEnabled(): Boolean = false

    fun shouldCaptureFrame(timestampMs: Long): Boolean = false

    fun publishFrame(source: Bitmap, timestampMs: Long) = Unit

    fun publishTracking(
        timestampMs: Long,
        points: FloatArray,
        centerX: Float,
        centerY: Float,
        handLabel: String,
        handConfidence: Float,
        category: String,
        categoryScore: Float,
        trackingState: String,
        gestureLabel: String?
    ) = Unit

    fun publishNoHand(
        timestampMs: Long,
        trackingState: String = "NO HAND"
    ) = Unit
}
