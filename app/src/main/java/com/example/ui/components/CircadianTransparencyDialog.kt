package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.CircadianState

@Composable
fun CircadianTransparencyDialog(
    circadianState: CircadianState,
    onDismiss: () -> Unit,
    onSaveOffsets: (caffeineMin: Int, lightMin: Int, windDownH: Int) -> Unit
) {
    val palette = ProtocolTheme.palette

    var caffeineOffset by remember { mutableIntStateOf(circadianState.caffeineOffsetMinutes) }
    var lightOffset by remember { mutableIntStateOf(circadianState.lightOffsetMinutes) }
    var windDownOffset by remember { mutableIntStateOf(circadianState.windDownOffsetHours) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CIRCADIAN METHODOLOGY",
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Timing Rules & Transparency",
                                color = palette.foreground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceRaised)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transparency Disclaimer Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "HONEST SCIENCE TRANSPARENCY",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "These timing recommendations are deterministic heuristics derived from your wake time. They provide general routine guidance, not personalized laboratory biometric modeling.",
                            color = palette.mutedForeground,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Rule 1: Morning Sunlight Window
                TimingRuleSection(
                    icon = Icons.Default.WbSunny,
                    title = "Morning Light Window",
                    formula = "Wake time + ${lightOffset}m configurable offset",
                    reason = "Supports a consistent morning light routine to help set your daily circadian rhythms.",
                    confidence = "General guidance",
                    currentValueText = "${lightOffset} min window",
                    options = listOf(30, 45, 60, 90),
                    selectedOption = lightOffset,
                    onOptionSelected = { lightOffset = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Rule 2: Caffeine Delay
                TimingRuleSection(
                    icon = Icons.Default.Coffee,
                    title = "Caffeine Delay Window",
                    formula = "Wake time + ${caffeineOffset}m configurable delay",
                    reason = "Supports natural morning alertness before caffeine intake, which may help reduce afternoon dips.",
                    confidence = "General guidance",
                    currentValueText = "${caffeineOffset} min delay",
                    options = listOf(60, 75, 90, 120),
                    selectedOption = caffeineOffset,
                    onOptionSelected = { caffeineOffset = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Rule 3: Evening Wind Down
                TimingRuleSection(
                    icon = Icons.Default.Nightlight,
                    title = "Evening Wind-Down",
                    formula = "Wake time + ${windDownOffset}h configurable offset",
                    reason = "Prepares the body for rest and supports natural evening wind-down.",
                    confidence = "General guidance",
                    currentValueText = "${windDownOffset}h post-wake",
                    options = listOf(12, 13, 14, 15),
                    selectedOption = windDownOffset,
                    onOptionSelected = { windDownOffset = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Save button
                Button(
                    onClick = {
                        onSaveOffsets(caffeineOffset, lightOffset, windDownOffset)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "SAVE CONFIGURATION",
                        color = palette.accentForeground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingRuleSection(
    icon: ImageVector,
    title: String,
    formula: String,
    reason: String,
    confidence: String,
    currentValueText: String,
    options: List<Int>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit
) {
    val palette = ProtocolTheme.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.background)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = palette.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.accentSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = confidence,
                    color = palette.accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "RULE: $formula",
            color = palette.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "REASON: $reason",
            color = palette.mutedForeground,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Configurable offset selector chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                val isSelected = opt == selectedOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) palette.accent else palette.surfaceRaised)
                        .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(8.dp))
                        .clickable { onOptionSelected(opt) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (opt > 24) "${opt}m" else "${opt}h"
                    Text(
                        text = label,
                        color = if (isSelected) palette.accentForeground else palette.foreground,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
