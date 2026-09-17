package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirActionCapabilitiesTest {

    @Test
    fun mediaPlayPauseRequiresApi36() {
        assertFalse(
            AirActionCapabilities.isSupported(
                AirAction.MEDIA_PLAY_PAUSE,
                35
            )
        )
        assertTrue(
            AirActionCapabilities.isSupported(
                AirAction.MEDIA_PLAY_PAUSE,
                36
            )
        )
    }

    @Test
    fun ordinaryNavigationActionsRemainAvailable() {
        listOf(
            AirAction.BACK,
            AirAction.HOME,
            AirAction.SCROLL_UP,
            AirAction.SCROLL_DOWN,
            AirAction.SCROLL_LEFT,
            AirAction.SCROLL_RIGHT
        ).forEach { action ->
            assertTrue(
                AirActionCapabilities.isSupported(
                    action,
                    26
                )
            )
        }
    }
}
