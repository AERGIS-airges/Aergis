package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultMappingsTest {
    @Test
    fun everyGestureHasExactlyOneDefaultActionValue() {
        GestureSignal.entries.forEach { signal ->
            val action = DefaultMappings.values[signal]
            assertNotNull("Missing action for $signal", action)
        }
        assertEquals(GestureSignal.entries.size, DefaultMappings.values.size)
    }
    @Test
    fun optionalPointerIsOffByDefaultForNavigationFirstExperience() {
        assertFalse(
            ActionMappingStore.DEFAULT_POINTER_ENABLED
        )
    }


    @Test
    fun freshInstallEnablesOnlyPrimaryNavigationByDefault() {
        val expectedEnabled =
            setOf(
                GestureSignal.SWIPE_LEFT,
                GestureSignal.SWIPE_RIGHT,
                GestureSignal.SWIPE_UP,
                GestureSignal.SWIPE_DOWN,
                GestureSignal.NAV_SCROLL_UP,
                GestureSignal.NAV_SCROLL_DOWN,
                GestureSignal.NAV_SCROLL_LEFT,
                GestureSignal.NAV_SCROLL_RIGHT
            )

        GestureSignal.entries.forEach { signal ->
            val action =
                DefaultMappings.values[signal]
                    ?: error("Missing default for $signal")

            if (signal in expectedEnabled) {
                assertFalse(
                    "Primary navigation should be enabled for $signal",
                    action == AirAction.NONE
                )
            } else {
                assertEquals(
                    "Static/custom gesture should be opt-in: $signal",
                    AirAction.NONE,
                    action
                )
            }
        }
    }


    @Test
    fun broadSwipeDefaultsMatchTheirAxes() {
        assertEquals(
            AirAction.SCROLL_LEFT,
            DefaultMappings.values[
                GestureSignal.SWIPE_LEFT
            ]
        )
        assertEquals(
            AirAction.SCROLL_RIGHT,
            DefaultMappings.values[
                GestureSignal.SWIPE_RIGHT
            ]
        )
        assertEquals(
            AirAction.FORWARD,
            DefaultMappings.values[
                GestureSignal.SWIPE_UP
            ]
        )
        assertEquals(
            AirAction.SCROLL_BACKWARD,
            DefaultMappings.values[
                GestureSignal.SWIPE_DOWN
            ]
        )
    }


}
