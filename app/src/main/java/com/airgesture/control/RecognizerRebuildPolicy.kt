package com.airgesture.control

data class RecognizerConfiguration(
    val delegate: VisionDelegateMode,
    val numHands: Int
)

/**
 * Decides whether the live GestureRecognizer must be rebuilt.
 *
 * numHands (1 during pointer sessions, 2 otherwise — see
 * POINTER_TRACKING_RESEARCH.md) is baked in at construction time and cannot
 * change on a live instance, exactly like delegate mode. A rebuild decision
 * must therefore compare BOTH dimensions together: comparing delegate alone
 * silently drops a numHands change whenever GPU availability doesn't also
 * flip (e.g. GPU disabled for the whole session, so delegate is always CPU).
 */
object RecognizerRebuildPolicy {
    fun desiredNumHands(pointerActive: Boolean): Int =
        if (pointerActive) 1 else 2

    fun desiredConfiguration(
        pointerActive: Boolean,
        gpuAllowed: Boolean
    ): RecognizerConfiguration =
        RecognizerConfiguration(
            delegate = VisionDelegatePolicy.preferred(
                pointerActive = pointerActive,
                gpuAllowed = gpuAllowed
            ),
            numHands = desiredNumHands(pointerActive)
        )

    fun rebuildRequired(
        active: RecognizerConfiguration?,
        desired: RecognizerConfiguration
    ): Boolean = active == null || active != desired
}
