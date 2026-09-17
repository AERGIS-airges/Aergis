package com.airgesture.control

enum class TouchAcquireFailure {
    EXPIRED,
    BUSY
}

data class TouchActionTicket(
    val id: Long,
    val label: String
)

data class TouchAcquireResult(
    val ticket: TouchActionTicket? = null,
    val failure: TouchAcquireFailure? = null
)

/**
 * Owns Android touch-gesture dispatches until AccessibilityService reports
 * completion/cancellation. Commands are never queued behind an active touch;
 * obsolete commands expire instead of creating a delayed action backlog.
 */
class TouchActionCoordinator {
    private var nextId = 0L
    private var active: TouchActionTicket? = null

    @Synchronized
    fun tryAcquire(
        nowMs: Long,
        deadlineMs: Long,
        label: String
    ): TouchAcquireResult {
        if (nowMs > deadlineMs) {
            return TouchAcquireResult(
                failure = TouchAcquireFailure.EXPIRED
            )
        }

        if (active != null) {
            return TouchAcquireResult(
                failure = TouchAcquireFailure.BUSY
            )
        }

        val ticket =
            TouchActionTicket(
                id = ++nextId,
                label = label
            )
        active = ticket
        return TouchAcquireResult(ticket = ticket)
    }

    @Synchronized
    fun release(ticket: TouchActionTicket): Boolean {
        if (active?.id != ticket.id) return false
        active = null
        return true
    }

    @Synchronized
    fun activeLabel(): String? = active?.label

    @Synchronized
    fun clearOwnership() {
        active = null
    }
}
