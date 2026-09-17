package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandIdentitySignatureTest {
    private fun p(
        x: Float,
        y: Float,
        z: Float = 0f
    ) = PoseLandmark(x, y, z)

    private fun hand(): MutableList<PoseLandmark> {
        val h = MutableList(21) { p(.5f, .5f) }
        h[0] = p(.50f, .78f)
        h[5] = p(.40f, .58f)
        h[9] = p(.49f, .55f)
        h[13] = p(.58f, .58f)
        h[17] = p(.66f, .64f)
        return h
    }

    @Test
    fun fingerArticulationDoesNotChangePalmIdentity() {
        val first = hand()
        val changed = hand().apply {
            this[8] = p(.20f, .15f, -.3f)
            this[12] = p(.80f, .20f, .2f)
            this[16] = p(.75f, .70f, -.2f)
            this[20] = p(.30f, .65f, .4f)
            this[4] = p(.10f, .55f, -.5f)
        }

        val a =
            HandIdentitySignature
                .fromImage(first, 1f)
        val b =
            HandIdentitySignature
                .fromImage(changed, 1f)

        assertEquals(a.size, b.size)
        assertEquals(
            0f,
            HandIdentitySignature
                .distance(a, b) ?: 1f,
            .0001f
        )
    }

    @Test
    fun uniformMetricScalePreservesIdentitySignature() {
        val base = hand()
        val scaled =
            base.map {
                p(
                    it.x * 1.6f,
                    it.y * 1.6f,
                    it.z * 1.6f
                )
            }

        val a =
            HandIdentitySignature
                .fromGeometry(base)
        val b =
            HandIdentitySignature
                .fromGeometry(scaled)
        val distance =
            HandIdentitySignature
                .distance(a, b)

        assertNotNull(distance)
        assertTrue((distance ?: 1f) < .0001f)
    }

    @Test
    fun materiallyDifferentPalmProportionsProduceDistance() {
        val base = hand()
        val changed =
            hand().apply {
                this[17] = p(.85f, .68f)
                this[13] = p(.68f, .58f)
            }

        val distance =
            HandIdentitySignature
                .distance(
                    HandIdentitySignature
                        .fromGeometry(base),
                    HandIdentitySignature
                        .fromGeometry(changed)
                )

        assertNotNull(distance)
        assertTrue((distance ?: 0f) > .12f)
    }
}
