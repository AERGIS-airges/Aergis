package com.airgesture.control

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class PointerCalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalibrationScreen() }
    }

    @Composable
    private fun CalibrationScreen() {
        val runtime by AirRuntime.state.collectAsStateWithLifecycle()
        val store = remember { ActionMappingStore(this) }
        val context =
            PointerCalibrationContext.forOrientation(
                handPreference = runtime.controlHandPreference,
                landscape =
                    resources.configuration.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
            )
        val original = remember(context) {
            store.pointerCalibration(context)
        }
        var draft by remember(context) {
            mutableStateOf(original)
        }

        fun updateDraft(value: PointerCalibration) {
            val safe = value.sanitized()
            draft = safe
            GestureCaptureService.updatePointerCalibration(safe)
        }

        fun cancelAndFinish() {
            GestureCaptureService.updatePointerCalibration(original)
            finish()
        }

        fun applyAndFinish() {
            val safe = draft.sanitized()
            store.setPointerCalibration(safe, context)
            GestureCaptureService.updatePointerCalibration(safe)
            finish()
        }

        BackHandler { cancelAndFinish() }

        AermotusTheme {
            AermotusBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MappingHeader(context, runtime)
                    LivePointerPreview(runtime)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassPanel(accent = AermotusPalette.Cyan) {
                            SectionEyebrow("Reach Geometry")
                            Text(
                                "Map comfortable fingertip travel to the full display without forcing the hand toward the camera edges.",
                                color = AermotusPalette.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )

                            CalibrationSlider(
                                label = "Left boundary",
                                value = draft.minX,
                                valueRange =
                                    PointerCalibration.MIN_BOUND..
                                        (draft.maxX - PointerCalibration.MIN_SPAN)
                                            .coerceAtLeast(PointerCalibration.MIN_BOUND),
                                onValueChange = {
                                    updateDraft(draft.copy(minX = it))
                                }
                            )
                            CalibrationSlider(
                                label = "Right boundary",
                                value = draft.maxX,
                                valueRange =
                                    (draft.minX + PointerCalibration.MIN_SPAN)
                                        .coerceAtMost(PointerCalibration.MAX_BOUND)..
                                        PointerCalibration.MAX_BOUND,
                                onValueChange = {
                                    updateDraft(draft.copy(maxX = it))
                                }
                            )
                            CalibrationSlider(
                                label = "Top boundary",
                                value = draft.minY,
                                valueRange =
                                    PointerCalibration.MIN_BOUND..
                                        (draft.maxY - PointerCalibration.MIN_SPAN)
                                            .coerceAtLeast(PointerCalibration.MIN_BOUND),
                                onValueChange = {
                                    updateDraft(draft.copy(minY = it))
                                }
                            )
                            CalibrationSlider(
                                label = "Bottom boundary",
                                value = draft.maxY,
                                valueRange =
                                    (draft.minY + PointerCalibration.MIN_SPAN)
                                        .coerceAtMost(PointerCalibration.MAX_BOUND)..
                                        PointerCalibration.MAX_BOUND,
                                onValueChange = {
                                    updateDraft(draft.copy(maxY = it))
                                }
                            )
                        }

                        GlassPanel(accent = AermotusPalette.Gold) {
                            SectionEyebrow("Precision Field")
                            Text(
                                "Shape center response while keeping the calibrated display edges reachable.",
                                color = AermotusPalette.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            CalibrationSlider(
                                label = "Horizontal precision",
                                value = draft.curveX,
                                valueRange =
                                    PointerCalibration.MIN_CURVE..
                                        PointerCalibration.MAX_CURVE,
                                onValueChange = {
                                    updateDraft(draft.copy(curveX = it))
                                }
                            )
                            CalibrationSlider(
                                label = "Vertical precision",
                                value = draft.curveY,
                                valueRange =
                                    PointerCalibration.MIN_CURVE..
                                        PointerCalibration.MAX_CURVE,
                                onValueChange = {
                                    updateDraft(draft.copy(curveY = it))
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                updateDraft(PointerCalibration.DEFAULT)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("RESET DRAFT TO DEFAULT")
                        }
                        Spacer(Modifier.height(3.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cancelAndFinish() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL")
                        }
                        Button(
                            onClick = { applyAndFinish() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AermotusPalette.Cyan,
                                contentColor = AermotusPalette.Void
                            )
                        ) {
                            Text(
                                "APPLY MAP",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MappingHeader(
        context: PointerCalibrationContext,
        runtime: RuntimeStatus
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitMark(active = runtime.pointerVisible)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionEyebrow("Reality Mapping")
                    Text(
                        "POINTER SPACE",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(
                            letterSpacing = 1.1.sp
                        ),
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        context.handPreference.label + " • " +
                            context.orientation.name
                                .lowercase()
                                .replaceFirstChar { it.uppercase() },
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            StatusChip(
                text = if (runtime.pointerVisible) "TRACKING" else "WAITING",
                active = runtime.pointerVisible
            )
        }
    }

    @Composable
    private fun LivePointerPreview(runtime: RuntimeStatus) {
        GlassPanel(accent = AermotusPalette.Cyan) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionEyebrow("Spatial Overlay")
                    Text(
                        "LIVE POINTER MAP",
                        color = AermotusPalette.TextPrimary,
                        fontWeight = FontWeight.Black
                    )
                }
                if (runtime.visionResultFps > 0f) {
                    Text(
                        String.format(Locale.US, "%.0f FPS", runtime.visionResultFps),
                        color = AermotusPalette.Gold,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF08171D),
                                Color(0xFF040B10)
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .border(
                        1.dp,
                        AermotusPalette.Cyan.copy(alpha = .24f),
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val grid = AermotusPalette.Cyan.copy(alpha = .08f)

                    for (i in 1..5) {
                        val x = w * i / 6f
                        drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
                    }
                    for (i in 1..3) {
                        val y = h * i / 4f
                        drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
                    }
                    drawLine(
                        AermotusPalette.Gold.copy(alpha = .18f),
                        Offset(w * .5f, 0f),
                        Offset(w * .5f, h),
                        2f
                    )
                    drawLine(
                        AermotusPalette.Gold.copy(alpha = .18f),
                        Offset(0f, h * .5f),
                        Offset(w, h * .5f),
                        2f
                    )

                    if (runtime.pointerRawPresent) {
                        val raw = Offset(
                            runtime.pointerRawX.coerceIn(0f, 1f) * w,
                            runtime.pointerRawY.coerceIn(0f, 1f) * h
                        )
                        drawCircle(
                            color = AermotusPalette.Gold,
                            radius = 9f,
                            center = raw,
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = AermotusPalette.Gold.copy(alpha = .12f),
                            radius = 24f,
                            center = raw
                        )
                    }

                    if (runtime.pointerVisible) {
                        val cursor = Offset(
                            runtime.pointerX.coerceIn(0f, 1f) * w,
                            runtime.pointerY.coerceIn(0f, 1f) * h
                        )
                        drawCircle(
                            color = AermotusPalette.Cyan.copy(alpha = .10f),
                            radius = 29f,
                            center = cursor
                        )
                        drawCircle(
                            color = AermotusPalette.CyanBright,
                            radius = 13f,
                            center = cursor,
                            style = Stroke(width = 5f)
                        )
                        drawCircle(
                            color = AermotusPalette.Cyan,
                            radius = 3f,
                            center = cursor
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendDot(
                    label = "RAW #8",
                    color = AermotusPalette.Gold
                )
                LegendDot(
                    label = "FINAL CURSOR",
                    color = AermotusPalette.Cyan
                )
            }

            Text(
                if (runtime.pointerRawPresent) {
                    String.format(
                        Locale.US,
                        "Raw %.3f, %.3f  •  cursor %.3f, %.3f",
                        runtime.pointerRawX,
                        runtime.pointerRawY,
                        runtime.pointerX,
                        runtime.pointerY
                    )
                } else {
                    "Start AERMOTUS and show the index fingertip to begin live mapping."
                },
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    @Composable
    private fun LegendDot(
        label: String,
        color: Color
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Text(
                label,
                color = AermotusPalette.TextMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = .7.sp
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun CalibrationSlider(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = AermotusPalette.TextPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
                SurfaceValue(value)
            }
            Slider(
                value = value.coerceIn(
                    valueRange.start,
                    valueRange.endInclusive
                ),
                onValueChange = onValueChange,
                valueRange = valueRange
            )
        }
    }

    @Composable
    private fun SurfaceValue(value: Float) {
        Box(
            modifier = Modifier
                .background(
                    AermotusPalette.Cyan.copy(alpha = .10f),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    AermotusPalette.Cyan.copy(alpha = .18f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                String.format(Locale.US, "%.2f", value),
                color = AermotusPalette.CyanBright,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}
