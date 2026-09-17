package com.airgesture.control

/**
 * Process-local R8 diagnostics transport.
 *
 * It deliberately carries observation only. Nothing in this object is allowed
 * to select a filter or alter the production pointer path.
 */
data class PointerFilterDiagnosticState(
    val updatedAtMs: Long = 0L,
    val productionControl: String = "R7 PointerMotionFilter",
    val challenger: String = "OneEuroPointerFilter — shadow only",
    val summary: String = "Pointer filter A/B: no samples",
    val snapshot: PointerFilterBenchmarkSnapshot? = null
)

object PointerFilterDiagnostics {
    @Volatile
    private var latest = PointerFilterDiagnosticState()

    fun publish(
        timestampMs: Long,
        summary: String,
        snapshot: PointerFilterBenchmarkSnapshot
    ) {
        latest =
            PointerFilterDiagnosticState(
                updatedAtMs = timestampMs,
                summary = summary,
                snapshot = snapshot
            )
    }

    fun current(): PointerFilterDiagnosticState = latest

    fun reset() {
        latest = PointerFilterDiagnosticState()
    }
}
