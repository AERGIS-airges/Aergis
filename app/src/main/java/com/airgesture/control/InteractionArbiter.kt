package com.airgesture.control

/**
 * R11 interaction ownership gate.
 *
 * Air Pointer is now an explicit interaction mode. While it is enabled, the
 * pose classifier may still run for diagnostics, but its categories do not
 * own the hand, block pointer contact, or execute mapped gestures. This makes
 * ordinary relaxed-hand shape changes inert instead of actionable.
 *
 * Generic gesture execution remains available when Air Pointer is disabled.
 * A future explicit gesture-arm state can be added without returning to
 * continuous pose-to-action arbitration during pointer control.
 */
object InteractionArbiter {
    @Suppress("UNUSED_PARAMETER")
    fun pointerPressAllowed(
        category: String,
        categoryScore: Float
    ): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun gestureExecutionAllowed(
        pointerEnabled: Boolean,
        pointerState: String,
        signal: GestureSignal
    ): Boolean = !pointerEnabled
}
