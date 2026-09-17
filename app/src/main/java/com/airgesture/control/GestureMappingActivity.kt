package com.airgesture.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GestureMappingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MappingScreen() }
    }

    @Composable
    private fun MappingScreen() {
        val store = remember { ActionMappingStore(this) }
        var mappings by remember { mutableStateOf(store.all()) }
        var expanded by remember { mutableStateOf<GestureSignal?>(null) }

        fun setMapping(signal: GestureSignal, action: AirAction) {
            store.setAction(signal, action)
            mappings = store.all()
            expanded = null
        }

        BackHandler { finish() }

        AermotusTheme {
            AermotusBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    GlassPanel(accent = AermotusPalette.Gold) {
                        SectionEyebrow("AERGIS / CONTROL ROUTING")
                        Text(
                            "GESTURE MAPPINGS",
                            color = AermotusPalette.TextPrimary,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Map deliberate gestures to system actions. Pointer steering remains isolated from these mappings.",
                            color = AermotusPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        GestureSignal.entries.forEach { signal ->
                            MappingRow(
                                signal = signal,
                                action = mappings[signal] ?: AirAction.NONE,
                                expanded = expanded == signal,
                                onExpand = {
                                    expanded = if (expanded == signal) null else signal
                                },
                                onSelect = { setMapping(signal, it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                mappings = store.resetGestureMappings()
                                expanded = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("RESET MAPPINGS") }

                        Button(
                            onClick = { finish() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AermotusPalette.Cyan,
                                contentColor = AermotusPalette.Void
                            )
                        ) { Text("DONE", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }

    @Composable
    private fun MappingRow(
        signal: GestureSignal,
        action: AirAction,
        expanded: Boolean,
        onExpand: () -> Unit,
        onSelect: (AirAction) -> Unit
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE607171D), RoundedCornerShape(5.dp))
                    .border(
                        1.dp,
                        AermotusPalette.Cyan.copy(alpha = .22f),
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        signal.label.uppercase(),
                        color = AermotusPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .7.sp)
                    )
                    Text(
                        action.label,
                        color = if (action == AirAction.NONE) AermotusPalette.TextMuted else AermotusPalette.CyanBright,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "CHANGE",
                    color = AermotusPalette.Gold,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onExpand,
                modifier = Modifier.background(Color(0xFF071219))
            ) {
                AirAction.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = if (option == action) AermotusPalette.CyanBright else AermotusPalette.TextPrimary
                            )
                        },
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}
