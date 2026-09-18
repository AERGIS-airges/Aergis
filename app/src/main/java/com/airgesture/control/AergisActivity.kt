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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class AergisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ActionMappingStore(this)
        AirRuntime.update {
            it.copy(
                pointerEnabled = store.pointerEnabled(),
                controlHandPreference = store.controlHandPreference()
            )
        }

        setContent { AergisApp() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isAccessibilityEnabled()
        AirRuntime.update { it.copy(accessibilityEnabled = enabled) }
        if (!enabled && AirRuntime.state.value.sessionActive) {
            stopAergis()
        }
    }

    @Composable
    private fun AergisApp() {
        val runtime by AirRuntime.state.collectAsStateWithLifecycle()
        val store = remember { ActionMappingStore(this) }
        var selectedTab by remember { mutableStateOf(AergisTab.CONTROL) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startAergis()
            } else {
                AirRuntime.update {
                    it.copy(lastMessage = "Camera permission denied • Aergis cannot start")
                }
            }
        }

        AergisTheme {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    AergisNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            ) { padding ->
                AergisBackground(Modifier.padding(padding)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AergisHeader(runtime)
                        FeatureRail(runtime)

                        when (selectedTab) {
                            AergisTab.CONTROL -> ControlScreen(runtime, permissionLauncher)
                            AergisTab.PRACTICE -> PracticeScreen(runtime)
                            AergisTab.SETUP -> SetupScreen(runtime, store)
                            AergisTab.SYSTEM -> SystemScreen(runtime)
                        }

                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun AergisHeader(runtime: RuntimeStatus) {
        GlassPanel(accent = AermotusPalette.Cyan) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitMark(active = runtime.sessionActive)
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            text = "AERGIS",
                            color = AermotusPalette.CyanBright,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "SPATIAL CONTROL INTERFACE",
                            color = AermotusPalette.TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusChip(
                        text = if (runtime.sessionActive) "LIVE" else "READY",
                        active = runtime.sessionActive
                    )
                    Text(
                        text = "AERGIS • vc63",
                        color = AermotusPalette.Gold,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 1.0.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun FeatureRail(runtime: RuntimeStatus) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            AermotusPalette.Silver.copy(alpha = .42f),
                            AermotusPalette.Cyan.copy(alpha = .58f),
                            AermotusPalette.Gold.copy(alpha = .28f)
                        )
                    ),
                    RoundedCornerShape(5.dp)
                ),
            color = Color(0xE607141A),
            shape = RoundedCornerShape(5.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureCell("SPATIAL VISION", if (runtime.cameraReady) "ONLINE" else "STANDBY", runtime.cameraReady)
                RailDivider()
                FeatureCell("POINTER", if (runtime.pointerEnabled) "ARMED" else "OFF", runtime.pointerEnabled)
                RailDivider()
                FeatureCell("SECURE", "LOCAL", true)
                RailDivider()
                FeatureCell(
                    "STREAM",
                    if (runtime.visionResultFps > 0f) "${runtime.visionResultFps.toInt()} FPS" else "READY",
                    runtime.visionResultFps > 0f
                )
            }
        }
    }

    @Composable
    private fun FeatureCell(label: String, value: String, active: Boolean) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                color = AermotusPalette.TextMuted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = .9.sp)
            )
            Text(
                text = value,
                color = if (active) AermotusPalette.CyanBright else AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = .25.sp)
            )
        }
    }

    @Composable
    private fun RailDivider() {
        Box(
            Modifier
                .size(width = 1.dp, height = 24.dp)
                .background(Color.White.copy(alpha = .12f))
        )
    }

    @Composable
    private fun ControlScreen(
        runtime: RuntimeStatus,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        SectionEyebrow("AERGIS HUB / LIVE CONTROL")
        HeroViewport(runtime)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            InstrumentTile(
                modifier = Modifier.weight(1f),
                label = "TRACKING",
                value = trackingLabel(runtime),
                detail = runtime.trackingState,
                accent = AermotusPalette.Cyan
            )
            InstrumentTile(
                modifier = Modifier.weight(1f),
                label = "RESULT RATE",
                value = if (runtime.visionResultFps > 0f) {
                    String.format(Locale.US, "%.0f FPS", runtime.visionResultFps)
                } else {
                    "—"
                },
                detail = "LIVE STREAM",
                accent = AermotusPalette.Mint
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            InstrumentTile(
                modifier = Modifier.weight(1f),
                label = "CONTROL HAND",
                value = runtime.controlHandLabel.takeIf { it.isNotBlank() && it != "—" } ?: "WAITING",
                detail = if (runtime.controlHandLocked) "LOCKED" else "AUTO",
                accent = AermotusPalette.Gold
            )
            InstrumentTile(
                modifier = Modifier.weight(1f),
                label = "HANDS",
                value = if (runtime.detectedHandCount > 0) runtime.detectedHandCount.toString() else "—",
                detail = "DETECTED",
                accent = AermotusPalette.Cyan
            )
        }

        GlassPanel(accent = if (runtime.sessionActive) AermotusPalette.Gold else AermotusPalette.Cyan) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SectionEyebrow("CURRENT LINK")
                    Text(
                        text = runtime.lastMessage,
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusChip(
                    text = if (runtime.sessionActive) "ACTIVE" else "IDLE",
                    active = runtime.sessionActive
                )
            }

            LuminousActionButton(
                text = when {
                    runtime.sessionActive -> "PAUSE AERGIS"
                    !runtime.accessibilityEnabled -> "ENABLE ACCESSIBILITY"
                    else -> "ENTER AERGIS"
                },
                modifier = Modifier.fillMaxWidth(),
                gold = runtime.sessionActive,
                onClick = {
                    handlePrimaryAction(runtime, permissionLauncher)
                }
            )
        }
    }

    @Composable
    private fun HeroViewport(runtime: RuntimeStatus) {
        GlassPanel(
            accent = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.Cyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "SPATIAL CONTROL CORE",
                        color = AermotusPalette.Cyan,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = if (runtime.sessionActive) "ACTIVE FIELD" else "READY FIELD",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = if (runtime.pointerEnabled) "POINTER // ON" else "POINTER // OFF",
                    color = if (runtime.pointerEnabled) AermotusPalette.Gold else AermotusPalette.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            MetallicViewport(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(258.dp),
                accent = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.Cyan
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w * .5f, h * .49f)
                    val radius = size.minDimension * .22f

                    repeat(8) { i ->
                        val y = h * .12f + i * h * .105f
                        drawLine(
                            color = AermotusPalette.Cyan.copy(alpha = .05f),
                            start = Offset(w * .06f, y),
                            end = Offset(w * .94f, y),
                            strokeWidth = 1f
                        )
                    }
                    repeat(9) { i ->
                        val x = w * .08f + i * w * .105f
                        drawLine(
                            color = AermotusPalette.Cyan.copy(alpha = .04f),
                            start = Offset(x, h * .08f),
                            end = Offset(x, h * .92f),
                            strokeWidth = 1f
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                AermotusPalette.CyanBright.copy(alpha = .33f),
                                AermotusPalette.Cyan.copy(alpha = .11f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 2.1f
                        ),
                        radius = radius * 2.1f,
                        center = center
                    )
                    drawCircle(
                        color = AermotusPalette.Cyan.copy(alpha = .64f),
                        radius = radius * 1.36f,
                        center = center,
                        style = Stroke(width = 2.0f)
                    )
                    drawCircle(
                        color = AermotusPalette.Gold.copy(alpha = .72f),
                        radius = radius * .96f,
                        center = center,
                        style = Stroke(width = 1.6f)
                    )
                    drawOval(
                        color = AermotusPalette.Gold.copy(alpha = .78f),
                        topLeft = Offset(center.x - radius * 1.65f, center.y - radius * .46f),
                        size = Size(radius * 3.3f, radius * .92f),
                        style = Stroke(width = 1.8f)
                    )

                    val s = radius * .78f
                    val a = Offset(center.x - s, center.y - s * .36f)
                    val b = Offset(center.x, center.y - s)
                    val c = Offset(center.x + s, center.y - s * .36f)
                    val d = Offset(center.x, center.y + s * .22f)
                    val drop = s * .72f
                    val a2 = Offset(a.x, a.y + drop)
                    val b2 = Offset(b.x, b.y + drop)
                    val c2 = Offset(c.x, c.y + drop)
                    val d2 = Offset(d.x, d.y + drop)
                    val cube = AermotusPalette.CyanBright.copy(alpha = .68f)
                    listOf(
                        a to b, b to c, c to d, d to a,
                        a2 to b2, b2 to c2, c2 to d2, d2 to a2,
                        a to a2, b to b2, c to c2, d to d2
                    ).forEach { (p1, p2) ->
                        drawLine(cube, p1, p2, strokeWidth = 1.7f)
                    }

                    drawCircle(
                        color = AermotusPalette.CyanBright,
                        radius = 4f,
                        center = Offset(center.x + radius * 1.47f, center.y - radius * .13f)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = if (runtime.sessionActive) "LIVE" else "AERGIS",
                        color = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.CyanBright,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (runtime.sessionActive) "CONTROL ONLINE" else "SPATIAL INPUT READY",
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    if (runtime.visionResultFps > 0f) {
                        Text(
                            text = String.format(Locale.US, "%.0f FPS", runtime.visionResultFps),
                            color = AermotusPalette.Gold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Text(
                text = if (runtime.sessionActive) {
                    "Index-tip spatial control is active. Tracking remains local and pointer ownership stays isolated from generic gesture actions."
                } else {
                    "Aergis turns the front camera into an on-device spatial control surface."
                },
                modifier = Modifier.fillMaxWidth(),
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun InstrumentTile(
        label: String,
        value: String,
        detail: String,
        modifier: Modifier = Modifier,
        accent: Color = AermotusPalette.Cyan
    ) {
        Surface(
            modifier = modifier
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            AermotusPalette.Silver.copy(alpha = .35f),
                            accent.copy(alpha = .68f),
                            AermotusPalette.Gold.copy(alpha = .16f)
                        )
                    ),
                    RoundedCornerShape(5.dp)
                ),
            color = Color(0xE807171E),
            shape = RoundedCornerShape(5.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = .08f), Color.Transparent)
                        )
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = AermotusPalette.TextMuted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                    )
                    Box(Modifier.size(5.dp).background(accent, CircleShape))
                }
                Text(
                    text = value,
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = detail,
                    color = AermotusPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun PracticeScreen(runtime: RuntimeStatus) {
        SectionEyebrow("SPATIAL ONBOARDING")
        SectionTitle(
            title = "Gesture Lab",
            subtitle = "Deliberate motion only. A relaxed hand should mean nothing."
        )

        GlassPanel(accent = AermotusPalette.Cyan) {
            MetallicViewport(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                accent = AermotusPalette.Cyan
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawLine(AermotusPalette.Cyan.copy(alpha = .22f), Offset(w * .5f, h * .12f), Offset(w * .5f, h * .88f), 1f)
                    drawLine(AermotusPalette.Cyan.copy(alpha = .22f), Offset(w * .12f, h * .5f), Offset(w * .88f, h * .5f), 1f)
                    drawCircle(AermotusPalette.Cyan.copy(alpha = .18f), h * .28f, Offset(w * .5f, h * .5f), style = Stroke(2f))
                    drawCircle(AermotusPalette.Gold.copy(alpha = .72f), 5f, Offset(w * .72f, h * .31f))
                }
                Text(
                    text = "MOVE • CROSS • COMMIT",
                    modifier = Modifier.align(Alignment.Center),
                    color = AermotusPalette.CyanBright,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        val drills = listOf(
            "01" to "Point and hold steady",
            "02" to "Relax unused fingers",
            "03" to "Deliberate index-middle click",
            "04" to "Hold contact and drag",
            "05" to "Reach all screen edges"
        )

        drills.forEach { (number, label) ->
            GlassPanel(accent = if (number.toInt() % 2 == 0) AermotusPalette.Gold else AermotusPalette.Cyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF07161C), RoundedCornerShape(5.dp))
                            .border(1.dp, AermotusPalette.Cyan.copy(alpha = .52f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(number, color = AermotusPalette.CyanBright, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(">", color = AermotusPalette.Gold, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        GlassPanel(accent = AermotusPalette.Mint) {
            SectionEyebrow("LIVE FEEDBACK")
            Text(
                text = if (runtime.sessionActive) runtime.lastMessage else "Start Aergis from Control to enter live recognition.",
                color = AermotusPalette.TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    @Composable
    private fun SetupScreen(runtime: RuntimeStatus, store: ActionMappingStore) {
        SectionEyebrow("SYSTEM SETTINGS")
        SectionTitle(
            title = "Control Environment",
            subtitle = "Configure the local systems Aergis requires."
        )

        GlassPanel(accent = AermotusPalette.Mint) {
            SectionEyebrow("CORE ACCESS")
            ReadinessRow("Accessibility bridge", runtime.accessibilityEnabled)
            ReadinessRow("Front camera permission", hasCameraPermission())
            OutlinedButton(
                onClick = { openAccessibilitySettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OPEN ACCESSIBILITY SETTINGS")
            }
        }

        GlassPanel(accent = AermotusPalette.Cyan) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SectionEyebrow("AIR POINTER")
                    Text(
                        text = if (runtime.pointerEnabled) "Index-tip pointer is armed." else "Index-tip pointer is off.",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Pointer position remains owned by landmark 8.",
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
        }

        GlassPanel(accent = AermotusPalette.Gold) {
            SectionEyebrow("REALITY MAPPING")
            Text(
                text = "Pointer Calibration",
                color = AermotusPalette.TextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tune reach and center precision while raw index-tip tracking and the final cursor remain visible together.",
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            LuminousActionButton(
                text = "OPEN REALITY MAPPING",
                modifier = Modifier.fillMaxWidth(),
                gold = true,
                onClick = { openPointerCalibration() }
            )
            Spacer(Modifier.height(2.dp))
            LuminousActionButton(
                text = "OPEN GESTURE MAPPINGS",
                modifier = Modifier.fillMaxWidth(),
                gold = false,
                onClick = { openGestureMappings() }
            )
        }
    }

    @Composable
    private fun SystemScreen(runtime: RuntimeStatus) {
        SectionEyebrow("MAPPING HEALTH")
        SectionTitle(
            title = "System Observatory",
            subtitle = "Live health, cadence and engineering access."
        )

        GlassPanel(accent = AermotusPalette.Mint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthDial(runtime)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SectionEyebrow("TRACKING LINK")
                    Text(
                        text = trackingLabel(runtime),
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = runtime.trackingState,
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        GlassPanel {
            ReadinessRow("Session", runtime.sessionActive)
            ReadinessRow("Camera", runtime.cameraReady)
            ReadinessRow("Recognizer", runtime.recognizerReady)
            ReadinessRow("Control hand", runtime.detectedHandCount > 0)
        }

        GlassPanel(accent = AermotusPalette.Gold) {
            SectionEyebrow("ENGINEERING")
            Text(
                text = "Technical Console",
                color = AermotusPalette.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Open diagnostics, mappings and pointer telemetry.",
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { openTechnicalConsole() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OPEN TECHNICAL CONSOLE")
            }
        }
    }

    @Composable
    private fun HealthDial(runtime: RuntimeStatus) {
        val accent = when {
            !runtime.sessionActive -> AermotusPalette.TextMuted
            runtime.cameraReady && runtime.recognizerReady && runtime.detectedHandCount > 0 -> AermotusPalette.Mint
            else -> AermotusPalette.Warning
        }

        Box(
            modifier = Modifier.size(108.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .40f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color.White.copy(alpha = .06f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 8f)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = if (runtime.sessionActive) 285f else 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 8f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (runtime.visionResultFps > 0f) String.format(Locale.US, "%.0f", runtime.visionResultFps) else "—",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "RESULT FPS",
                    color = AermotusPalette.TextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                )
            }
        }
    }

    @Composable
    private fun ReadinessRow(label: String, ready: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted, CircleShape)
                )
                Text(label, color = AermotusPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (ready) "READY" else "OFF",
                color = if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    @Composable
    private fun AergisNav(
        selectedTab: AergisTab,
        onTabSelected: (AergisTab) -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            AermotusPalette.Silver.copy(alpha = .24f),
                            AermotusPalette.Cyan.copy(alpha = .46f),
                            AermotusPalette.Gold.copy(alpha = .24f)
                        )
                    ),
                    RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
                ),
            color = Color(0xFA031016),
            shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AergisTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) AermotusPalette.Cyan.copy(alpha = .13f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) AermotusPalette.Cyan.copy(alpha = .36f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = tab.symbol,
                            color = if (selected) AermotusPalette.CyanBright else AermotusPalette.TextMuted,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = tab.label.uppercase(),
                            color = if (selected) AermotusPalette.Cyan else AermotusPalette.TextMuted,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = .7.sp)
                        )
                    }
                }
            }
        }
    }

    private fun trackingLabel(runtime: RuntimeStatus): String =
        when {
            !runtime.sessionActive -> "IDLE"
            !runtime.cameraReady || !runtime.recognizerReady -> "STARTING"
            runtime.detectedHandCount <= 0 -> "SEARCHING"
            runtime.trackingState in setOf("COAST", "INVALID_FRAME", "PRESENCE") -> "REACQUIRE"
            else -> "STABLE"
        }

    private fun handlePrimaryAction(
        runtime: RuntimeStatus,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        when {
            runtime.sessionActive -> stopAergis()
            !runtime.accessibilityEnabled -> openAccessibilitySettings()
            hasCameraPermission() -> startAergis()
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

    private fun startAergis() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, GestureCaptureService::class.java)
                    .setAction(GestureCaptureService.ACTION_START)
            )
        }.onFailure { error ->
            AirRuntime.update {
                it.copy(
                    sessionActive = false,
                    lastMessage = "Aergis could not start: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    private fun stopAergis() {
        val intent = Intent(this, GestureCaptureService::class.java)
            .setAction(GestureCaptureService.ACTION_STOP)
        runCatching {
            if (AirRuntime.state.value.sessionActive) startService(intent) else stopService(intent)
        }.onFailure { error ->
            AirRuntime.update {
                it.copy(
                    lastMessage = "Aergis stop request failed: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openPointerCalibration() {
        startActivity(Intent(this, PointerCalibrationActivity::class.java))
    }

    private fun openGestureMappings() {
        startActivity(Intent(this, GestureMappingActivity::class.java))
    }

    private fun openTechnicalConsole() {
        startActivity(Intent(this, MainActivity::class.java))
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
