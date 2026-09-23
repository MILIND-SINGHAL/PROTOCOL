package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun BreathPacerScreen(
    onClose: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    val phases = listOf("Inhale", "Hold", "Exhale", "Hold")
    var phaseIndex by remember { mutableIntStateOf(0) }
    var cycleKey by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1.0f) }

    LaunchedEffect(cycleKey) {
        scale.snapTo(1.0f)
        while (isActive) {
            // Phase 0: Inhale 4s -> scale to 1.45
            phaseIndex = 0
            triggerHaptic(context, 0)
            scale.animateTo(1.45f, animationSpec = tween(4000, easing = LinearEasing))

            // Phase 1: Hold 4s
            phaseIndex = 1
            triggerHaptic(context, 0)
            delay(4000)

            // Phase 2: Exhale 4s -> scale to 1.0
            phaseIndex = 2
            triggerHaptic(context, 0)
            scale.animateTo(1.0f, animationSpec = tween(4000, easing = LinearEasing))

            // Phase 3: Hold 4s
            phaseIndex = 3
            triggerHaptic(context, 0)
            delay(4000)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isCompact = screenWidth < 380.dp
        val horizontalPadding = if (isCompact) 16.dp else 24.dp

        // Dynamic circle base size to guarantee it never overflows on small phones
        val circleBaseSize = min(screenWidth * 0.52f, screenHeight * 0.26f).coerceIn(120.dp, 200.dp)
        val innerCircleSize = circleBaseSize * 0.625f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TACTICAL UTILITY",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Button,
                                onClick = {
                                    triggerHaptic(context, 1)
                                    cycleKey++
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Pacer",
                            tint = palette.foreground,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Button,
                                onClick = onClose
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Pacer",
                            tint = palette.foreground,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Center Breathing Circle with adaptive sizing
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(circleBaseSize)
                        .scale(scale.value)
                        .border(1.5.dp, palette.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(innerCircleSize)
                            .clip(CircleShape)
                            .background(palette.accentSoft)
                            .border(1.5.dp, palette.accent, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = phases[phaseIndex],
                    color = palette.foreground,
                    fontSize = if (isCompact) 28.sp else 34.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Four seconds per phase",
                    color = palette.mutedForeground,
                    fontSize = 13.sp
                )
            }

            // Bottom button
            ProtocolPrimaryButton(
                text = "Finish session",
                onClick = {
                    triggerHaptic(context, 1)
                    onClose()
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finish",
                        tint = palette.accentForeground,
                        modifier = Modifier.size(19.dp)
                    )
                }
            )
        }
    }
}
