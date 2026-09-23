package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiveWearableHUDCard
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.ProtocolViewModel

@Composable
fun BiometricsScreenContent(
    viewModel: ProtocolViewModel,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val activeDevice = userProfile?.wearable ?: "None"
    val isDeviceConnected = !activeDevice.equals("None", ignoreCase = true) && activeDevice.isNotEmpty()

    val isHealthConnectInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    val hrvVal = "-- ms"
    val rhrVal = "-- bpm"
    val recoveryVal = "--"
    val sleepScoreVal = "--%"

    val (hrvStatus, rhrStatus, recoveryStatus, sleepStatus) = if (!isDeviceConnected) {
        listOf("Device not connected", "No sensor stream", "Awaiting device setup", "No sleep records")
    } else if (activeDevice.contains("Health Connect", ignoreCase = true)) {
        if (isHealthConnectInstalled) {
            listOf("Permissions required", "Awaiting sensor log", "Requires 3-day baseline", "Awaiting daily sync")
        } else {
            listOf("App not installed", "Package missing", "Install Health Connect", "Not available")
        }
    } else {
        listOf("Awaiting cloud sync", "Awaiting OAuth", "Awaiting baseline", "Awaiting sleep session")
    }

    var isSyncingNow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Title
        Text(
            text = "WEARABLE STATUS",
            color = palette.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )

        // Live Wearable HUD Card
        LiveWearableHUDCard(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )

        // Physiological Calibration Details
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Cardiovascular & Nervous System Status",
                            color = palette.foreground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live telemetry linked to circadian protocol scheduling",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "HRV BALANCE",
                        value = hrvVal,
                        status = hrvStatus,
                        statusColor = if (isDeviceConnected) palette.accent else palette.mutedForeground,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "RESTING HR",
                        value = rhrVal,
                        status = rhrStatus,
                        statusColor = if (isDeviceConnected) palette.foreground else palette.mutedForeground,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "RECOVERY SCORE",
                        value = recoveryVal,
                        status = recoveryStatus,
                        statusColor = if (isDeviceConnected) palette.accent else palette.mutedForeground,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "SLEEP EFFICIENCY",
                        value = sleepScoreVal,
                        status = sleepStatus,
                        statusColor = if (isDeviceConnected) palette.accent else palette.mutedForeground,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(14.dp))

                // Manual Telemetry Sync Button
                ProtocolPrimaryButton(
                    text = if (isSyncingNow) "Checking Health Connect..." else "Poll Health Connect Telemetry",
                    onClick = {
                        triggerHaptic(context, 1)
                        isSyncingNow = true
                        userProfile?.let { viewModel.updateWearable(it.wearable) }
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isSyncingNow = false
                            triggerHaptic(context, 4)
                            if (isHealthConnectInstalled) {
                                Toast.makeText(
                                    context,
                                    "Health Connect polled: Permissions must be granted in Android Settings to read sensor records.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Health Connect app is not installed on this device (com.google.android.apps.healthdata).",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }, 800)
                    },
                    trailingIcon = {
                        if (isSyncingNow) {
                            CircularProgressIndicator(
                                color = palette.accentForeground,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = palette.accentForeground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }

        // Live OneSignal Cloud Sync Status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automated Circadian Notification Sync",
                        color = palette.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notification tags 'wake_time', 'wearable_type', 'recovery_score', and 'is_pro' are automatically updated with real-time biometric metrics.",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    status: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceRaised)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = palette.mutedForeground,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = palette.foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
