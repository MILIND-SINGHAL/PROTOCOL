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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.FirebaseSyncStatus
import com.example.ui.components.GoogleLogoIcon
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ProtocolViewModel
import com.example.viewmodel.UserSessionState

@Composable
fun AccountScreenContent(
    viewModel: ProtocolViewModel,
    activeThemeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onResetBaseline: () -> Unit,
    onOpenAuth: () -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    var isGoogleLoading by remember { mutableStateOf(false) }

    val isAuthenticated = sessionState == UserSessionState.AUTHENTICATED
    val userEmail = userProfile?.email
    val isGoogleUser = isAuthenticated && userEmail?.endsWith("@gmail.com", ignoreCase = true) == true
    val userName = if (!userProfile?.displayName.isNullOrBlank()) {
        userProfile?.displayName ?: "Protocol Member"
    } else if (!userEmail.isNullOrBlank()) {
        userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
    } else {
        "Protocol Member"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Text(
            text = "ACCOUNT & IDENTITY",
            color = palette.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )

        // Main User Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar with Google Badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (isGoogleUser) listOf(Color(0xFF4285F4), Color(0xFF34A853))
                                    else listOf(palette.accent, palette.accentSoft)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.firstOrNull()?.uppercase() ?: "P",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userName,
                                color = palette.foreground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isAuthenticated) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = if (isGoogleUser) Color(0xFF4285F4) else palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isAuthenticated) userEmail ?: "" else "Guest Mode (Not Signed In)",
                            color = palette.mutedForeground,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Status Chip
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isGoogleUser) {
                                GoogleLogoIcon(sizeDp = 12.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Google Identity Verified",
                                    color = Color(0xFF4285F4),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = if (isAuthenticated) "Local Verified" else "Local Session",
                                    color = palette.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = " • ",
                                color = palette.mutedForeground,
                                fontSize = 10.sp
                            )

                            val isPro = userProfile?.isPro == true
                            Text(
                                text = if (isPro) "Protocol Pro" else "Member",
                                color = if (isPro) palette.accent else palette.foreground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(14.dp))

                // Subscription Plan Details
                val isPro = userProfile?.isPro == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE SUBSCRIPTION",
                            color = palette.mutedForeground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPro) "Protocol Annual (Pro)" else "Free Protocol Baseline",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subscription Management via Google Play
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(palette.accentSoft)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            if (!isPro) {
                                viewModel.setNavDestination("paywall")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Subscription",
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isPro) "Google Play Subscription" else "Upgrade to Protocol Pro",
                                color = palette.foreground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPro) "Billed and managed securely via Google Play" else "Unlock full circadian stack, timers & cloud sync",
                                color = palette.mutedForeground,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (!isPro) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Upgrade",
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Binary & Access Integrity Status
                val securityReport by viewModel.securityReport.collectAsState()
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Integrity",
                            tint = if (securityReport?.isTampered == true) palette.danger else palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SIGNATURE & ENVIRONMENT INTEGRITY",
                                color = palette.mutedForeground,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (securityReport?.isTampered == true) "Untrusted Certificate / Altered Binary" else "Verified SHA-256 Digest • Server-Anchored Pro",
                                color = if (securityReport?.isTampered == true) palette.danger else palette.foreground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (securityReport?.isTampered == true) palette.danger.copy(alpha = 0.15f) else palette.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (securityReport?.isTampered == true) "UNTRUSTED" else "SIGNATURE VERIFIED",
                            color = if (securityReport?.isTampered == true) palette.danger else palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switch / Sign-In with Google Button
                GoogleSignInButton(
                    text = if (isAuthenticated) "Switch Google Account" else "Sign In with Google",
                    subtitle = "Google manages authentication & protects credentials",
                    isLoading = isGoogleLoading,
                    onClick = {
                        triggerHaptic(context, 1)
                        isGoogleLoading = true
                        viewModel.signInWithGoogle(context) { status, _ ->
                            isGoogleLoading = false
                            if (status == FirebaseSyncStatus.REAL_SUCCESS) {
                                triggerHaptic(context, 4)
                            } else {
                                triggerHaptic(context, 5)
                            }
                        }
                    }
                )

                if (isAuthenticated) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = onOpenAuth
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = null,
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Full Login & Signup Screen",
                            color = palette.mutedForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Appearance & Display Theme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "App Color Theme",
                            color = palette.foreground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceRaised)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeOptionPill(
                        icon = Icons.Default.DarkMode,
                        label = "Dark OLED",
                        isSelected = activeThemeMode == ThemeMode.DARK,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            triggerHaptic(context, 0)
                            onThemeChanged(ThemeMode.DARK)
                        }
                    )
                    ThemeOptionPill(
                        icon = Icons.Default.LightMode,
                        label = "Modern Light",
                        isSelected = activeThemeMode == ThemeMode.LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            triggerHaptic(context, 0)
                            onThemeChanged(ThemeMode.LIGHT)
                        }
                    )
                    ThemeOptionPill(
                        icon = Icons.Default.Palette,
                        label = "Cozy Amber",
                        isSelected = activeThemeMode == ThemeMode.COZY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            triggerHaptic(context, 0)
                            onThemeChanged(ThemeMode.COZY)
                        }
                    )
                }
            }
        }

        // System Settings & Actions Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Baseline recalibration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            triggerHaptic(context, 1)
                            onResetBaseline()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Retake Baseline Onboarding",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Recalibrate your wake-time, focus stack & primary wearable",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(16.dp)
                    )
                }

                HorizontalDivider(color = palette.border)

                // Sign Out
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            triggerHaptic(context, 3)
                            viewModel.signOut()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = palette.danger,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAuthenticated) "Sign Out of Account" else "Reset Session",
                            color = palette.danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Clears active credentials and returns to authentication screen",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ThemeOptionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) palette.surface else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) palette.accent.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) palette.accent else palette.mutedForeground,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) palette.foreground else palette.mutedForeground,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
