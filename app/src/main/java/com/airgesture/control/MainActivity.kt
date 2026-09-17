package com.airgesture.control

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

/**
 * R16 engineering console.
 *
 * This keeps the diagnostic/mapping capabilities needed during development,
 * but deliberately uses the same visual language as ProductionActivity. The
 * old generic Material settings page is no longer exposed as a product screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ActionMappingStore(this)
        AirRuntime.update {
            it.copy(
                pointerEnabled = store.pointerEnabled(),
                controlHandPreference = store.controlHandPreference()
            )
        }

        setContent { TechnicalConsole() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isAccessibilityEnabled()
        AirRuntime.update { it.copy(accessibilityEnabled = enabled) }
        if (!enabled && AirRuntime.state.value.sessionActive) {
            stopAirControl()
        }
    }

    @Composable
    private fun TechnicalConsole() {
        val runtime by AirRuntime.state.collectAsStateWithLifecycle()
        val store = remember { ActionMappingStore(this) }
        val mappings = remember {
            mutableStateMapOf<GestureSignal, AirAction>().apply {
                putAll(store.all())
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startAirControl()
            } else {
                AirRuntime.update {
                    it.copy(lastMessage = "Camera permission denied")
                }
            }
        }

        AermotusTheme {
            Scaffold(containerColor = Color.Transparent) { padding ->
                AermotusBackground(Modifier.padding(padding)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        ConsoleHeader(runtime)
                        ConsoleStatusStrip(runtime)
                        SessionModule(runtime, permissionLauncher)
                        PointerModule(runtime, store)
                        MappingModule(mappings, store)
                        SystemModule(runtime)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun ConsoleHeader(runtime: RuntimeStatus) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitMark(active = runtime.sessionActive)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "AERMOTUS",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "ENGINEERING OBSERVATORY",
                        color = AermotusPalette.Cyan,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            StatusChip(
                if (runtime.sessionActive) "LIVE" else "STANDBY",
                runtime.sessionActive
            )
        }
    }

    @Composable
    private fun ConsoleStatusStrip(runtime: RuntimeStatus) {
        GlassPanel(accent = AermotusPalette.Cyan) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TinyMetric("VISION", if (runtime.cameraReady) "ONLINE" else "OFF")
                TinyDivider()
                TinyMetric(
                    "RESULT",
                    if (runtime.visionResultFps > 0f) {
                        String.format(Locale.US, "%.0f FPS", runtime.visionResultFps)
                    } else "—"
                )
                TinyDivider()
                TinyMetric("POINTER", runtime.pointerState)
                TinyDivider()
                TinyMetric("HANDS", runtime.detectedHandCount.toString())
            }
        }
    }

    @Composable
    private fun SessionModule(
        runtime: RuntimeStatus,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        GlassPanel(accent = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.Cyan) {
            SectionEyebrow("Session Control")
            SectionTitle(
                if (runtime.sessionActive) "Spatial Link Active" else "Spatial Link Ready",
                runtime.lastMessage
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LuminousActionButton(
                    text = if (runtime.sessionActive) "Pause" else "Start",
                    modifier = Modifier.weight(1f),
                    gold = runtime.sessionActive,
                    onClick = {
                        handlePrimaryAction(runtime, permissionLauncher)
                    }
                )
                OutlinedButton(
                    onClick = { finish() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RETURN")
                }
            }
        }
    }

    @Composable
    private fun PointerModule(
        runtime: RuntimeStatus,
        store: ActionMappingStore
    ) {
        GlassPanel(accent = AermotusPalette.Gold) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SectionEyebrow("Pointer Core")
                    Text(
                        "INDEX LANDMARK 8",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${runtime.pointerState} • ${(runtime.pointerConfidence * 100f).toInt().coerceIn(0, 100)}% confidence",
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = runtime.pointerEnabled,
                    onCheckedChange = { enabled ->
                        store.setPointerEnabled(enabled)
                        AirRuntime.update {
                            it.copy(
                                pointerEnabled = enabled,
                                pointerVisible = if (enabled) it.pointerVisible else false,
                                pointerState = if (enabled) it.pointerState else "OFF"
                            )
                        }
                        AirAccessibilityService.setPointerEnabled(enabled)
                        GestureCaptureService.updatePointerMode()
                    }
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ControlHandPreference.entries.forEach { preference ->
                    val selected = runtime.controlHandPreference == preference
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) AermotusPalette.Cyan.copy(alpha = .14f)
                                else AermotusPalette.Glass,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) AermotusPalette.Cyan.copy(alpha = .72f)
                                else AermotusPalette.Hairline,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                store.setControlHandPreference(preference)
                                AirRuntime.update { it.copy(controlHandPreference = preference) }
                                GestureCaptureService.updateControlHandPreference(preference)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            preference.label.uppercase(),
                            color = if (selected) AermotusPalette.CyanBright else AermotusPalette.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Text(
                String.format(
                    Locale.US,
                    "CURSOR %.1f%% / %.1f%%   RAW #8 %.3f / %.3f",
                    runtime.pointerX * 100f,
                    runtime.pointerY * 100f,
                    runtime.pointerRawX,
                    runtime.pointerRawY
                ),
                color = AermotusPalette.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )

            LuminousActionButton(
                text = "Reality Mapping",
                modifier = Modifier.fillMaxWidth(),
                gold = true,
                onClick = {
                    startActivity(Intent(this, PointerCalibrationActivity::class.java))
                }
            )
        }
    }

    @Composable
    private fun MappingModule(
        mappings: MutableMap<GestureSignal, AirAction>,
        store: ActionMappingStore
    ) {
        GlassPanel(accent = AermotusPalette.Cyan) {
            SectionEyebrow("Gesture Matrix")
            SectionTitle(
                "Action Mappings",
                "Compact engineering controls. Pointer ownership remains isolated while Air Pointer is enabled."
            )

            val prioritySignals = listOf(
                GestureSignal.SWIPE_LEFT,
                GestureSignal.SWIPE_RIGHT,
                GestureSignal.SWIPE_UP,
                GestureSignal.SWIPE_DOWN,
                GestureSignal.NAV_SCROLL_UP,
                GestureSignal.NAV_SCROLL_DOWN,
                GestureSignal.NAV_SCROLL_LEFT,
                GestureSignal.NAV_SCROLL_RIGHT
            )

            prioritySignals.forEach { signal ->
                MappingSelector(
                    signal = signal,
                    selected = mappings[signal] ?: AirAction.NONE,
                    onSelect = { action ->
                        mappings[signal] = action
                        store.setAction(signal, action)
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    val restored = store.resetGestureMappings()
                    mappings.clear()
                    mappings.putAll(restored)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("RESTORE SAFE DEFAULTS")
            }
        }
    }

    @Composable
    private fun MappingSelector(
        signal: GestureSignal,
        selected: AirAction,
        onSelect: (AirAction) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                signal.label.uppercase(),
                modifier = Modifier.weight(1f),
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Box {
                Box(
                    modifier = Modifier
                        .background(AermotusPalette.Cyan.copy(alpha = .08f), RoundedCornerShape(5.dp))
                        .border(1.dp, AermotusPalette.Cyan.copy(alpha = .28f), RoundedCornerShape(5.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        selected.label.uppercase(),
                        color = AermotusPalette.CyanBright,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = AermotusPalette.DeepBlue
                ) {
                    AirAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    action.label,
                                    color = AermotusPalette.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelect(action)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SystemModule(runtime: RuntimeStatus) {
        GlassPanel(accent = AermotusPalette.Mint) {
            SectionEyebrow("System Health")
            StatusLine("Accessibility", runtime.accessibilityEnabled)
            StatusLine("Camera", runtime.cameraReady)
            StatusLine("Recognizer", runtime.recognizerReady)
            StatusLine("Control hand", runtime.detectedHandCount > 0)
            if (runtime.visionResultFps > 0f) {
                Text(
                    String.format(
                        Locale.US,
                        "RESULT %.1f FPS  •  ANALYSIS→RESULT %.0f MS",
                        runtime.visionResultFps,
                        runtime.visionAnalysisToResultMs
                    ),
                    color = AermotusPalette.Gold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedButton(
                onClick = { openAccessibilitySettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ACCESSIBILITY SETTINGS")
            }
        }
    }

    @Composable
    private fun TinyMetric(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = AermotusPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = AermotusPalette.CyanBright, style = MaterialTheme.typography.labelSmall)
        }
    }

    @Composable
    private fun TinyDivider() {
        Box(
            Modifier
                .size(width = 1.dp, height = 24.dp)
                .background(AermotusPalette.Cyan.copy(alpha = .16f))
        )
    }

    @Composable
    private fun StatusLine(label: String, ready: Boolean) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = AermotusPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted,
                            CircleShape
                        )
                )
                Text(
                    if (ready) "READY" else "OFF",
                    color = if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    private fun handlePrimaryAction(
        runtime: RuntimeStatus,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        when {
            runtime.sessionActive -> stopAirControl()
            !runtime.accessibilityEnabled -> openAccessibilitySettings()
            hasCameraPermission() -> startAirControl()
            else -> permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= 33) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            )
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startAirControl() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, GestureCaptureService::class.java)
                    .setAction(GestureCaptureService.ACTION_START)
            )
        }
    }

    private fun stopAirControl() {
        val intent = Intent(this, GestureCaptureService::class.java)
            .setAction(GestureCaptureService.ACTION_STOP)
        runCatching { startService(intent) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val serviceInfo = info.resolveInfo.serviceInfo
            serviceInfo.packageName == packageName &&
                serviceInfo.name == AirAccessibilityService::class.java.name
        }
    }
}
