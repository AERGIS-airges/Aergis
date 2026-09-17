package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartPolicyTest {

    @Test
    fun nullRestartIntentCanNeverReacquireCamera() {
        val result = SessionStartPolicy.evaluate(
            action = null,
            expectedStartAction = "START",
            hasCameraPermission = true,
            accessibilityReady = true
        )

        assertFalse(result.allowed)
    }

    @Test
    fun missingCameraPermissionBlocksSession() {
        val result = SessionStartPolicy.evaluate(
            action = "START",
            expectedStartAction = "START",
            hasCameraPermission = false,
            accessibilityReady = true
        )

        assertFalse(result.allowed)
    }

    @Test
    fun disabledAccessibilityBlocksSession() {
        val result = SessionStartPolicy.evaluate(
            action = "START",
            expectedStartAction = "START",
            hasCameraPermission = true,
            accessibilityReady = false
        )

        assertFalse(result.allowed)
    }

    @Test
    fun explicitStartWithPrerequisitesIsAllowed() {
        val result = SessionStartPolicy.evaluate(
            action = "START",
            expectedStartAction = "START",
            hasCameraPermission = true,
            accessibilityReady = true
        )

        assertTrue(result.allowed)
        assertNull(result.message)
    }
}
