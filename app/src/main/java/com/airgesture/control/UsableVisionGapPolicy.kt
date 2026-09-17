package com.airgesture.control

object UsableVisionGapPolicy {
    fun shouldFailSafeReset(
        firstSubmittedMs: Long,
        lastUsableResultMs: Long,
        nowMs: Long,
        pointerActive: Boolean,
        alreadyApplied: Boolean
    ): Boolean {
        if (alreadyApplied) return false
        val reference =
            maxOf(
                firstSubmittedMs,
                lastUsableResultMs
            )
        if (
            reference <= 0L ||
            nowMs < reference
        ) {
            return false
        }

        return nowMs - reference >
            VisionLatencyPolicy
                .hardMaxResultAgeMs(
                    pointerActive
                )
    }
}
