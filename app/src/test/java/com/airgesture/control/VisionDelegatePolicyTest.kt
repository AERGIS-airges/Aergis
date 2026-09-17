package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionDelegatePolicyTest {
    @Test
    fun pointerPrefersGpuWhenAllowed() {
        assertEquals(
            VisionDelegateMode.GPU,
            VisionDelegatePolicy.preferred(
                pointerActive = true,
                gpuAllowed = true
            )
        )
    }

    @Test
    fun gestureOnlyStaysCpu() {
        assertEquals(
            VisionDelegateMode.CPU,
            VisionDelegatePolicy.preferred(
                pointerActive = false,
                gpuAllowed = true
            )
        )
    }

    @Test
    fun gpuCircuitBreakerForcesCpu() {
        assertEquals(
            VisionDelegateMode.CPU,
            VisionDelegatePolicy.preferred(
                pointerActive = true,
                gpuAllowed = false
            )
        )
    }
}
