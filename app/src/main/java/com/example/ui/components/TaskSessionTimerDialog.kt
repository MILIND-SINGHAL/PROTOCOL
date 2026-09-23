package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ProtocolTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TaskSessionTimerDialog(
    taskTitle: String,
    initialMinutes: Int,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    var totalSeconds by remember { mutableIntStateOf(initialMinutes * 60) }
    var secondsRemaining by remember { mutableIntStateOf(initialMinutes * 60) }
    var isRunning by remember { mutableStateOf(false) }

    val isSunlightTask = remember(taskTitle) {
        taskTitle.contains("Sunlight", ignoreCase = true) || taskTitle.contains("Light", ignoreCase = true)
    }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val lightSensor = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val hasHardwareSensor = lightSensor != null

    var hardwareLux by remember { mutableFloatStateOf(0f) }
    val effectiveLux = if (hasHardwareSensor) hardwareLux else 0f
    val isThresholdReached = hasHardwareSensor && effectiveLux >= 10000f

    DisposableEffect(isSunlightTask, lightSensor) {
        if (!isSunlightTask || sensorManager == null || lightSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    hardwareLux = event.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            if (isActive) {
                secondsRemaining -= 1
                if (secondsRemaining % 60 == 0 || secondsRemaining == 10 || secondsRemaining == 5) {
                    triggerHaptic(context, 0)
                }
                if (secondsRemaining == 0) {
                    isRunning = false
                    triggerHaptic(context, 4) // Success burst
                    onCompleted()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
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
                    Text(
                        text = "SESSION TIMER",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceRaised)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Button,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.foreground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = taskTitle,
                    color = palette.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isSunlightTask) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Sunlight Detection HUD
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isThresholdReached) palette.accentSoft else palette.surfaceRaised)
                            .border(1.dp, if (isThresholdReached) palette.accent else palette.border, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = if (isThresholdReached) palette.accent else palette.mutedForeground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (!hasHardwareSensor) "Ambient light sensor unavailable" else "OPTICAL LUX SENSOR",
                                        color = if (isThresholdReached) palette.accent else palette.mutedForeground,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = if (!hasHardwareSensor) "Lux unavailable" else if (effectiveLux >= 10000f) "100% Sunlight (10k+ Lux)" else "${(effectiveLux / 100).toInt()}% Target",
                                    color = if (isThresholdReached) palette.accent else palette.foreground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress Bar towards full morning sunlight threshold
                            val luxProgress = (effectiveLux / 10000f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette.border)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(luxProgress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isThresholdReached) palette.accent else palette.mutedForeground)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        !hasHardwareSensor -> "Timer mode only • Lux measurement unavailable"
                                        effectiveLux >= 10000f -> "Direct natural outdoor light active"
                                        effectiveLux >= 2500f -> "Cloudy / Indirect light (~20m optimal)"
                                        else -> "Low ambient lux — face morning sun"
                                    },
                                    color = if (isThresholdReached) palette.accent else palette.mutedForeground,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (hasHardwareSensor) {
                                    "Measuring optical lux via ambient sensor for circadian stimulus."
                                } else {
                                    "This device does not have a hardware light sensor (TYPE_LIGHT). The timer tracks duration, but lux measurements are unavailable."
                                },
                                color = palette.faintForeground,
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Circular Time Display
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                val timeFormatted = String.format("%02d:%02d", minutes, seconds)

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(if (isSunlightTask && isThresholdReached) palette.accentSoft else palette.surfaceRaised)
                        .border(
                            2.5.dp,
                            if (isSunlightTask && isThresholdReached) palette.accent else palette.border,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeFormatted,
                            color = palette.foreground,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isRunning) "ACTIVE" else "PAUSED",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Time adjustment controls (Add / Subtract 1 min or 5 min)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .clickable {
                                triggerHaptic(context, 0)
                                if (secondsRemaining >= 60) {
                                    secondsRemaining -= 60
                                    totalSeconds = totalSeconds.coerceAtLeast(secondsRemaining)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Remove, contentDescription = "-1m", tint = palette.foreground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1m", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .clickable {
                                triggerHaptic(context, 0)
                                secondsRemaining += 60
                                totalSeconds += 60
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "+1m", tint = palette.foreground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1m", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .clickable {
                                triggerHaptic(context, 0)
                                secondsRemaining += 300
                                totalSeconds += 300
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "+5m", tint = palette.foreground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("5m", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Play / Pause / Reset / Complete Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play / Pause
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accent)
                            .clickable {
                                triggerHaptic(context, 1)
                                isRunning = !isRunning
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = palette.accentForeground,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) "Pause" else "Start",
                                color = palette.accentForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Reset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable {
                                triggerHaptic(context, 1)
                                isRunning = false
                                secondsRemaining = initialMinutes * 60
                                totalSeconds = initialMinutes * 60
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Timer",
                                tint = palette.foreground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset",
                                color = palette.foreground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Mark Done
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable {
                                triggerHaptic(context, 4)
                                onCompleted()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
