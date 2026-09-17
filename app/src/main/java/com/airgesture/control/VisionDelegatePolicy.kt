package com.airgesture.control

enum class VisionDelegateMode {
    CPU,
    GPU
}

/**
 * GPU is reserved for Air Pointer sessions where inference latency is a
 * release-critical UX constraint. Gesture-only sessions stay on CPU to avoid
 * paying extra GPU/battery cost without a measured pointer benefit.
 */
object VisionDelegatePolicy {
    fun preferred(
        pointerActive: Boolean,
        gpuAllowed: Boolean
    ): VisionDelegateMode =
        if (
            pointerActive &&
            gpuAllowed
        ) {
            VisionDelegateMode.GPU
        } else {
            VisionDelegateMode.CPU
        }
}
