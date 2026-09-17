package com.airgesture.control

object AirActionCapabilities {
    fun isSupported(
        action: AirAction,
        sdkInt: Int
    ): Boolean =
        when (action) {
            AirAction.MEDIA_PLAY_PAUSE ->
                sdkInt >= 36
            else -> true
        }
}
