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

class ProductionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ActionMappingStore(this)
        AirRuntime.update {
            it.copy(
                pointerEnabled = store.pointerEnabled(),
                controlHandPreference = store.controlHandPreference()
            )
        }

        setContent { ProductionApp() }
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
    private fun ProductionApp() {
        val runtime by AirRuntime.state.collectAsStateWithLifecycle()
        val store = remember { ActionMappingStore(this) }
        var selectedTab by remember { mutableStateOf(ProductionTab.CONTROL) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startAirControl()
            } else {
                AirRuntime.update {
                    it.copy(lastMessage = "Camera permission denied • Air Control cannot start")
                }
            }
        }

        AermotusTheme {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    SpatialNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            ) { padding ->
                AermotusBackground(Modifier.padding(padding)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        BrandHeader(runtime)
                        SystemRibbon(runtime)

                        when (selectedTab) {
                            ProductionTab.CONTROL -> ControlTab(
                                runtime = runtime,
                                onPrimaryAction = {
                                    handlePrimaryAction(runtime, permissionLauncher)
                                }
                            )

                            ProductionTab.PRACTICE -> PracticeTab(runtime)
                            ProductionTab.SETUP -> SetupTab(runtime, store)
                            ProductionTab.DIAGNOSTICS -> DiagnosticsTab(runtime)
                        }

                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun BrandHeader(runtime: RuntimeStatus) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                    OrbitMark(active = runtime.sessionActive)
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            "AERMOTUS",
                            color = AermotusPalette.CyanBright,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "CONTROL THE AIR. SHAPE THE INTERFACE.",
                            color = AermotusPalette.TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontSize = 8.sp
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusChip(
                    text = if (runtime.sessionActive) "LIVE" else "READY",
                    active = runtime.sessionActive
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AermotusPalette.Cyan.copy(alpha = .62f),
                                AermotusPalette.Silver.copy(alpha = .36f),
                                AermotusPalette.Gold.copy(alpha = .34f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    @Composable
    private fun SystemRibbon(runtime: RuntimeStatus) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            AermotusPalette.Silver.copy(alpha = .25f),
                            AermotusPalette.Cyan.copy(alpha = .36f),
                            AermotusPalette.Gold.copy(alpha = .20f)
                        )
                    ),
                    RoundedCornerShape(12.dp)
                ),
            color = Color(0xDA06141C),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RibbonItem("VISION", if (runtime.cameraReady) "ONLINE" else "STANDBY", runtime.cameraReady)
                RibbonDivider()
                RibbonItem("POINTER", if (runtime.pointerEnabled) "ARMED" else "OFF", runtime.pointerEnabled)
                RibbonDivider()
                RibbonItem("SECURE", "LOCAL", true)
                RibbonDivider()
                RibbonItem(
                    "STREAM",
                    if (runtime.visionResultFps > 0f) "${runtime.visionResultFps.toInt()} FPS" else "READY",
                    runtime.visionResultFps > 0f
                )
            }
        }
    }

    @Composable
    private fun RibbonItem(label: String, value: String, active: Boolean) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                label,
                color = AermotusPalette.TextMuted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = .9.sp),
                fontWeight = FontWeight.Bold
            )
            Text(
                value,
                color = if (active) AermotusPalette.CyanBright else AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = .3.sp),
                fontWeight = FontWeight.Black
            )
        }
    }

    @Composable
    private fun RibbonDivider() {
        Box(
            Modifier
                .size(width = 1.dp, height = 25.dp)
                .background(Color.White.copy(alpha = .10f))
        )
    }

    @Composable
    private fun ControlTab(runtime: RuntimeStatus, onPrimaryAction: () -> Unit) {
        SectionEyebrow("04 / AERMOTUS HUB")
        HeroControlPanel(runtime, onPrimaryAction)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "TRACKING",
                value = trackingLabel(runtime),
                detail = runtime.trackingState,
                modifier = Modifier.weight(1f),
                accent = AermotusPalette.Cyan
            )
            MetricCard(
                title = "RESULT RATE",
                value = if (runtime.visionResultFps > 0f) {
                    String.format(Locale.US, "%.0f FPS", runtime.visionResultFps)
                } else {
                    "—"
                },
                detail = "LIVE STREAM",
                modifier = Modifier.weight(1f),
                accent = AermotusPalette.Mint
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "CONTROL HAND",
                value = runtime.controlHandLabel.takeIf { it.isNotBlank() && it != "—" } ?: "WAITING",
                detail = if (runtime.controlHandLocked) "LOCKED" else "AUTO",
                modifier = Modifier.weight(1f),
                accent = AermotusPalette.Gold
            )
            MetricCard(
                title = "HANDS",
                value = if (runtime.detectedHandCount > 0) runtime.detectedHandCount.toString() else "—",
                detail = "DETECTED",
                modifier = Modifier.weight(1f),
                accent = AermotusPalette.Cyan
            )
        }

        GlassPanel(accent = AermotusPalette.Gold) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SectionEyebrow("CURRENT LINK")
                    Text(
                        runtime.lastMessage,
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusChip(
                    text = if (runtime.sessionActive) "ACTIVE" else "IDLE",
                    active = runtime.sessionActive
                )
            }

            val last = runtime.lastGesture?.let { gesture ->
                gesture.label + "  →  " + (runtime.lastAction?.label ?: "—")
            } ?: "No committed action yet"

            Text(
                "LAST ACTION  //  $last",
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    @Composable
    private fun HeroControlPanel(runtime: RuntimeStatus, onPrimaryAction: () -> Unit) {
        GlassPanel(
            accent = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.Cyan
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "SPATIAL CONTROL CORE",
                        color = AermotusPalette.Cyan,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (runtime.sessionActive) "ACTIVE LAYER" else "READY FOR INPUT",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    if (runtime.pointerEnabled) "POINTER // ON" else "POINTER // OFF",
                    color = if (runtime.pointerEnabled) AermotusPalette.Gold else AermotusPalette.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0A2732),
                                Color(0xFF061820),
                                Color(0xFF020A0E)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                AermotusPalette.Silver.copy(alpha = .38f),
                                AermotusPalette.Cyan.copy(alpha = .44f),
                                AermotusPalette.Gold.copy(alpha = .18f)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w * .5f, h * .47f)
                    val radius = size.minDimension * .22f

                    repeat(7) { i ->
                        val y = h * .16f + i * h * .11f
                        drawLine(
                            color = AermotusPalette.Cyan.copy(alpha = .045f),
                            start = Offset(w * .08f, y),
                            end = Offset(w * .92f, y),
                            strokeWidth = 1f
                        )
                    }
                    repeat(7) { i ->
                        val x = w * .12f + i * w * .125f
                        drawLine(
                            color = AermotusPalette.Cyan.copy(alpha = .035f),
                            start = Offset(x, h * .10f),
                            end = Offset(x, h * .90f),
                            strokeWidth = 1f
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                AermotusPalette.CyanBright.copy(alpha = .28f),
                                AermotusPalette.Cyan.copy(alpha = .12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 2.0f
                        ),
                        radius = radius * 2.0f,
                        center = center
                    )
                    drawCircle(
                        color = AermotusPalette.Cyan.copy(alpha = .55f),
                        radius = radius * 1.32f,
                        center = center,
                        style = Stroke(width = 2.2f)
                    )
                    drawCircle(
                        color = AermotusPalette.Gold.copy(alpha = .66f),
                        radius = radius * .92f,
                        center = center,
                        style = Stroke(width = 1.7f)
                    )
                    drawCircle(
                        color = if (runtime.sessionActive) {
                            AermotusPalette.Mint.copy(alpha = .24f)
                        } else {
                            AermotusPalette.Cyan.copy(alpha = .20f)
                        },
                        radius = radius * .58f,
                        center = center
                    )

                    // Wireframe spatial cube, matching the reference's layered holographic geometry.
                    val s = radius * .76f
                    val a = Offset(center.x - s, center.y - s * .42f)
                    val b = Offset(center.x, center.y - s)
                    val c = Offset(center.x + s, center.y - s * .42f)
                    val d = Offset(center.x, center.y + s * .18f)
                    val a2 = Offset(a.x, a.y + s * .78f)
                    val b2 = Offset(b.x, b.y + s * .78f)
                    val c2 = Offset(c.x, c.y + s * .78f)
                    val d2 = Offset(d.x, d.y + s * .78f)
                    val cubeColor = AermotusPalette.CyanBright.copy(alpha = .58f)
                    listOf(
                        a to b, b to c, c to d, d to a,
                        a2 to b2, b2 to c2, c2 to d2, d2 to a2,
                        a to a2, b to b2, c to c2, d to d2
                    ).forEach { (p1, p2) ->
                        drawLine(cubeColor, p1, p2, strokeWidth = 1.8f)
                    }

                    drawOval(
                        color = AermotusPalette.Gold.copy(alpha = .64f),
                        topLeft = Offset(center.x - radius * 1.6f, center.y - radius * .48f),
                        size = Size(radius * 3.2f, radius * .96f),
                        style = Stroke(width = 1.8f)
                    )
                    drawCircle(
                        color = AermotusPalette.CyanBright,
                        radius = 4.5f,
                        center = Offset(center.x + radius * 1.45f, center.y - radius * .16f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        if (runtime.sessionActive) "LIVE" else "AIR",
                        color = if (runtime.sessionActive) AermotusPalette.Mint else AermotusPalette.CyanBright,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (runtime.sessionActive) "CONTROL ONLINE" else "READY CONTROL",
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        fontWeight = FontWeight.Bold
                    )
                    if (runtime.sessionActive && runtime.visionResultFps > 0f) {
                        Text(
                            String.format(Locale.US, "%.0f FPS", runtime.visionResultFps),
                            color = AermotusPalette.Gold,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Text(
                when {
                    runtime.sessionActive -> "On-device spatial vision is active. Your index fingertip owns pointer position while deliberate actions remain isolated."
                    !runtime.accessibilityEnabled -> "Enable the accessibility bridge once, then return here to enter spatial control."
                    else -> "Start a local touchless-control session. Camera access exists only while AERMOTUS is active."
                },
                modifier = Modifier.fillMaxWidth(),
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            LuminousActionButton(
                text = when {
                    runtime.sessionActive -> "PAUSE AERMOTUS"
                    !runtime.accessibilityEnabled -> "ENABLE ACCESSIBILITY"
                    else -> "ENTER AERMOTUS"
                },
                modifier = Modifier.fillMaxWidth(),
                gold = runtime.sessionActive,
                onClick = onPrimaryAction
            )
        }
    }

    @Composable
    private fun PracticeTab(runtime: RuntimeStatus) {
        SectionEyebrow("02 / SPATIAL ONBOARDING")
        SectionTitle(
            "Gesture Layers",
            "Practice deliberate movement patterns inside a controlled spatial layer."
        )

        GlassPanel(accent = AermotusPalette.Cyan) {
            SectionEyebrow("CONTEXTUAL VIRTUAL INPUT")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .background(Color(0xFF06161E), RoundedCornerShape(12.dp))
                    .border(1.dp, AermotusPalette.Cyan.copy(alpha = .26f), RoundedCornerShape(12.dp))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawLine(AermotusPalette.Cyan.copy(alpha = .16f), Offset(w * .5f, h * .12f), Offset(w * .5f, h * .88f), 1f)
                    drawLine(AermotusPalette.Cyan.copy(alpha = .16f), Offset(w * .12f, h * .5f), Offset(w * .88f, h * .5f), 1f)
                    drawCircle(AermotusPalette.Cyan.copy(alpha = .14f), h * .28f, Offset(w * .5f, h * .5f), style = Stroke(2f))
                    drawCircle(AermotusPalette.Gold.copy(alpha = .56f), 5f, Offset(w * .72f, h * .32f))
                }
                Text(
                    "MOVE • CROSS • COMMIT",
                    modifier = Modifier.align(Alignment.Center),
                    color = AermotusPalette.CyanBright,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                "Keep the hand relaxed. Every exercise should feel obvious, repeatable and intentional.",
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val gestures = listOf(
            "Swipe left" to "One clear whole-hand movement to the left.",
            "Swipe right" to "One clear whole-hand movement to the right.",
            "Swipe up" to "One clear upward movement through the control zone.",
            "Swipe down" to "One clear downward movement through the control zone.",
            "Fine scroll" to "Thumb + index + middle extended; ring + pinky folded, then aim the two fingers."
        )

        gestures.forEachIndexed { index, item ->
            PracticeRow(index + 1, item.first, item.second)
        }

        GlassPanel(accent = AermotusPalette.Mint) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SectionEyebrow("LIVE FEEDBACK")
                    Text(
                        if (runtime.sessionActive) runtime.lastMessage else "Start AERMOTUS from Control for live recognition feedback.",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusChip(
                    text = if (runtime.sessionActive) "LIVE" else "OFFLINE",
                    active = runtime.sessionActive
                )
            }
        }
    }

    @Composable
    private fun PracticeRow(number: Int, title: String, detail: String) {
        GlassPanel(accent = if (number % 2 == 0) AermotusPalette.Gold else AermotusPalette.Cyan) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    AermotusPalette.Cyan.copy(alpha = .28f),
                                    Color(0xFF07141B)
                                )
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (number % 2 == 0) AermotusPalette.Gold.copy(alpha = .50f) else AermotusPalette.Cyan.copy(alpha = .55f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number.toString().padStart(2, '0'),
                        color = AermotusPalette.CyanBright,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        detail,
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "›",
                    color = AermotusPalette.Gold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    @Composable
    private fun SetupTab(runtime: RuntimeStatus, store: ActionMappingStore) {
        SectionEyebrow("09 / SYSTEM SETTINGS")
        SectionTitle(
            "Control Environment",
            "Configure the secure local systems that AERMOTUS needs."
        )

        GlassPanel(accent = AermotusPalette.Mint) {
            SectionEyebrow("CORE ACCESS")
            SetupStatusRow("Accessibility bridge", runtime.accessibilityEnabled)
            SetupStatusRow("Front camera permission", hasCameraPermission())
            OutlinedButton(
                onClick = { openAccessibilitySettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OPEN ACCESSIBILITY SETTINGS")
            }
        }

        GlassPanel(accent = AermotusPalette.Cyan) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SectionEyebrow("AIR POINTER")
                    Text(
                        if (runtime.pointerEnabled) "Index-tip spatial pointer is armed." else "Index-tip spatial pointer is currently off.",
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Pointer ownership remains isolated from generic gesture execution while enabled.",
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
            SectionEyebrow("10 / REALITY MAPPING")
            Text(
                "Pointer Calibration",
                color = AermotusPalette.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Tune reach and center precision while raw index-tip tracking and the final cursor remain visible together.",
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            LuminousActionButton(
                text = "OPEN REALITY MAPPING",
                modifier = Modifier.fillMaxWidth(),
                gold = true,
                onClick = { openPointerCalibration() }
            )
        }
    }

    @Composable
    private fun DiagnosticsTab(runtime: RuntimeStatus) {
        SectionEyebrow("12 / MAPPING HEALTH")
        SectionTitle(
            "System Observatory",
            "Live health, result cadence and engineering access."
        )

        GlassPanel(accent = AermotusPalette.Mint) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthDial(runtime)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SectionEyebrow("TRACKING LINK")
                    Text(
                        trackingLabel(runtime),
                        color = AermotusPalette.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        runtime.trackingState,
                        color = AermotusPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        GlassPanel {
            SectionEyebrow("SYSTEM HEALTH")
            SetupStatusRow("Session", runtime.sessionActive)
            SetupStatusRow("Camera", runtime.cameraReady)
            SetupStatusRow("Recognizer", runtime.recognizerReady)
            SetupStatusRow("Control hand", runtime.detectedHandCount > 0)

            if (runtime.visionResultFps > 0f) {
                Text(
                    String.format(
                        Locale.US,
                        "RESULTS %.1f FPS  //  ANALYSIS→RESULT %.0f MS",
                        runtime.visionResultFps,
                        runtime.visionAnalysisToResultMs
                    ),
                    color = AermotusPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        GlassPanel(accent = AermotusPalette.Gold) {
            SectionEyebrow("ENGINEERING")
            Text(
                "Technical Console",
                color = AermotusPalette.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Open the complete diagnostics, mappings and pointer telemetry console used during development.",
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
            modifier = Modifier.size(114.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .40f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color.White.copy(alpha = .06f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 9f)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = if (runtime.sessionActive) 285f else 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 9f)
                )
                drawCircle(
                    color = AermotusPalette.Cyan.copy(alpha = .17f),
                    radius = radius * .72f,
                    center = center,
                    style = Stroke(width = 1.4f)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    if (runtime.visionResultFps > 0f) String.format(Locale.US, "%.0f", runtime.visionResultFps) else "—",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "RESULT FPS",
                    color = AermotusPalette.TextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = .7.sp)
                )
            }
        }
    }

    @Composable
    private fun MetricCard(
        title: String,
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
                            AermotusPalette.Silver.copy(alpha = .24f),
                            accent.copy(alpha = .52f),
                            AermotusPalette.Gold.copy(alpha = .12f)
                        )
                    ),
                    RoundedCornerShape(12.dp)
                ),
            color = Color(0xE8071820),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = .07f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = AermotusPalette.TextMuted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(accent, CircleShape)
                    )
                }
                Text(
                    value,
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    detail,
                    color = AermotusPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun SetupStatusRow(label: String, ready: Boolean) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted,
                            CircleShape
                        )
                )
                Text(
                    label,
                    color = AermotusPalette.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (ready) "READY" else "OFF",
                color = if (ready) AermotusPalette.Mint else AermotusPalette.TextMuted,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .8.sp)
            )
        }
    }

    @Composable
    private fun SpatialNavigationBar(
        selectedTab: ProductionTab,
        onTabSelected: (ProductionTab) -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            AermotusPalette.Silver.copy(alpha = .18f),
                            AermotusPalette.Cyan.copy(alpha = .34f),
                            AermotusPalette.Gold.copy(alpha = .18f)
                        )
                    ),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                ),
            color = Color(0xFA031016),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductionTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            AermotusPalette.Cyan.copy(alpha = .18f),
                                            AermotusPalette.Cyan.copy(alpha = .05f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) AermotusPalette.Cyan.copy(alpha = .26f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            tab.symbol,
                            color = if (selected) AermotusPalette.CyanBright else AermotusPalette.TextMuted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            tab.label.uppercase(),
                            color = if (selected) AermotusPalette.Cyan else AermotusPalette.TextMuted,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = .7.sp),
                            fontWeight = FontWeight.Bold
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
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startAirControl() {
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
                    lastMessage = "Air Control could not start: " + (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    private fun stopAirControl() {
        val intent = Intent(this, GestureCaptureService::class.java)
            .setAction(GestureCaptureService.ACTION_STOP)
        runCatching {
            if (AirRuntime.state.value.sessionActive) startService(intent) else stopService(intent)
        }.onFailure { error ->
            AirRuntime.update {
                it.copy(
                    lastMessage = "Air Control stop request failed: " + (error.message ?: error.javaClass.simpleName)
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

    private enum class ProductionTab(val label: String, val symbol: String) {
        CONTROL("Control", "◉"),
        PRACTICE("Practice", "✦"),
        SETUP("Setup", "◇"),
        DIAGNOSTICS("More", "⋯")
    }
}
