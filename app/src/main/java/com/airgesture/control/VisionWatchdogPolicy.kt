package com.airgesture.control

/** Shared by watchdog observation and delayed main-thread revalidation. */
object VisionWatchdogPolicy {
    fun isStalled(
        firstSubmittedMs: Long,
        lastResultMs: Long,
        nowMs: Long,
        timeoutMs: Long,
        recovering: Boolean
    ): Boolean {
        if (recovering || firstSubmittedMs <= 0L || timeoutMs <= 0L) return false
        val reference = maxOf(firstSubmittedMs, lastResultMs)
        return nowMs >= reference && nowMs - reference >= timeoutMs
    }
}
