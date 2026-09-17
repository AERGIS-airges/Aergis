package com.airgesture.control

class PointerCalibrationSession(
    original: PointerCalibration
) {
    private val baseline =
        original.sanitized()

    var draft: PointerCalibration =
        baseline
        private set

    fun update(
        value: PointerCalibration
    ): PointerCalibration {
        draft = value.sanitized()
        return draft
    }

    fun resetDraft(): PointerCalibration {
        draft = PointerCalibration.DEFAULT
        return draft
    }

    fun cancel(): PointerCalibration {
        draft = baseline
        return baseline
    }

    fun apply(): PointerCalibration =
        draft.sanitized()

    fun original(): PointerCalibration =
        baseline
}
