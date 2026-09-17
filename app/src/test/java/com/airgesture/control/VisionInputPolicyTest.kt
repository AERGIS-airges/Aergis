package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionInputPolicyTest {
    @Test
    fun pointerUsesDirectMediaImageWhenAvailableAndAllowed() {
        assertEquals(
            VisionInputMode.DIRECT_MEDIA_IMAGE,
            VisionInputPolicy.choose(
                pointerActive = true,
                mediaImageAvailable = true,
                directMediaAllowed = true
            )
        )
    }

    @Test
    fun pointerFallsBackWhenMediaImageIsUnavailable() {
        assertEquals(
            VisionInputMode.BITMAP,
            VisionInputPolicy.choose(
                pointerActive = true,
                mediaImageAvailable = false,
                directMediaAllowed = true
            )
        )
    }

    @Test
    fun compatibilityCircuitBreakerForcesBitmap() {
        assertEquals(
            VisionInputMode.BITMAP,
            VisionInputPolicy.choose(
                pointerActive = true,
                mediaImageAvailable = true,
                directMediaAllowed = false
            )
        )
    }

    @Test
    fun gestureOnlyModeKeepsValidatedBitmapPath() {
        assertEquals(
            VisionInputMode.BITMAP,
            VisionInputPolicy.choose(
                pointerActive = false,
                mediaImageAvailable = true,
                directMediaAllowed = true
            )
        )
    }
}
