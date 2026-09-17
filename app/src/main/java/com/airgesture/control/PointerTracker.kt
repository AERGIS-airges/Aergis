package com.airgesture.control

data class PointerFrame(
    val timestampMs: Long,
    val handPresent: Boolean,
    val aimX: Float = 0.5f,
    val aimY: Float = 0.5f,
    val aimConfidence: Float = 1f,
    val aimStable: Boolean = true,
    /**
     * Normalized distance between MediaPipe index tip #8 and middle tip #12.
     * Lower means closer. This is the only Pointer Tap/Hold shape input.
     */
    val indexMiddleTipSeparation: Float = Float.POSITIVE_INFINITY,
    val clickContactReliable: Boolean = false,
    /** Retained only for binary/source compatibility. It is never used to click. */
    val indexStraightness: Float = 1f,
    val pressAllowed: Boolean = true,
    val resultAgeMs: Long = 0L
)

data class PointerDecision(
    val visible: Boolean,
    val x: Float,
    val y: Float,
    val tap: Boolean = false,
    val hold: Boolean = false,
    val actionX: Float? = null,
    val actionY: Float? = null,
    /** 0 = tips separated, 1 = index+middle tips in click-contact region. */
    val pressAmount: Float = 0f,
    val state: String,
    val confidence: Float,
    val reasoning: String = "—"
)

/**
 * Pointer interaction contract:
 *
 * POSITION
 * - Pointer X/Y arrives through aimX/aimY and is the calibrated MediaPipe
 *   INDEX_FINGER_TIP (#8) coordinate only.
 * - Palm/wrist/finger-body geometry is not consumed by this class for X/Y.
 *
 * CLICK
 * - Pointer click is armed only by deliberate INDEX_FINGER_TIP (#8) +
 *   MIDDLE_FINGER_TIP (#12) contact.
 * - Index bend/straightness, direction changes and thumb proximity cannot
 *   create a click.
 * - Contact must be confirmed briefly and then released for Tap.
 * - Sustained confirmed contact represents a continuous touchscreen finger
 *   down. The accessibility layer may turn pointer movement during that
 *   contact into a native drag/scroll path.
 * - Separate enter/release/neutral thresholds provide hysteresis so landmark
 *   shimmer around one threshold cannot chatter actions.
 */
class PointerTracker(
    initialCalibration: PointerCalibration = PointerCalibration.DEFAULT,
    private val presenceArmMs: Long = 120L,
    private val neutralArmMs: Long = 100L,
    private val candidateConfirmMs: Long = 55L,
    private val candidateTimeoutMs: Long = 450L,
    private val tapMinMs: Long = 70L,
    private val holdCommitMs: Long = 680L,
    private val pressTimeoutMs: Long = 12_000L,
    private val actionCooldownMs: Long = 260L,
    /** Cursor-only grace: brief detector loss should not make control feel brittle. */
    private val pointerCoastGraceMs: Long = 360L,
    /** Press/contact grace stays short so a virtual finger can never remain stuck down. */
    private val contactOcclusionGraceMs: Long = 130L,
    /** Post-calibration comfort gain reaches display edges before the fingertip reaches camera edges. */
    private val comfortReachGainX: Float = 1.15f,
    private val comfortReachGainY: Float = 1.18f,
    /** Tips this close are treated as intentional near-contact; exact physical touching is not required. */
    private val contactEnterSeparation: Float = 0.34f,
    /** A sustained hold may tolerate slightly more landmark shimmer. */
    private val holdContactSeparation: Float = 0.38f,
    /** Contact is not released until the tips move clearly farther apart. */
    private val contactReleaseSeparation: Float = 0.45f,
    /** New actions re-arm only after an unmistakably separated pose. */
    private val neutralSeparation: Float = 0.48f
) {
    @Volatile
    private var calibration = initialCalibration.sanitized()

    private var presenceSinceMs: Long? = null
    private var lastSeenMs: Long? = null
    private val motionFilter = PointerMotionFilter()

    private var neutralSinceMs: Long? = null
    private var contactArmed = false

    private var candidateSinceMs: Long? = null
    private var candidateActionX = 0.5f
    private var candidateActionY = 0.5f
    private var candidatePeakContact = 0f

    private var pressStartedMs: Long? = null
    private var pressActionX = 0.5f
    private var pressActionY = 0.5f
    private var maxPressAmount = 0f
    private var holdLatched = false

    private var lastActionMs = Long.MIN_VALUE / 4
    private var contactUnknownSinceMs: Long? = null

    fun update(frame: PointerFrame): PointerDecision {
        if (!frame.handPresent) {
            val lastSeen = lastSeenMs
            val cursor = motionFilter.current()
            val x = cursor?.first
            val y = cursor?.second

            if (
                lastSeen != null &&
                x != null &&
                y != null &&
                frame.timestampMs - lastSeen <= pointerCoastGraceMs
            ) {
                val preservingClick =
                    bridgeContactGap(frame.timestampMs)
                if (!preservingClick) {
                    cancelPressIntent(clearNeutralArm = true)
                }
                return PointerDecision(
                    visible = true,
                    x = x,
                    y = y,
                    state =
                        if (preservingClick) "PRESS_OCCLUDED" else "COAST",
                    confidence =
                        if (preservingClick) 0.45f else 0.25f,
                    reasoning =
                        if (preservingClick) {
                            "Brief hand loss • holding index-tip #8 and preserving confirmed click contact"
                        } else {
                            "Brief tracking loss • preserving last index-tip #8 position"
                        }
                )
            }

            // Once the click-safety grace expires, hide the cursor and clear all
            // action/presence state, but retain the last motion-filter position.
            // ControlHandSelector keeps same-hand identity for a longer window;
            // if that same hand reappears, the first noisy reacquisition sample
            // must still pass through PointerMotionFilter's shock guard instead
            // of teleporting the cursor. A real ownership discontinuity resets
            // the filter explicitly in onControlHandDiscontinuity().
            resetPresence(resetMotionFilter = false)
            return PointerDecision(
                visible = false,
                x = 0.5f,
                y = 0.5f,
                state = "HIDDEN",
                confidence = 0f,
                reasoning = "Pointer index fingertip not visible"
            )
        }

        val now = frame.timestampMs
        lastSeenMs = now
        if (presenceSinceMs == null) {
            presenceSinceMs = now
        }

        val calibrationSnapshot = calibration
        val mappedX =
            comfortReach(
                calibrationSnapshot.mapX(frame.aimX),
                comfortReachGainX
            )
        val mappedY =
            comfortReach(
                calibrationSnapshot.mapY(frame.aimY),
                comfortReachGainY
            )

        if (!frame.aimStable) {
            val preservingClick = bridgeContactGap(now)
            if (!preservingClick) {
                cancelPressIntent(clearNeutralArm = true)
            }

            val stable = motionFilter.current()
            val stableX = stable?.first
            val stableY = stable?.second

            return PointerDecision(
                visible = stableX != null && stableY != null,
                x = stableX ?: 0.5f,
                y = stableY ?: 0.5f,
                state =
                    if (preservingClick) "PRESS_OCCLUDED" else "POINTING_AMBIGUOUS",
                confidence =
                    if (preservingClick) 0.45f else frame.aimConfidence.coerceIn(0f, 0.32f),
                reasoning =
                    if (preservingClick) {
                        "Index fingertip #8 briefly occluded • holding cursor and preserving confirmed click contact"
                    } else {
                        "Index fingertip #8 unavailable • holding last stable cursor • click suppressed"
                    }
            )
        }

        val cursor =
            trackAimAdaptive(
                x = mappedX,
                y = mappedY,
                now = now,
                confidence = frame.aimConfidence,
                resultAgeMs = frame.resultAgeMs
            )

        val separation =
            frame.indexMiddleTipSeparation
                .takeIf { it.isFinite() && it >= 0f }
                ?: Float.POSITIVE_INFINITY
        val contactReliable =
            frame.clickContactReliable && separation.isFinite()
        val contactAmount = contactAmount(separation)

        val presenceArmed =
            now - (presenceSinceMs ?: now) >= presenceArmMs

        if (!presenceArmed) {
            if (contactReliable) {
                updateNeutralArm(now, separation)
            }
            return PointerDecision(
                visible = true,
                x = cursor.first,
                y = cursor.second,
                pressAmount = contactAmount,
                state = "ARMING",
                confidence = 0.35f,
                reasoning =
                    "Acquiring index fingertip #8 • waiting for index+middle tips to separate"
            )
        }

        val contactOverridesPoseBlock =
            contactReliable &&
                separation <= holdContactSeparation
        val clickSequenceActive =
            candidateSinceMs != null ||
                pressStartedMs != null

        if (
            !frame.pressAllowed &&
            !contactOverridesPoseBlock &&
            !clickSequenceActive
        ) {
            cancelPressIntent(clearNeutralArm = true)
            if (contactReliable) {
                updateNeutralArm(now, separation)
            }
            return PointerDecision(
                visible = true,
                x = cursor.first,
                y = cursor.second,
                pressAmount = contactAmount,
                state = "POINTING_LOCKED",
                confidence = 0.72f,
                reasoning =
                    "Index-tip #8 cursor active • index+middle click suppressed by mapped gesture"
            )
        }

        if (!contactReliable) {
            val preservingClick = bridgeContactGap(now)
            if (!preservingClick) {
                cancelPressIntent(clearNeutralArm = true)
            }
            return PointerDecision(
                visible = true,
                x = cursor.first,
                y = cursor.second,
                actionX =
                    if (preservingClick) {
                        if (pressStartedMs != null) pressActionX else candidateActionX
                    } else null,
                actionY =
                    if (preservingClick) {
                        if (pressStartedMs != null) pressActionY else candidateActionY
                    } else null,
                pressAmount = 0f,
                state =
                    if (preservingClick) "PRESS_OCCLUDED" else "POINTING_CONTACT_UNKNOWN",
                confidence =
                    if (preservingClick) 0.55f else frame.aimConfidence.coerceIn(0.45f, 0.86f),
                reasoning =
                    if (preservingClick) {
                        "Index + middle fingertip geometry briefly occluded • preserving active click sequence"
                    } else {
                        "Index-tip #8 cursor active • tip-contact geometry unavailable • click suppressed"
                    }
            )
        }

        closeContactGap(now)

        val activePressStart = pressStartedMs
        if (activePressStart != null) {
            return updateActivePress(
                now = now,
                separation = separation,
                cursorX = cursor.first,
                cursorY = cursor.second,
                pressStart = activePressStart
            )
        }

        val activeCandidate = candidateSinceMs
        if (activeCandidate != null) {
            return updateCandidate(
                now = now,
                separation = separation,
                cursorX = cursor.first,
                cursorY = cursor.second,
                candidateStart = activeCandidate
            )
        }

        updateNeutralArm(now, separation)

        if (
            contactArmed &&
            separation <= contactEnterSeparation &&
            now - lastActionMs >= actionCooldownMs
        ) {
            candidateSinceMs = now
            candidateActionX = cursor.first
            candidateActionY = cursor.second
            candidatePeakContact = contactAmount
            contactArmed = false
            neutralSinceMs = null

            return PointerDecision(
                visible = true,
                x = cursor.first,
                y = cursor.second,
                actionX = candidateActionX,
                actionY = candidateActionY,
                pressAmount = contactAmount,
                state = "PRESS_CANDIDATE",
                confidence = contactConfidence(separation) * 0.80f,
                reasoning =
                    contactReasoning(
                        label = "Index + middle tip contact candidate",
                        durationMs = 0L,
                        separation = separation
                    )
            )
        }

        return PointerDecision(
            visible = true,
            x = cursor.first,
            y = cursor.second,
            pressAmount = contactAmount,
            state = if (contactArmed) "POINTING" else "POINTING_ARMING",
            confidence = pointingConfidence(frame.aimConfidence, separation),
            reasoning =
                if (contactArmed) {
                    "Index fingertip #8 cursor active • tap by bringing index + middle fingertips together"
                } else {
                    "Index fingertip #8 cursor active • separate index + middle tips briefly to arm click"
                }
        )
    }

    private fun updateCandidate(
        now: Long,
        separation: Float,
        cursorX: Float,
        cursorY: Float,
        candidateStart: Long
    ): PointerDecision {
        val duration = now - candidateStart
        val amount = contactAmount(separation)
        candidatePeakContact = maxOf(candidatePeakContact, amount)

        if (duration > candidateTimeoutMs) {
            clearCandidate()
            contactArmed = false
            neutralSinceMs = null
            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                pressAmount = amount,
                state = "POINTING_ARMING",
                confidence = 0.66f,
                reasoning =
                    "Tip-contact candidate timed out • separate index + middle tips to re-arm"
            )
        }

        if (separation >= contactReleaseSeparation) {
            clearCandidate()
            updateNeutralArm(now, separation)
            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                pressAmount = amount,
                state = "POINTING_ARMING",
                confidence = 0.68f,
                reasoning =
                    "Tip contact ended before confirmation • no click"
            )
        }

        if (
            duration >= candidateConfirmMs &&
            separation <= holdContactSeparation
        ) {
            pressStartedMs = candidateStart
            pressActionX = candidateActionX
            pressActionY = candidateActionY
            maxPressAmount = candidatePeakContact
            holdLatched = false
            clearCandidate()

            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                actionX = pressActionX,
                actionY = pressActionY,
                pressAmount = amount,
                state = "PRESSING",
                confidence = contactConfidence(separation),
                reasoning =
                    contactReasoning(
                        label = "Index + middle fingertip contact confirmed",
                        durationMs = duration,
                        separation = separation
                    )
            )
        }

        return PointerDecision(
            visible = true,
            x = cursorX,
            y = cursorY,
            actionX = candidateActionX,
            actionY = candidateActionY,
            pressAmount = amount,
            state = "PRESS_CANDIDATE",
            confidence = contactConfidence(separation) * 0.82f,
            reasoning =
                contactReasoning(
                    label = "Confirming index + middle fingertip contact",
                    durationMs = duration,
                    separation = separation
                )
        )
    }

    private fun updateActivePress(
        now: Long,
        separation: Float,
        cursorX: Float,
        cursorY: Float,
        pressStart: Long
    ): PointerDecision {
        val duration = now - pressStart
        val amount = contactAmount(separation)
        maxPressAmount = maxOf(maxPressAmount, amount)

        if (duration > pressTimeoutMs) {
            cancelPressIntent(clearNeutralArm = true)
            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                pressAmount = amount,
                state = "POINTING_ARMING",
                confidence = 0.62f,
                reasoning =
                    "Index + middle contact timed out • separate fingertips to re-arm"
            )
        }

        if (holdLatched) {
            if (separation >= contactReleaseSeparation) {
                val actionX = pressActionX
                val actionY = pressActionY
                cancelPressIntent(clearNeutralArm = true)
                updateNeutralArm(now, separation)
                return PointerDecision(
                    visible = true,
                    x = cursorX,
                    y = cursorY,
                    actionX = actionX,
                    actionY = actionY,
                    pressAmount = amount,
                    state = "LONG_PRESS_RELEASE",
                    confidence = 0.92f,
                    reasoning =
                        "Long press released • separate fingertips briefly to re-arm"
                )
            }

            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                actionX = pressActionX,
                actionY = pressActionY,
                pressAmount = amount,
                state = "LONG_PRESS_WAIT_RELEASE",
                confidence = 0.95f,
                reasoning =
                    contactReasoning(
                        label = "Long press already dispatched • release fingertip contact",
                        durationMs = duration,
                        separation = separation
                    )
            )
        }

        if (
            duration >= holdCommitMs &&
            separation <= holdContactSeparation
        ) {
            holdLatched = true
            lastActionMs = now
            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                hold = true,
                actionX = pressActionX,
                actionY = pressActionY,
                pressAmount = amount,
                state = "LONG_PRESS",
                confidence = contactConfidence(separation),
                reasoning =
                    contactReasoning(
                        label = "Index + middle fingertip long press committed",
                        durationMs = duration,
                        separation = separation
                    )
            )
        }

        if (separation >= contactReleaseSeparation) {
            val validTap =
                duration >= tapMinMs &&
                    duration < holdCommitMs &&
                    maxPressAmount >= 0.85f
            val actionX = pressActionX
            val actionY = pressActionY
            val confidence = maxOf(0.78f, maxPressAmount.coerceIn(0f, 1f))

            cancelPressIntent(clearNeutralArm = true)
            updateNeutralArm(now, separation)

            if (validTap) {
                lastActionMs = now
                return PointerDecision(
                    visible = true,
                    x = cursorX,
                    y = cursorY,
                    tap = true,
                    actionX = actionX,
                    actionY = actionY,
                    pressAmount = amount,
                    state = "TAP",
                    confidence = confidence,
                    reasoning =
                        contactReasoning(
                            label = "Index + middle fingertip click committed",
                            durationMs = duration,
                            separation = separation
                        )
                )
            }

            return PointerDecision(
                visible = true,
                x = cursorX,
                y = cursorY,
                pressAmount = amount,
                state = "POINTING_ARMING",
                confidence = 0.68f,
                reasoning =
                    "Fingertips released without confirmed click contact • re-arming"
            )
        }

        return PointerDecision(
            visible = true,
            x = cursorX,
            y = cursorY,
            actionX = pressActionX,
            actionY = pressActionY,
            pressAmount = amount,
            state = "PRESSING",
            confidence = contactConfidence(separation),
            reasoning =
                contactReasoning(
                    label = "Index + middle fingertip contact held",
                    durationMs = duration,
                    separation = separation
                )
        )
    }

    /**
     * Preserve an already-started #8+#12 click sequence across only a very
     * short landmark/contact dropout. Tracking loss itself never starts or
     * commits a click.
     */
    private fun bridgeContactGap(now: Long): Boolean {
        val clickSequenceActive =
            candidateSinceMs != null || pressStartedMs != null
        if (!clickSequenceActive) {
            contactUnknownSinceMs = null
            return false
        }

        val gapStart = contactUnknownSinceMs
        if (gapStart == null) {
            contactUnknownSinceMs = now
            return true
        }

        if (now - gapStart <= contactOcclusionGraceMs) {
            return true
        }

        cancelPressIntent(clearNeutralArm = true)
        return false
    }

    /**
     * Exclude the occluded interval from candidate/press duration so a lost
     * landmark cannot manufacture a long press or satisfy click timing.
     */
    private fun closeContactGap(now: Long) {
        val gapStart = contactUnknownSinceMs ?: return
        val gapDuration = (now - gapStart).coerceAtLeast(0L)
        candidateSinceMs = candidateSinceMs?.plus(gapDuration)
        pressStartedMs = pressStartedMs?.plus(gapDuration)
        contactUnknownSinceMs = null
    }

    private fun updateNeutralArm(
        now: Long,
        separation: Float
    ) {
        if (separation < neutralSeparation) {
            neutralSinceMs = null
            return
        }

        val start = neutralSinceMs
        if (start == null) {
            neutralSinceMs = now
            return
        }

        if (now - start >= neutralArmMs) {
            contactArmed = true
        }
    }

    private fun contactAmount(separation: Float): Float {
        if (!separation.isFinite()) return 0f
        val span =
            (contactReleaseSeparation - contactEnterSeparation)
                .coerceAtLeast(0.01f)
        return (
            (contactReleaseSeparation - separation) / span
            ).coerceIn(0f, 1f)
    }

    private fun contactConfidence(separation: Float): Float =
        (0.68f + contactAmount(separation) * 0.32f)
            .coerceIn(0f, 1f)

    private fun pointingConfidence(
        aimConfidence: Float,
        separation: Float
    ): Float {
        val contactPenalty =
            if (separation < neutralSeparation) 0.05f else 0f
        return (
            aimConfidence.coerceIn(0.62f, 0.94f) - contactPenalty
            ).coerceIn(0.55f, 0.94f)
    }

    private fun contactReasoning(
        label: String,
        durationMs: Long,
        separation: Float
    ): String =
        "$label • tip separation " +
            String.format(
                java.util.Locale.US,
                "%.2fx hand scale",
                separation
            ) +
            if (durationMs > 0L) {
                " • $durationMs ms"
            } else {
                ""
            }

    private fun comfortReach(
        value: Float,
        gain: Float
    ): Float {
        if (!value.isFinite()) return Float.NaN
        val safeGain = gain.takeIf { it.isFinite() }?.coerceIn(1f, 1.35f) ?: 1f
        return (0.5f + (value - 0.5f) * safeGain).coerceIn(0f, 1f)
    }

    private fun trackAimAdaptive(
        x: Float,
        y: Float,
        now: Long,
        confidence: Float,
        resultAgeMs: Long
    ): Pair<Float, Float> =
        motionFilter.update(
            rawX = x,
            rawY = y,
            timestampMs = now,
            confidence = confidence,
            resultAgeMs = resultAgeMs
        )

    fun updateCalibration(value: PointerCalibration) {
        calibration = value.sanitized()
    }

    fun currentCalibration(): PointerCalibration = calibration

    fun onControlHandDiscontinuity() {
        // A hand-ownership discontinuity must discard BOTH click intent and the
        // previous hand's motion history. The next control hand starts from its
        // own landmark-8 position; no old cursor state can bleed across hands.
        resetPresence(resetMotionFilter = true)
    }

    fun resetForControlHandChange() {
        resetPresence(resetMotionFilter = true)
    }

    private fun clearCandidate() {
        candidateSinceMs = null
        candidatePeakContact = 0f
    }

    private fun cancelPressIntent(
        clearNeutralArm: Boolean
    ) {
        clearCandidate()
        pressStartedMs = null
        maxPressAmount = 0f
        holdLatched = false
        contactUnknownSinceMs = null

        if (clearNeutralArm) {
            neutralSinceMs = null
            contactArmed = false
        }
    }

    private fun resetPresence(
        resetMotionFilter: Boolean
    ) {
        presenceSinceMs = null
        lastSeenMs = null
        if (resetMotionFilter) {
            motionFilter.reset()
        }
        neutralSinceMs = null
        contactArmed = false
        cancelPressIntent(clearNeutralArm = true)
    }
}
