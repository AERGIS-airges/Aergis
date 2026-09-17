package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlHandSelectorTest {

    private fun hand(
        index: Int,
        x: Float,
        y: Float,
        handedness: String,
        score: Float = .95f,
        scale: Float = .14f,
        identity: List<Float> =
            emptyList()
    ) = HandCandidate(
        index = index,
        centerX = x,
        centerY = y,
        scale = scale,
        handedness = handedness,
        handednessScore = score,
        identitySignature = identity
    )

    @Test
    fun autoModeKeepsSamePhysicalHandWhenResultOrderChanges() {
        val selector = ControlHandSelector()

        assertNull(
            selector.select(
                0,
                listOf(
                    hand(
                        0,
                        .28f,
                        .52f,
                        "Left",
                        scale = .16f
                    ),
                    hand(
                        1,
                        .75f,
                        .50f,
                        "Right",
                        scale = .14f
                    )
                )
            )
        )

        val first =
            selector.select(
                40,
                listOf(
                    hand(
                        0,
                        .29f,
                        .52f,
                        "Left",
                        scale = .16f
                    ),
                    hand(
                        1,
                        .75f,
                        .50f,
                        "Right",
                        scale = .14f
                    )
                )
            )!!

        assertEquals(0, first.index)

        val reordered = selector.select(
            80,
            listOf(
                hand(0, .74f, .51f, "Right", scale = .14f),
                hand(1, .30f, .53f, "Left", scale = .16f)
            )
        )!!

        assertEquals(1, reordered.index)
        assertEquals("Left", reordered.handedness)
        assertTrue(reordered.continuityLocked)
    }

    @Test
    fun explicitRightPreferenceRejectsLeftHand() {
        val selector = ControlHandSelector(
            ControlHandPreference.RIGHT
        )

        assertNull(
            selector.select(
                0,
                listOf(hand(0, .4f, .5f, "Left"))
            )
        )

        val selected = selector.select(
            20,
            listOf(
                hand(0, .4f, .5f, "Left"),
                hand(1, .6f, .5f, "Right")
            )
        )

        assertEquals(1, selected?.index)
        assertEquals("Right", selected?.handedness)
    }

    @Test
    fun preferenceChangeResetsContinuityLock() {
        val selector = ControlHandSelector()
        selector.select(
            0,
            listOf(hand(0, .25f, .5f, "Left"))
        )

        selector.updatePreference(ControlHandPreference.RIGHT)

        val selected = selector.select(
            20,
            listOf(hand(0, .75f, .5f, "Right"))
        )!!

        assertEquals("Right", selected.handedness)
        assertEquals(false, selected.continuityLocked)
    }

    @Test
    fun temporaryLossDoesNotForceContinuityReset() {
        val selector = ControlHandSelector(lostResetMs = 400L)

        selector.select(
            0,
            listOf(hand(0, .30f, .5f, "Left"))
        )
        assertNull(selector.select(120, emptyList()))

        val selected = selector.select(
            200,
            listOf(hand(1, .31f, .51f, "Left"))
        )!!

        assertTrue(selected.continuityLocked)
    }

    @Test
    fun longPreferredHandAbsenceExpiresOldContinuity() {
        val selector = ControlHandSelector(
            initialPreference = ControlHandPreference.RIGHT,
            lostResetMs = 200L
        )

        selector.select(
            0,
            listOf(hand(0, .70f, .5f, "Right"))
        )

        assertNull(
            selector.select(
                250,
                listOf(hand(0, .30f, .5f, "Left"))
            )
        )

        val returned = selector.select(
            300,
            listOf(hand(0, .25f, .5f, "Right"))
        )!!

        assertEquals(false, returned.continuityLocked)
    }

    @Test
    fun longLossResetsPreviousHandIdentity() {
        val selector = ControlHandSelector(lostResetMs = 200L)

        selector.select(
            0,
            listOf(hand(0, .30f, .5f, "Left"))
        )
        assertNull(selector.select(250, emptyList()))

        val selected = selector.select(
            300,
            listOf(hand(1, .31f, .51f, "Left"))
        )!!

        assertEquals(false, selected.continuityLocked)
    }
    @Test
    fun singleDiscontinuousFrameIsNotAdopted() {
        val selector = ControlHandSelector(
            discontinuityConfirmFrames = 2
        )

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .28f,
                    .50f,
                    "Left",
                    scale = .16f
                )
            )
        )

        val outlier =
            selector.select(
                40,
                listOf(
                    hand(
                        0,
                        .82f,
                        .50f,
                        "Right",
                        scale = .16f
                    )
                )
            )

        assertNull(outlier)

        val originalReturns =
            selector.select(
                80,
                listOf(
                    hand(
                        0,
                        .30f,
                        .51f,
                        "Left",
                        scale = .16f
                    )
                )
            )!!

        assertEquals("Left", originalReturns.handedness)
        assertTrue(originalReturns.continuityLocked)
    }

    @Test
    fun consistentNewHandNeedsTwoFramesBeforeAdoption() {
        val selector = ControlHandSelector(
            discontinuityConfirmFrames = 2
        )

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .25f,
                    .50f,
                    "Left",
                    scale = .15f
                )
            )
        )

        assertNull(
            selector.select(
                40,
                listOf(
                    hand(
                        0,
                        .76f,
                        .50f,
                        "Right",
                        scale = .15f
                    )
                )
            )
        )

        val adopted =
            selector.select(
                80,
                listOf(
                    hand(
                        0,
                        .75f,
                        .51f,
                        "Right",
                        scale = .15f
                    )
                )
            )!!

        assertEquals("Right", adopted.handedness)
        assertEquals(false, adopted.continuityLocked)
    }


    @Test
    fun explicitPreferenceKeepsSpatiallyContinuousHandDuringWeakLabelFlicker() {
        val selector =
            ControlHandSelector(
                ControlHandPreference.RIGHT
            )

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .68f,
                    .50f,
                    "Right",
                    score = .94f
                )
            )
        )

        val weakFlicker =
            selector.select(
                50,
                listOf(
                    hand(
                        1,
                        .69f,
                        .51f,
                        "Left",
                        score = .35f
                    )
                )
            )

        assertEquals(
            "Unknown",
            weakFlicker?.handedness
        )
        assertTrue(
            weakFlicker?.continuityLocked ==
                true
        )
    }

    @Test
    fun explicitPreferenceRejectsConfidentOppositeHandEvenIfNearby() {
        val selector =
            ControlHandSelector(
                ControlHandPreference.RIGHT
            )

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .68f,
                    .50f,
                    "Right",
                    score = .94f
                )
            )
        )

        assertNull(
            selector.select(
                50,
                listOf(
                    hand(
                        1,
                        .69f,
                        .51f,
                        "Left",
                        score = .92f
                    )
                )
            )
        )
    }


    @Test
    fun singleHandInitialAcquisitionRemainsImmediate() {
        val selector = ControlHandSelector()

        val selected =
            selector.select(
                0,
                listOf(
                    hand(
                        0,
                        .42f,
                        .50f,
                        "Left",
                        scale = .15f
                    )
                )
            )

        assertEquals(0, selected?.index)
        assertEquals(false, selected?.continuityLocked)
    }

    @Test
    fun twoHandInitialAcquisitionNeedsConfirmation() {
        val selector =
            ControlHandSelector(
                multiHandAcquireConfirmFrames = 2
            )

        val first =
            selector.select(
                0,
                listOf(
                    hand(
                        0,
                        .28f,
                        .50f,
                        "Left",
                        scale = .17f
                    ),
                    hand(
                        1,
                        .72f,
                        .50f,
                        "Right",
                        scale = .14f
                    )
                )
            )

        assertNull(first)

        val confirmed =
            selector.select(
                45,
                listOf(
                    hand(
                        0,
                        .29f,
                        .50f,
                        "Left",
                        scale = .17f
                    ),
                    hand(
                        1,
                        .72f,
                        .50f,
                        "Right",
                        scale = .14f
                    )
                )
            )

        assertEquals(0, confirmed?.index)
        assertEquals(false, confirmed?.continuityLocked)
    }

    @Test
    fun fastSmallHandMotionKeepsContinuityInsteadOfDroppingSwipe() {
        val selector = ControlHandSelector()

        val first =
            selector.select(
                0,
                listOf(
                    hand(
                        0,
                        .28f,
                        .50f,
                        "Left",
                        scale = .05f
                    )
                )
            )
        assertEquals(0, first?.index)

        // 0.22 screen widths is more than four palm widths for this distant
        // hand, but is still a plausible fast physical motion.
        val fast =
            selector.select(
                70,
                listOf(
                    hand(
                        1,
                        .50f,
                        .50f,
                        "Left",
                        scale = .05f
                    )
                )
            )

        assertEquals(1, fast?.index)
        assertTrue(fast?.continuityLocked == true)
    }

    @Test
    fun autoModeKeepsSpatialHandThroughConfidentHandednessFlicker() {
        val selector = ControlHandSelector()

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .36f,
                    .52f,
                    "Left",
                    score = .95f,
                    scale = .12f
                )
            )
        )

        val flicker =
            selector.select(
                60,
                listOf(
                    hand(
                        1,
                        .42f,
                        .51f,
                        "Right",
                        score = .94f,
                        scale = .12f
                    )
                )
            )

        assertEquals(1, flicker?.index)
        assertTrue(flicker?.continuityLocked == true)
    }


    @Test
    fun nearbyReplacementWithDifferentPalmSignatureCannotContinuityLock() {
        val selector =
            ControlHandSelector(
                discontinuityConfirmFrames = 2
            )
        val firstSignature =
            listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        val replacementSignature =
            listOf(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f)

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .48f,
                    .50f,
                    "Left",
                    identity =
                        firstSignature
                )
            )
        )

        val nearbyReplacement =
            selector.select(
                50,
                listOf(
                    hand(
                        1,
                        .49f,
                        .50f,
                        "Left",
                        identity =
                            replacementSignature
                    )
                )
            )

        assertNull(nearbyReplacement)

        val confirmedReplacement =
            selector.select(
                100,
                listOf(
                    hand(
                        1,
                        .50f,
                        .50f,
                        "Left",
                        identity =
                            replacementSignature
                    )
                )
            )

        assertEquals(
            1,
            confirmedReplacement?.index
        )
        assertEquals(
            false,
            confirmedReplacement
                ?.continuityLocked
        )
    }

    @Test
    fun matchingPalmSignatureWinsCrossingHands() {
        val selector = ControlHandSelector()
        val controlled =
            listOf(1f, .9f, .8f, .7f, .6f, .5f, .4f, .3f)
        val other =
            listOf(.4f, .5f, .6f, .7f, .8f, .9f, 1f, 1.1f)

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .35f,
                    .50f,
                    "Left",
                    identity = controlled
                )
            )
        )

        val crossing =
            selector.select(
                70,
                listOf(
                    hand(
                        0,
                        .49f,
                        .50f,
                        "Right",
                        score = .60f,
                        identity = other
                    ),
                    hand(
                        1,
                        .51f,
                        .50f,
                        "Left",
                        score = .60f,
                        identity = controlled
                    )
                )
            )

        assertEquals(1, crossing?.index)
        assertTrue(
            crossing?.continuityLocked ==
                true
        )
    }

    @Test
    fun equallyPlausibleMultiHandCandidatesAbstain() {
        val selector =
            ControlHandSelector(
                minMultiHandScoreMargin = .08f
            )

        selector.select(
            0,
            listOf(
                hand(
                    0,
                    .50f,
                    .50f,
                    "Unknown",
                    score = .20f
                )
            )
        )

        val ambiguous =
            selector.select(
                50,
                listOf(
                    hand(
                        0,
                        .49f,
                        .50f,
                        "Unknown",
                        score = .20f
                    ),
                    hand(
                        1,
                        .51f,
                        .50f,
                        "Unknown",
                        score = .20f
                    )
                )
            )

        assertNull(ambiguous)
    }

}
