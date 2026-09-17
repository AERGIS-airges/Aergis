package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerTouchDragGateTest {

    @Test
    fun subThresholdJitterStaysAnchoredToTouchDown() {
        val gate = PointerTouchDragGate(
            dragStartDistancePx = 20f
        )
        gate.start(100f, 200f)

        val target =
            gate.update(
                x = 112f,
                y = 208f
            )!!

        assertFalse(target.dragging)
        assertEquals(100f, target.x, .001f)
        assertEquals(200f, target.y, .001f)
        assertFalse(gate.isDragging())
    }

    @Test
    fun deliberateTravelCrossesThresholdAndFollowsPointer() {
        val gate = PointerTouchDragGate(
            dragStartDistancePx = 20f
        )
        gate.start(100f, 200f)

        val target =
            gate.update(
                x = 125f,
                y = 200f
            )!!

        assertTrue(target.dragging)
        assertEquals(125f, target.x, .001f)
        assertEquals(200f, target.y, .001f)
        assertTrue(gate.isDragging())
    }

    @Test
    fun dragLatchDoesNotFallBackToTapWhenPointerReturnsNearOrigin() {
        val gate = PointerTouchDragGate(
            dragStartDistancePx = 20f
        )
        gate.start(100f, 200f)
        gate.update(130f, 200f)

        val returned =
            gate.update(
                x = 103f,
                y = 202f
            )!!

        assertTrue(returned.dragging)
        assertEquals(103f, returned.x, .001f)
        assertEquals(202f, returned.y, .001f)
    }

    @Test
    fun resetRequiresANewTouchDownBeforeMovementCanResume() {
        val gate = PointerTouchDragGate(
            dragStartDistancePx = 20f
        )
        gate.start(100f, 200f)
        gate.update(130f, 200f)
        gate.reset()

        assertEquals(
            null,
            gate.update(
                x = 160f,
                y = 200f
            )
        )

        val restarted = gate.start(40f, 50f)
        assertFalse(restarted.dragging)
        assertEquals(40f, restarted.x, .001f)
        assertEquals(50f, restarted.y, .001f)
    }
}
