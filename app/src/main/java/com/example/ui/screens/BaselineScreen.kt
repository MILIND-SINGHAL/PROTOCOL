package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProtocolLogoMark
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme

@Composable
fun BaselineScreen(
    onComplete: (wakeTime: String, focus: String, wearable: String) -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    var step by remember { mutableIntStateOf(0) }
    var wakeTime by remember { mutableStateOf("06:30") }
    var focus by remember { mutableStateOf("Deep Sleep") }
    var wearable by remember { mutableStateOf("None") }

    val wakeTimes = listOf(
        "05:30", "05:45", "06:00", "06:15", "06:30", "06:45",
        "07:00", "07:15", "07:30", "07:45", "08:00", "08:15",
        "08:30", "08:45", "09:00"
    )

    val titles = listOf(
        "Start with your baseline.",
        "What should Protocol optimize?",
        "One last signal."
    )

    val subtitles = listOf(
        "A few precise inputs are all we need to shape your day.",
        "Choose the outcome you want to feel more often.",
        "Connect the data you already have, or keep it simple."
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isCompact = maxWidth < 380.dp
        val horizontalPadding = if (isCompact) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProtocolLogoMark(sizeDp = 24)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PROTOCOL",
                        color = palette.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.8.sp
                    )
                }
                Text(
                    text = "0${step + 1} / 03",
                    color = palette.mutedForeground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }

            // Progress bar (3 segments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0..2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i <= step) palette.accent else palette.border)
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 20.dp)
            ) {
            Text(
                text = "BASELINE INTAKE",
                color = palette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = titles[step],
                color = palette.foreground,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitles[step],
                color = palette.mutedForeground,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step_content"
            ) { currentStep ->
                when (currentStep) {
                    0 -> {
                        // Wake Time picker
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                                    .background(palette.surface)
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = wakeTime,
                                        color = palette.foreground,
                                        fontSize = 58.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-2.5).sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "LOCAL TIME",
                                        color = palette.mutedForeground,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Horizontal scroll of chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                wakeTimes.forEach { time ->
                                    val isSelected = time == wakeTime
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                1.dp,
                                                if (isSelected) palette.accent else palette.border,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .background(if (isSelected) palette.accent else palette.surface)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(),
                                                role = Role.RadioButton
                                            ) {
                                                triggerHaptic(context, 0)
                                                wakeTime = time
                                            }
                                            .padding(horizontal = 18.dp, vertical = 13.dp)
                                    ) {
                                        Text(
                                            text = time,
                                            color = if (isSelected) palette.accentForeground else palette.mutedForeground,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Your protocol will work backward from this anchor.",
                                color = palette.faintForeground,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    1 -> {
                        // Focus optimization
                        val focusOptions = listOf(
                            Triple("Physical Recovery", "Mobility, hydration & low-impact movement (Rest & recovery routine)", Icons.Default.FitnessCenter),
                            Triple("Deep Sleep", "Restful sleep habits & evening wind-down (Circadian alignment)", Icons.Default.Bedtime),
                            Triple("Mental Clarity", "Protect focus & cognitive stamina (Structured work blocks & NSDR)", Icons.Default.Psychology),
                            Triple("All Stacks", "The complete executive bio-stack (All protocols combined)", Icons.Default.Bolt)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            focusOptions.forEach { (title, description, icon) ->
                                val isSelected = title == focus
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) palette.accent else palette.border,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .background(if (isSelected) palette.accentSoft else palette.surface)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            role = Role.RadioButton
                                        ) {
                                            triggerHaptic(context, 0)
                                            focus = title
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) palette.accent else palette.surfaceRaised),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = if (isSelected) palette.accentForeground else palette.mutedForeground,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            color = palette.foreground,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = description,
                                            color = palette.mutedForeground,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .border(1.5.dp, if (isSelected) palette.accent else palette.border, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(palette.accent, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceRaised)
                                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "All-Access Pass: Your single subscription unlocks all 4 protocols. You can switch or combine them anytime.",
                                    color = palette.mutedForeground,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    2 -> {
                        // Wearable Telemetry
                        val wearableList = listOf(
                            Pair("Apple Watch", Icons.Default.Watch),
                            Pair("Oura", Icons.Outlined.Circle),
                            Pair("Whoop", Icons.Outlined.ShowChart),
                            Pair("None", Icons.Default.RemoveCircleOutline)
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (i in 0..1) {
                                    val (label, icon) = wearableList[i]
                                    val isSelected = label == wearable
                                    WearableCard(
                                        modifier = Modifier.weight(1f),
                                        label = label,
                                        icon = icon,
                                        isSelected = isSelected,
                                        onClick = {
                                            triggerHaptic(context, 0)
                                            wearable = label
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (i in 2..3) {
                                    val (label, icon) = wearableList[i]
                                    val isSelected = label == wearable
                                    WearableCard(
                                        modifier = Modifier.weight(1f),
                                        label = label,
                                        icon = icon,
                                        isSelected = isSelected,
                                        onClick = {
                                            triggerHaptic(context, 0)
                                            wearable = label
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Local privacy badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surfaceRaised)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Privacy Shield",
                                    tint = palette.mutedForeground,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Your inputs stay securely on this device until you choose to subscribe.",
                                    color = palette.mutedForeground,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom CTA button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 14.dp)
        ) {
            ProtocolPrimaryButton(
                text = if (step == 2) "Generate Protocol" else "Continue",
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        onComplete(wakeTime, focus, wearable)
                    }
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = palette.accentForeground,
                        modifier = Modifier.size(19.dp)
                    )
                }
            )
        }
    }
}
}

@Composable
private fun WearableCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette
    Box(
        modifier = modifier
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isSelected) palette.accent else palette.border,
                RoundedCornerShape(20.dp)
            )
            .background(if (isSelected) palette.accentSoft else palette.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) palette.accent else palette.mutedForeground,
                    modifier = Modifier.size(24.dp)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = label,
                color = palette.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
