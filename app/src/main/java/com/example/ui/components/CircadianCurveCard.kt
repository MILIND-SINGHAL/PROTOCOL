package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ProtocolTheme
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

data class CircadianPhaseDetail(
    val phaseIndex: Int,
    val title: String,
    val subtitle: String,
    val protocolRule: String,
    val scientificReason: String,
    val confidence: String = "General guidance",
    val hoursOffset: String
)

@Composable
fun CircadianCurveCard(
    wakeTime: String, // e.g. "06:00"
    modifier: Modifier = Modifier,
    onOpenInfo: (() -> Unit)? = null
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    // Parse wake hour and minute
    val (wakeH, wakeM) = remember(wakeTime) {
        val parts = wakeTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Pair(h, m)
    }

    val cal = Calendar.getInstance()
    val currentHourFloat = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    val wakeHourFloat = wakeH + wakeM / 60f

    // Hours elapsed since wake time (modulo 24)
    val hoursPostWake = ((currentHourFloat - wakeHourFloat + 24f) % 24f)

    // Current phase index: 0 = Phase 1 (0-4h), 1 = Phase 2 (4-11h), 2 = Phase 3 (11-16h), 3 = Phase 4 (16-24h)
    val currentPhaseIndex = when {
        hoursPostWake < 4f -> 0
        hoursPostWake < 11f -> 1
        hoursPostWake < 16f -> 2
        else -> 3
    }

    var selectedPhase by remember { mutableIntStateOf(currentPhaseIndex) }

    val phaseDetails = remember {
        listOf(
            CircadianPhaseDetail(
                phaseIndex = 0,
                title = "Phase 1: Morning Awakening Window",
                subtitle = "Hours 0–4 Post-Wake",
                protocolRule = "Morning light window = wake time + 60m offset; Delay caffeine 90m.",
                scientificReason = "Supports a consistent morning routine and entrains the central circadian pacemaker.",
                confidence = "General guidance",
                hoursOffset = "0h – 4h"
            ),
            CircadianPhaseDetail(
                phaseIndex = 1,
                title = "Phase 2: Daytime Focus & Energy Window",
                subtitle = "Hours 4–11 Post-Wake",
                protocolRule = "Ultradian focus blocks = 90 min; Afternoon caffeine boundary = 14:00.",
                scientificReason = "Aligns deep cognitive work with natural daytime alertness and protects nocturnal sleep drive.",
                confidence = "General guidance",
                hoursOffset = "4h – 11h"
            ),
            CircadianPhaseDetail(
                phaseIndex = 2,
                title = "Phase 3: Evening Wind-Down Transition",
                subtitle = "Hours 11–16 Post-Wake",
                protocolRule = "Dim artificial lighting = wake time + 12–14h; Cool sleep environment (18°C).",
                scientificReason = "Minimizes evening bright light stimulation to support natural evening wind-down.",
                confidence = "General guidance",
                hoursOffset = "11h – 16h"
            ),
            CircadianPhaseDetail(
                phaseIndex = 3,
                title = "Phase 4: Restorative Sleep Opportunity",
                subtitle = "Hours 16–24 Post-Wake",
                protocolRule = "Consistent dark sleep opportunity window: ~7–9 hours uninterrupted.",
                scientificReason = "Supports restorative sleep stages that help consolidate physical recovery and memory.",
                confidence = "General guidance",
                hoursOffset = "16h – 24h"
            )
        )
    }

    val activeDetail = phaseDetails[selectedPhase]

    // Pulsing animation for the "You Are Here" position
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .border(1.dp, palette.border, RoundedCornerShape(23.dp))
            .background(palette.surface)
            .padding(18.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CIRCADIAN TIMING PROTOCOL",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Standard Timing Reference Curve",
                        color = palette.foreground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "WAKE $wakeTime",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Drawing the 24h Circadian Wave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.background)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.matchParentSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    val w = size.width
                    val h = size.height
                    val midY = h * 0.52f
                    val amp = h * 0.38f

                    // Draw subtle grid lines
                    val gridSteps = 4
                    for (i in 1..gridSteps) {
                        val x = w * (i.toFloat() / (gridSteps + 1))
                        drawLine(
                            color = palette.border.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // Construct smooth circadian wave path
                    val curvePath = Path()
                    val fillPath = Path()
                    val pointsCount = 80

                    fillPath.moveTo(0f, h)

                    for (i in 0..pointsCount) {
                        val frac = i / pointsCount.toFloat()
                        val x = frac * w
                        // Circadian curve formula: peak alertness at ~20-30% of day post-wake, lowest in sleep
                        val angle = (frac * 2 * PI) - (PI / 2)
                        val y = (midY - (sin(angle) * amp).toFloat()).coerceIn(4f, h - 4f)

                        if (i == 0) {
                            curvePath.moveTo(x, y)
                            fillPath.lineTo(x, y)
                        } else {
                            curvePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw gradient fill under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw curve line
                    drawPath(
                        path = curvePath,
                        color = palette.accent,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw "Current Time" point on curve
                    val currentFrac = (hoursPostWake / 24f).coerceIn(0f, 1f)
                    val currentX = currentFrac * w
                    val currentAngle = (currentFrac * 2 * PI) - (PI / 2)
                    val currentY = (midY - (sin(currentAngle) * amp).toFloat()).coerceIn(4f, h - 4f)

                    // Pulse outer halo
                    drawCircle(
                        color = palette.accent.copy(alpha = 0.35f),
                        radius = 11.dp.toPx() * pulseScale,
                        center = Offset(currentX, currentY)
                    )
                    // Inner bright bead
                    drawCircle(
                        color = palette.foreground,
                        radius = 4.5.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                    drawCircle(
                        color = palette.accent,
                        radius = 3.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                }

                // Live status tag overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceRaised)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "YOU ARE HERE",
                        color = palette.accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Phase Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                phaseDetails.forEach { phase ->
                    val isSelected = phase.phaseIndex == selectedPhase
                    val isCurrent = phase.phaseIndex == currentPhaseIndex

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> palette.accent
                                    isCurrent -> palette.accentSoft
                                    else -> palette.surfaceRaised
                                }
                            )
                            .border(
                                1.dp,
                                if (isSelected || isCurrent) palette.accent else palette.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    triggerHaptic(context, 0)
                                    selectedPhase = phase.phaseIndex
                                }
                            )
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "P${phase.phaseIndex + 1}",
                                color = if (isSelected) palette.accentForeground else palette.foreground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = phase.hoursOffset,
                                color = if (isSelected) palette.accentForeground.copy(alpha = 0.8f) else palette.mutedForeground,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Phase Deep Dive Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeDetail.title,
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedPhase == currentPhaseIndex) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(palette.accent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE WINDOW",
                                    color = palette.accentForeground,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Protocol Rule
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "PROTOCOL RULE: ",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeDetail.protocolRule,
                            color = palette.foreground,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Reason
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "REASON: ",
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeDetail.scientificReason,
                            color = palette.mutedForeground,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Confidence Level
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CONFIDENCE: ",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accentSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = activeDetail.confidence,
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Methodology: Timing windows are calculated from your configured wake time using evidence-based heuristics. They provide general routine guidance, not personalized laboratory biomarker telemetry.",
                color = palette.mutedForeground.copy(alpha = 0.7f),
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
    }
}
