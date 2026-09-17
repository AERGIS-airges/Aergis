package com.airgesture.control

import android.content.Context

class ActionMappingStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "air_gesture_mappings",
        Context.MODE_PRIVATE
    )

    fun actionFor(signal: GestureSignal): AirAction {
        val fallback = DefaultMappings.values[signal] ?: AirAction.NONE
        val stored = prefs.getString(signal.name, fallback.name)
        return runCatching {
            AirAction.valueOf(stored ?: fallback.name)
        }.getOrDefault(fallback)
    }

    fun setAction(signal: GestureSignal, action: AirAction) {
        prefs.edit().putString(signal.name, action.name).apply()
    }

    fun controlHandPreference(): ControlHandPreference {
        val stored = prefs.getString(
            KEY_CONTROL_HAND,
            ControlHandPreference.AUTO.name
        )
        return runCatching {
            ControlHandPreference.valueOf(
                stored ?: ControlHandPreference.AUTO.name
            )
        }.getOrDefault(ControlHandPreference.AUTO)
    }

    fun setControlHandPreference(
        preference: ControlHandPreference
    ) {
        prefs.edit()
            .putString(KEY_CONTROL_HAND, preference.name)
            .apply()
    }

    fun pointerEnabled(): Boolean =
        prefs.getBoolean(
            KEY_POINTER_ENABLED,
            DEFAULT_POINTER_ENABLED
        )

    fun setPointerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POINTER_ENABLED, enabled).apply()
    }

    fun pointerCalibration(
        context: PointerCalibrationContext
    ): PointerCalibration {
        val prefix =
            "pointer_calibration_" +
                context.storageKey
        val profileVersion =
            prefs.getInt(
                prefix + "_version",
                0
            )

        if (
            profileVersion <
                POINTER_CALIBRATION_PROFILE_VERSION
        ) {
            // Profile v2 is the first calibration schema tied exclusively to
            // direct MediaPipe index-fingertip (landmark 8) steering. Older
            // calibration values may have been captured while pointer motion
            // still behaved like a mixed hand/palm anchor. Reusing those
            // ranges after the source change can magnify offsets and jitter.
            // Reset only this pointer-calibration context; gesture mappings,
            // pointer enablement and hand preference remain untouched.
            return resetPointerCalibration(context)
        }

        return PointerCalibration(
            minX = prefs.getFloat(
                prefix + "_min_x",
                PointerCalibration.DEFAULT_MIN_X
            ),
            maxX = prefs.getFloat(
                prefix + "_max_x",
                PointerCalibration.DEFAULT_MAX_X
            ),
            minY = prefs.getFloat(
                prefix + "_min_y",
                PointerCalibration.DEFAULT_MIN_Y
            ),
            maxY = prefs.getFloat(
                prefix + "_max_y",
                PointerCalibration.DEFAULT_MAX_Y
            ),
            curveX = prefs.getFloat(
                prefix + "_curve_x",
                PointerCalibration.DEFAULT_CURVE_X
            ),
            curveY = prefs.getFloat(
                prefix + "_curve_y",
                PointerCalibration.DEFAULT_CURVE_Y
            )
        ).sanitized()
    }

    fun setPointerCalibration(
        value: PointerCalibration,
        context: PointerCalibrationContext
    ) {
        val safe = value.sanitized()
        val prefix =
            "pointer_calibration_" +
                context.storageKey
        prefs.edit()
            .putFloat(
                prefix + "_min_x",
                safe.minX
            )
            .putFloat(
                prefix + "_max_x",
                safe.maxX
            )
            .putFloat(
                prefix + "_min_y",
                safe.minY
            )
            .putFloat(
                prefix + "_max_y",
                safe.maxY
            )
            .putFloat(
                prefix + "_curve_x",
                safe.curveX
            )
            .putFloat(
                prefix + "_curve_y",
                safe.curveY
            )
            .putInt(
                prefix + "_version",
                POINTER_CALIBRATION_PROFILE_VERSION
            )
            .apply()
    }

    fun resetPointerCalibration(
        context: PointerCalibrationContext
    ): PointerCalibration {
        val calibration =
            PointerCalibration.DEFAULT
        setPointerCalibration(
            calibration,
            context
        )
        return calibration
    }

    fun all(): Map<GestureSignal, AirAction> =
        GestureSignal.entries.associateWith(::actionFor)

    fun resetGestureMappings(): Map<GestureSignal, AirAction> {
        val editor = prefs.edit()
        GestureSignal.entries.forEach { signal ->
            editor.remove(signal.name)
        }
        editor.apply()
        return all()
    }

    companion object {
        const val DEFAULT_POINTER_ENABLED = false

        private const val KEY_POINTER_ENABLED = "pointer_enabled"
        private const val KEY_CONTROL_HAND = "control_hand_preference"

        // v2 deliberately invalidates pre-landmark-8 calibration profiles.
        private const val POINTER_CALIBRATION_PROFILE_VERSION = 2
    }
}
