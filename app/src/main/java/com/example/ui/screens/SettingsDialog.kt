package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.data.firebase.FirebaseSyncStatus
import androidx.compose.material3.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import com.example.data.revenuecat.RevenueCatManager
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.ProtocolViewModel

@Composable
fun SettingsDialog(
    viewModel: ProtocolViewModel,
    onDismiss: () -> Unit,
    onResetBaseline: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()

    var authFeedback by remember { mutableStateOf<String?>(null) }
    var authFeedbackIsError by remember { mutableStateOf(false) }
    var isVerifyingEmail by remember { mutableStateOf(false) }
    var isCheckingVerification by remember { mutableStateOf(false) }
    var isSyncingCloud by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showPrivacyTerms by remember { mutableStateOf(false) }

    if (showPrivacyTerms) {
        com.example.ui.components.PrivacyAndLegalDialog(
            viewModel = viewModel,
            onDismiss = { showPrivacyTerms = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .imePadding()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
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
                    Text(
                        text = "PROTOCOL SETTINGS",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Baseline summary card
                Text(
                    text = "Active Baseline",
                    color = palette.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Wake anchor: ${profile?.wakeTime ?: "06:30"} · Focus: ${profile?.focus ?: "Deep Sleep"} · Telemetry: ${profile?.wearable ?: "None"}",
                    color = palette.mutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(18.dp))

                // OneSignal Integration Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Circadian Push Reminders",
                            color = palette.foreground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.success.copy(alpha = 0.15f))
                            .border(1.dp, palette.success, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "CONNECTED",
                            color = palette.success,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Automated physiological push alerts scheduled locally on your device based on your wake anchor.",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Notification Channel Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOTIFICATION CHANNEL",
                                color = palette.mutedForeground,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ACTIVE",
                                color = palette.success,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "protocol_circadian_channel",
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Scheduled Protocol Alerts:",
                    color = palette.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "• Morning Lux Window (0-30 min post-wake)",
                        "• Caffeine Unlock (90 min post-wake)",
                        "• Melatonin Dimming (14 hr post-wake)",
                        "• Wind-Down Sequence (16 hr post-wake)"
                    ).forEach { alert ->
                        Text(
                            text = alert,
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(18.dp))

                // Live Wearable Telemetry & Wearable APIs Section
                var showWearableApiDocs by remember { mutableStateOf(false) }
                val currentWearable = profile?.wearable ?: "Health Connect"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Wearable Telemetry",
                            color = palette.foreground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accent.copy(alpha = 0.15f))
                            .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SYNCED",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live biometric synchronization for automated wake detection, recovery scores, and HRV.",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Wearable Selector Pills
                Text(
                    text = "Active Sensor Source:",
                    color = palette.foreground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Health Connect", "Oura Ring", "Whoop 4.0", "Garmin").forEach { dev ->
                        val isSelected = currentWearable.contains(dev, ignoreCase = true) || (dev == "Health Connect" && (currentWearable.isEmpty() || currentWearable == "None" || currentWearable == "Apple Watch"))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) palette.accent else palette.surfaceRaised)
                                .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerHaptic(context, 1)
                                    viewModel.updateWearable(dev)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dev,
                                color = if (isSelected) palette.accentForeground else palette.foreground,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Wearable Status Card
                val isWearableConnected = !currentWearable.equals("None", ignoreCase = true) && currentWearable.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isWearableConnected) "PROVIDER: ${currentWearable.uppercase()}" else "WEARABLE STATUS: NOT CONNECTED",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isWearableConnected) palette.accent else palette.mutedForeground, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isWearableConnected) "CONFIGURED" else "DISCONNECTED",
                                    color = if (isWearableConnected) palette.accent else palette.mutedForeground,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("RECOVERY", color = palette.mutedForeground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isWearableConnected) "Awaiting sync" else "--", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("HRV (rMSSD)", color = palette.mutedForeground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isWearableConnected) "Awaiting sensor" else "-- ms", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("RESTING HR", color = palette.mutedForeground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isWearableConnected) "Awaiting sensor" else "-- bpm", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("SLEEP", color = palette.mutedForeground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isWearableConnected) "Awaiting sync" else "-- h -- m", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle API Reference
                Row(
                    modifier = Modifier
                        .clickable { showWearableApiDocs = !showWearableApiDocs }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showWearableApiDocs) "▾ Hide Live Wearable API Architectures" else "▸ View Live Wearable API Endpoints & Architecture",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showWearableApiDocs) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceRaised)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. Android Health Connect (Unified SDK)",
                            color = palette.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "androidx.health.connect:connect-client\nReads SleepSessionRecord, HeartRateRecord, and Steps directly on-device from Google Health Connect without vendor lock-in.",
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        Text(
                            text = "2. Oura Ring API v2",
                            color = palette.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GET https://api.ouraring.com/v2/usercollection/daily_sleep\nGET https://api.ouraring.com/v2/usercollection/daily_readiness\nSynced via OAuth2 Bearer token; pulls REM, deep sleep duration, and autonomic readiness score.",
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        Text(
                            text = "3. Whoop Developer API v1",
                            color = palette.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GET https://api.prod.whoop.com/developer/v1/recovery\nGET https://api.prod.whoop.com/developer/v1/cycle\nRetrieves Recovery %, Day Strain, and nocturnal HRV (rMSSD).",
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        Text(
                            text = "4. Garmin Health Companion API",
                            color = palette.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Push webhook ping/pull architecture streaming Body Battery, All-Day Stress, and Pulse Ox sleep telemetry.",
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(18.dp))

                // RevenueCat Section
                val isLiveConnected by viewModel.revenueCatManager.isLiveConnected.collectAsState()
                var customApiKeyInput by remember { mutableStateOf(viewModel.revenueCatManager.getCustomApiKey(context)) }
                var apiKeyFeedback by remember { mutableStateOf<String?>(null) }
                var showAdvancedBilling by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Protocol Membership",
                            color = palette.foreground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (profile?.isPro == true) palette.accentSoft else palette.surfaceRaised)
                            .border(1.dp, if (profile?.isPro == true) palette.accent else palette.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (profile?.isPro == true) "EXECUTIVE PRO" else "STANDARD TIER",
                            color = if (profile?.isPro == true) palette.accent else palette.mutedForeground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profile?.isPro == true) "All Stacks & Real-Time Sensors Unlocked" else "Unlock full circadian suite",
                            color = palette.foreground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Google Play encrypted subscription engine",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile?.isPro == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                    .background(palette.surfaceRaised)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        triggerHaptic(context, 2)
                                        viewModel.resetSubscriptionToFree()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Reset Free",
                                    color = palette.mutedForeground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, palette.accent, RoundedCornerShape(10.dp))
                                    .background(palette.accentSoft)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        triggerHaptic(context, 1)
                                        onDismiss()
                                        viewModel.navigateToPaywall()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Test Paywall",
                                    color = palette.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                .background(palette.surfaceRaised)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Button
                                ) {
                                    triggerHaptic(context, 1)
                                    viewModel.restoreSubscription(onSuccess = {})
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Restore",
                                color = palette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Payment Processing Transparency Notice
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subscriptions and purchases are verified and processed through Google Play Billing and RevenueCat.",
                        color = palette.mutedForeground,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clickable { showAdvancedBilling = !showAdvancedBilling }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAdvancedBilling) "▾ Hide Diagnostic Billing Tools" else "▸ Advanced Billing Diagnostics",
                        color = palette.mutedForeground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showAdvancedBilling) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Google Play / RevenueCat Public API Key:",
                        color = palette.mutedForeground,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = { 
                                customApiKeyInput = it 
                                apiKeyFeedback = null
                            },
                            placeholder = { Text("goog_... (Google Play public key)", color = palette.mutedForeground, fontSize = 11.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.border,
                                focusedTextColor = palette.foreground,
                                unfocusedTextColor = palette.foreground
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Button
                                ) {
                                    triggerHaptic(context, 1)
                                    val success = viewModel.revenueCatManager.saveCustomApiKey(context, customApiKeyInput)
                                    apiKeyFeedback = if (viewModel.revenueCatManager.isLiveKey(customApiKeyInput)) {
                                        if (success) "Connected to RevenueCat Live!" else "Invalid key or connection error"
                                    } else if (customApiKeyInput.startsWith("test_")) {
                                        "Saved. 'test_' keys use Safe Sandbox Mode (Play Store uses 'goog_' keys)"
                                    } else {
                                        "Saved in Resilient Sandbox Mode"
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Save",
                                color = palette.accentForeground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (apiKeyFeedback != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = apiKeyFeedback ?: "",
                            color = if (isLiveConnected) palette.success else palette.mutedForeground,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ship-a-thon A/B Offering Experiment:",
                        color = palette.mutedForeground,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val activeOfferingId by viewModel.revenueCatManager.activeOfferingId.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeOfferingId == "standard") palette.accentSoft else palette.surfaceRaised)
                                .border(1.dp, if (activeOfferingId == "standard") palette.accent else palette.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerHaptic(context, 1)
                                    viewModel.revenueCatManager.switchOffering("standard")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Standard (Control)",
                                color = if (activeOfferingId == "standard") palette.accent else palette.mutedForeground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeOfferingId == "shipaton_experiment_offer") palette.accentSoft else palette.surfaceRaised)
                                .border(1.dp, if (activeOfferingId == "shipaton_experiment_offer") palette.accent else palette.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerHaptic(context, 1)
                                    viewModel.revenueCatManager.switchOffering("shipaton_experiment_offer")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Promo (Experiment)",
                                color = if (activeOfferingId == "shipaton_experiment_offer") palette.accent else palette.mutedForeground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(18.dp))

                // Account & Security Lifecycle (Cloud Protocol Sync)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Backup & Sync",
                            color = palette.foreground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewModel.firebaseManager.isFirebaseInitialized) palette.success.copy(alpha = 0.15f) else palette.surfaceRaised)
                            .border(1.dp, if (viewModel.firebaseManager.isFirebaseInitialized) palette.success else palette.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (viewModel.firebaseManager.isFirebaseInitialized) "SYNC ACTIVE" else "LOCAL SECURE",
                            color = if (viewModel.firebaseManager.isFirebaseInitialized) palette.success else palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Encrypted biometric synchronization for wake protocols, stacks, and streaks.",
                    color = palette.mutedForeground,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Account Identity Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = profile?.email ?: "No account email",
                                    color = palette.foreground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val isVerified = profile?.isEmailVerified == true
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isVerified) palette.success.copy(alpha = 0.15f) else palette.accent.copy(alpha = 0.15f))
                                    .border(1.dp, if (isVerified) palette.success else palette.accent, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (isVerified) "VERIFIED" else "UNVERIFIED",
                                    color = if (isVerified) palette.success else palette.accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        if (profile?.firebaseUid != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Firebase UID: ${profile?.firebaseUid}",
                                color = palette.mutedForeground,
                                fontSize = 10.sp
                            )
                        }

                        // If not verified, show real Firebase verification actions
                        if (profile?.isEmailVerified != true) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Your email address is not yet verified with Firebase. Send an official verification email, click the link, and tap Refresh.",
                                color = palette.mutedForeground,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.accentSoft)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            role = Role.Button
                                        ) {
                                            triggerHaptic(context, 1)
                                            isVerifyingEmail = true
                                            viewModel.sendEmailVerification { status, msg ->
                                                isVerifyingEmail = false
                                                authFeedback = msg
                                                authFeedbackIsError = (status == FirebaseSyncStatus.ERROR)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isVerifyingEmail) "Sending..." else "Send Email Link",
                                        color = palette.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.surface)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            role = Role.Button
                                        ) {
                                            triggerHaptic(context, 1)
                                            isCheckingVerification = true
                                            viewModel.refreshEmailVerificationStatus { status, verified, msg ->
                                                isCheckingVerification = false
                                                authFeedback = msg
                                                authFeedbackIsError = (status == FirebaseSyncStatus.ERROR)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = palette.foreground,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isCheckingVerification) "Checking..." else "Refresh Status",
                                            color = palette.foreground,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cloud Protocol Sync Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSoft)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            isSyncingCloud = true
                            viewModel.syncCloudData { status, msg ->
                                isSyncingCloud = false
                                authFeedback = msg
                                authFeedbackIsError = (status == FirebaseSyncStatus.ERROR)
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSyncingCloud) "Syncing to Firebase..." else "Sync Now with Firebase Cloud",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (authFeedback != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authFeedbackIsError) palette.danger.copy(alpha = 0.1f) else palette.accentSoft)
                            .border(1.dp, if (authFeedbackIsError) palette.danger.copy(alpha = 0.4f) else palette.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = authFeedback ?: "",
                            color = if (authFeedbackIsError) palette.danger else palette.accent,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sign Out / Switch User
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            onDismiss()
                            viewModel.signOut()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign Out / Switch Account",
                        color = palette.mutedForeground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(18.dp))

                // Reset Baseline
                if (!showResetConfirm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Button
                            ) {
                                triggerHaptic(context, 1)
                                showResetConfirm = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = palette.danger,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset Baseline",
                                color = palette.danger,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceRaised)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Reset all baseline intake data?",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This will return you to step 1 of onboarding.",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.danger)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        triggerHaptic(context, 2)
                                        viewModel.resetBaseline()
                                        onResetBaseline()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Confirm Reset",
                                    color = palette.surface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surface)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        showResetConfirm = false
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = palette.mutedForeground,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .clickable {
                            triggerHaptic(context, 0)
                            showPrivacyTerms = true
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy, Terms & GDPR Export",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ProtocolPrimaryButton(
                    text = "Keep Protocol",
                    onClick = onDismiss,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = palette.accentForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}
