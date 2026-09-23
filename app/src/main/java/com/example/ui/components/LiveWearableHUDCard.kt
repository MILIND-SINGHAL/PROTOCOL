package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.ProtocolViewModel

data class WearableBiometrics(
    val deviceName: String,
    val recoveryScore: String,
    val recoverySubtext: String,
    val isOptimal: Boolean,
    val hrvRmssd: String,
    val hrvSubtext: String,
    val restingHr: String,
    val restingHrSubtext: String,
    val detectedWakeTime: String,
    val sleepDuration: String,
    val autonomicInsight: String,
    val connectionStatus: String,
    val isConnected: Boolean
)

@Composable
fun LiveWearableHUDCard(
    viewModel: ProtocolViewModel,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    var showDevicePicker by remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfile.collectAsState()
    val activeDevice = userProfile?.wearable ?: "None"

    val isHealthConnectInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    val biometrics = remember(activeDevice, isHealthConnectInstalled, userProfile?.wakeTime) {
        val userWake = userProfile?.wakeTime ?: "06:30"
        when {
            activeDevice.equals("None", ignoreCase = true) || activeDevice.isEmpty() -> WearableBiometrics(
                deviceName = "NO WEARABLE CONNECTED",
                recoveryScore = "--",
                recoverySubtext = "No telemetry",
                isOptimal = false,
                hrvRmssd = "-- ms",
                hrvSubtext = "Sensor offline",
                restingHr = "-- bpm",
                restingHrSubtext = "Sensor offline",
                detectedWakeTime = userWake,
                sleepDuration = "-- h -- m",
                autonomicInsight = "No biometric source connected. Connect Android Health Connect, WHOOP, Oura, or Garmin to stream real autonomic nervous system telemetry.",
                connectionStatus = "DISCONNECTED",
                isConnected = false
            )
            activeDevice.contains("Health Connect", ignoreCase = true) -> {
                if (isHealthConnectInstalled) {
                    WearableBiometrics(
                        deviceName = "HEALTH CONNECT",
                        recoveryScore = "--",
                        recoverySubtext = "Awaiting sync",
                        isOptimal = false,
                        hrvRmssd = "-- ms",
                        hrvSubtext = "Permissions required",
                        restingHr = "-- bpm",
                        restingHrSubtext = "Awaiting sensor log",
                        detectedWakeTime = userWake,
                        sleepDuration = "0 records",
                        autonomicInsight = "Health Connect is installed on this device. Permissions to read Heart Rate and Sleep records must be granted in Android Settings to populate telemetry.",
                        connectionStatus = "PERMISSION REQUIRED",
                        isConnected = false
                    )
                } else {
                    WearableBiometrics(
                        deviceName = "HEALTH CONNECT",
                        recoveryScore = "--",
                        recoverySubtext = "Not installed",
                        isOptimal = false,
                        hrvRmssd = "-- ms",
                        hrvSubtext = "Package missing",
                        restingHr = "-- bpm",
                        restingHrSubtext = "Package missing",
                        detectedWakeTime = userWake,
                        sleepDuration = "Unavailable",
                        autonomicInsight = "Android Health Connect application (com.google.android.apps.healthdata) is not installed on this system. Install from Google Play to sync sensor telemetry.",
                        connectionStatus = "NOT INSTALLED",
                        isConnected = false
                    )
                }
            }
            else -> WearableBiometrics(
                deviceName = activeDevice.uppercase(),
                recoveryScore = "--",
                recoverySubtext = "Awaiting OAuth",
                isOptimal = false,
                hrvRmssd = "-- ms",
                hrvSubtext = "Awaiting sync",
                restingHr = "-- bpm",
                restingHrSubtext = "Awaiting sync",
                detectedWakeTime = userWake,
                sleepDuration = "-- h -- m",
                autonomicInsight = "$activeDevice is selected. Cloud partner OAuth connection required to read raw nocturnal telemetry.",
                connectionStatus = "AWAITING CREDENTIALS",
                isConnected = false
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .background(palette.surface)
            .padding(16.dp)
    ) {
        Column {
            // Header: Device badge & Live streaming status
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
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = biometrics.deviceName,
                                color = palette.foreground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (biometrics.isConnected) palette.success
                                        else if (biometrics.connectionStatus.contains("REQUIRED") || biometrics.connectionStatus.contains("AWAITING")) Color(0xFFEAB308)
                                        else palette.mutedForeground,
                                        CircleShape
                                    )
                            )
                        }
                        Text(
                            text = biometrics.connectionStatus,
                            color = if (biometrics.isConnected) palette.success else palette.mutedForeground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Switch Device Action
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .background(palette.surfaceRaised)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            showDevicePicker = !showDevicePicker
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch",
                            tint = palette.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SWITCH",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expandable Device Switcher
            AnimatedVisibility(visible = showDevicePicker) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Select Biometric Telemetry Hub:",
                        color = palette.mutedForeground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            "None" to "None",
                            "Health Connect" to "Health Connect",
                            "Whoop" to "Whoop",
                            "Oura" to "Oura",
                            "Garmin" to "Garmin"
                        ).forEach { (label, key) ->
                            val isSelected = if (key == "None") activeDevice.equals("None", ignoreCase = true) || activeDevice.isEmpty()
                                else activeDevice.contains(key, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) palette.accent else palette.surfaceRaised)
                                    .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(8.dp))
                                    .clickable {
                                        triggerHaptic(context, 1)
                                        viewModel.updateWearable(if (key == "None") "None" else label)
                                        showDevicePicker = false
                                        val msg = if (key == "None") "Wearable disconnected" else "Configured provider: $label"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) palette.accentForeground else palette.foreground,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Column Biometric Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Recovery / Readiness
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RECOVERY",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = biometrics.recoveryScore,
                        color = palette.success,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = biometrics.recoverySubtext,
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }

                // HRV
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HRV (rMSSD)",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = biometrics.hrvRmssd,
                        color = palette.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = biometrics.hrvSubtext,
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }

                // Resting HR
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESTING HR",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = biometrics.restingHr,
                        color = palette.foreground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = biometrics.restingHrSubtext,
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }

                // Detected Wake
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WAKE DETECTED",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = biometrics.detectedWakeTime,
                        color = palette.foreground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = biometrics.sleepDuration,
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Autonomic Insight Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier
                            .size(15.dp)
                            .padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = biometrics.autonomicInsight,
                        color = palette.foreground,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button: Truthful according to connection state
            if (activeDevice.equals("None", ignoreCase = true) || activeDevice.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            showDevicePicker = true
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = palette.accentForeground,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONNECT WEARABLE OR HEALTH CONNECT",
                            color = palette.accentForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else if (activeDevice.contains("Health Connect", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            if (isHealthConnectInstalled) {
                                Toast.makeText(
                                    context,
                                    "Health Connect is installed. Permissions must be granted in Android Settings to read sensor data.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Health Connect is not installed on this Android device. Install from Google Play to sync sensor telemetry.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHealthConnectInstalled) "CHECK HEALTH CONNECT PERMISSIONS" else "HEALTH CONNECT NOT INSTALLED",
                            color = palette.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            Toast.makeText(
                                context,
                                "$activeDevice cloud integration requires developer API credentials.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AWAITING $activeDevice OAUTH CREDENTIALS",
                            color = palette.mutedForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
