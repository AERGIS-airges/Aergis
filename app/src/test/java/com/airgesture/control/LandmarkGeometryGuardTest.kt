package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkGeometryGuardTest {

    @Test
    fun normalNormalizedLandmarkIsAccepted() {
        assertTrue(
            LandmarkGeometryGuard
                .landmarkPlausible(
                    x = .52f,
                    y = .31f,
                    z = -.12f
                )
        )
    }

    @Test
    fun modestOffFrameLandmarkIsStillAccepted() {
        assertTrue(
            LandmarkGeometryGuard
                .landmarkPlausible(
                    x = -.12f,
                    y = 1.08f,
                    z = -.40f
                )
        )
    }

    @Test
    fun substantialOffFramePalmLandmarkCanRemainValidForContinuation() {
        assertTrue(
            LandmarkGeometryGuard
                .landmarkPlausible(
                    x = .48f,
                    y = 1.85f,
                    z = -.30f
                )
        )
        assertTrue(
            LandmarkGeometryGuard
                .candidatePlausible(
                    centerX = .50f,
                    centerY = 1.55f,
                    rawScale = .18f
                )
        )
    }

    @Test
    fun extremeFiniteCoordinatesAreRejected() {
        assertFalse(
            LandmarkGeometryGuard
                .landmarkPlausible(
                    x = 5f,
                    y = .5f,
                    z = 0f
                )
        )
        assertFalse(
            LandmarkGeometryGuard
                .landmarkPlausible(
                    x = .5f,
                    y = .5f,
                    z = -5f
                )
        )
        assertFalse(
            LandmarkGeometryGuard
                .candidatePlausible(
                    centerX = .5f,
                    centerY = 3f,
                    rawScale = .16f
                )
        )
    }

    @Test
    fun absurdHandScaleIsRejectedBeforeClamp() {
        assertFalse(
            LandmarkGeometryGuard
                .candidatePlausible(
                    centerX = .5f,
                    centerY = .5f,
                    rawScale = 1.8f
                )
        )
        assertFalse(
            LandmarkGeometryGuard
                .candidatePlausible(
                    centerX = .5f,
                    centerY = .5f,
                    rawScale = .005f
                )
        )
        assertTrue(
            LandmarkGeometryGuard
                .candidatePlausible(
                    centerX = .5f,
                    centerY = .5f,
                    rawScale = .16f
                )
        )
    }
}
