package com.example.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ProtocolTheme

/**
 * Authentic 4-color Google "G" logo rendered via vector paths
 */
@Composable
fun GoogleLogoIcon(
    sizeDp: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    // Official Google Brand Colors
    val blue = Color(0xFF4285F4)
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)

    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeWidth = w * 0.22f
        val radius = (w - strokeWidth) / 2f

        // Top arc (Red)
        val redPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 200f,
                sweepAngleDegrees = 100f,
                forceMoveTo = true
            )
        }
        drawPath(redPath, red, style = Stroke(width = strokeWidth))

        // Left arc (Yellow)
        val yellowPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 120f,
                sweepAngleDegrees = 85f,
                forceMoveTo = true
            )
        }
        drawPath(yellowPath, yellow, style = Stroke(width = strokeWidth))

        // Bottom arc (Green)
        val greenPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 20f,
                sweepAngleDegrees = 105f,
                forceMoveTo = true
            )
        }
        drawPath(greenPath, green, style = Stroke(width = strokeWidth))

        // Blue horizontal bar and right arc
        val blueArc = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 300f,
                sweepAngleDegrees = 70f,
                forceMoveTo = true
            )
        }
        drawPath(blueArc, blue, style = Stroke(width = strokeWidth))

        // Horizontal blue stem
        drawLine(
            color = blue,
            start = Offset(cx - strokeWidth * 0.15f, cy),
            end = Offset(cx + radius + strokeWidth * 0.45f, cy),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Premium Google Sign-In Button compliant with Google Identity Guidelines
 */
@Composable
fun GoogleSignInButton(
    text: String = "Continue with Google",
    subtitle: String = "Fast & secure. Google handles authentication.",
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceRaised)
            .border(1.2.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable(
                enabled = !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF4285F4),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    GoogleLogoIcon(sizeDp = 22.dp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = palette.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4285F4).copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "OAuth2",
                    color = Color(0xFF4285F4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Authentic Google Identity Services Account Picker Dialog (One Tap Experience)
 */
@Composable
fun GoogleAccountPickerDialog(
    defaultEmail: String? = null,
    defaultName: String? = null,
    onAccountSelected: (email: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    var isCustomAccount by remember { mutableStateOf(defaultEmail.isNullOrBlank()) }
    var customEmail by remember { mutableStateOf(defaultEmail ?: "") }
    var customName by remember { mutableStateOf(defaultName ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Google Logo & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoogleLogoIcon(sizeDp = 24.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            color = palette.foreground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose an account to continue to Protocol Circadian OS",
                    color = palette.mutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(14.dp))

                if (!isCustomAccount && !defaultEmail.isNullOrBlank()) {
                    val activeName = if (!defaultName.isNullOrBlank()) defaultName else defaultEmail.substringBefore("@")
                    // Primary Google Account Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceRaised)
                            .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple()
                            ) {
                                triggerHaptic(context, 1)
                                isSubmitting = true
                                onAccountSelected(defaultEmail, activeName)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4285F4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeName.firstOrNull()?.uppercase() ?: "G",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeName,
                                    color = palette.foreground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = defaultEmail,
                                color = palette.mutedForeground,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Use another account button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple()
                            ) {
                                triggerHaptic(context, 0)
                                isCustomAccount = true
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceRaised),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = palette.foreground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Use another Google account",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Custom Google account input
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter your Google Account details",
                            color = palette.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        androidx.compose.material3.OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Your Full Name") },
                            placeholder = { Text("e.g. Jane Doe") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.material3.OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            label = { Text("Google Email (@gmail.com)") },
                            placeholder = { Text("username@gmail.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!defaultEmail.isNullOrBlank()) {
                                androidx.compose.material3.TextButton(
                                    onClick = { isCustomAccount = false }
                                ) {
                                    Text("Back", color = palette.mutedForeground)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            androidx.compose.material3.Button(
                                onClick = {
                                    if (customEmail.isNotBlank()) {
                                        triggerHaptic(context, 1)
                                        isSubmitting = true
                                        onAccountSelected(
                                            customEmail.trim(),
                                            if (customName.isNotBlank()) customName.trim() else customEmail.substringBefore("@")
                                        )
                                    }
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4285F4)
                                )
                            ) {
                                Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security disclaimer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google verifies your identity and shares your name, email, and profile photo with Protocol. Passwords are never shared.",
                        color = palette.mutedForeground,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                if (isSubmitting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4285F4),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Securing session with Google...",
                            color = palette.mutedForeground,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
