package com.airgesture.control

data class SessionStartDecision(
    val allowed: Boolean,
    val message: String? = null
)

object SessionStartPolicy {
    fun evaluate(
        action: String?,
        expectedStartAction: String,
        hasCameraPermission: Boolean,
        accessibilityReady: Boolean
    ): SessionStartDecision {
        if (action != expectedStartAction) {
            return SessionStartDecision(
                allowed = false,
                message = "Air control requires an explicit start"
            )
        }

        if (!hasCameraPermission) {
            return SessionStartDecision(
                allowed = false,
                message = "Camera permission is required"
            )
        }

        if (!accessibilityReady) {
            return SessionStartDecision(
                allowed = false,
                message = "Enable Air Gesture accessibility service first"
            )
        }

        return SessionStartDecision(allowed = true)
    }
}
