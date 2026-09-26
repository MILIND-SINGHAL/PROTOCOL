package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.example.ui.components.CircadianCurveCard
import com.example.ui.components.GoogleLogoIcon
import com.example.ui.components.LiveWearableHUDCard
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ProtocolViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DashboardStackItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val hasInfo: Boolean = false,
    val hasTimer: Boolean = false,
    val defaultMinutes: Int = 10,
    val isPriority: Boolean = false,
    val priorityTag: String? = null
)

data class ProtocolTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

data class BiologicalStackPlan(
    val morningTitle: String = "Morning Ignition",
    val morningCaption: String,
    val morningItems: List<DashboardStackItem>,
    val middayTitle: String,
    val middayCaption: String,
    val middayItems: List<DashboardStackItem>,
    val eveningTitle: String = "Evening Recovery",
    val eveningCaption: String,
    val eveningItems: List<DashboardStackItem>,
    val focusTagline: String
)

@Composable
fun DashboardScreen(
    viewModel: ProtocolViewModel,
    activeThemeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onResetBaseline: () -> Unit,
    onOpenAuth: () -> Unit = {}
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    val userProfile by viewModel.userProfile.collectAsState()
    val completedItems by viewModel.todayCompleted.collectAsState()
    val circadianState by viewModel.circadianState.collectAsState()
    val notificationLogs by viewModel.notificationLogs.collectAsState()
    val realStreak by viewModel.realStreak.collectAsState()
    val allCompletedDates by viewModel.allCompletedDates.collectAsState()

    var currentTab by remember { mutableStateOf("protocols") }

    var showBreathPacer by remember { mutableStateOf(false) }
    var showScienceNote by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var activeTimerItem by remember { mutableStateOf<DashboardStackItem?>(null) }
    var showStreakShare by remember { mutableStateOf(false) }
    var showMilestoneCelebration by remember { mutableStateOf(false) }
    var showCircadianTransparencyDialog by remember { mutableStateOf(false) }

    val activeInAppMessage by viewModel.notificationManager.activeInAppMessage.collectAsState()
    val adaptiveState by viewModel.adaptiveProtocolState.collectAsState()

    val currentFocus = userProfile?.focus ?: "Deep Sleep"

    val protocolTracks = remember {
        listOf(
            ProtocolTrack("Physical Recovery", "Muscle Recovery", "Mobility & rest routine", Icons.Default.FitnessCenter),
            ProtocolTrack("Deep Sleep", "Deep Sleep", "Evening wind-down & sleep habits", Icons.Default.Bedtime),
            ProtocolTrack("Mental Clarity", "Mental Clarity", "Focus & attention blocks", Icons.Default.Psychology),
            ProtocolTrack("All Stacks", "All Stacks", "Complete executive stack", Icons.Default.Bolt)
        )
    }

    val durationMultiplier = adaptiveState.difficulty.durationMultiplier
    val priorityId = adaptiveState.priorityTaskId
    val priorityLabel = adaptiveState.priorityTaskLabel

    val activePlan = remember(currentFocus, durationMultiplier, priorityId, priorityLabel) {
        val rawPlan = when (currentFocus) {
            "Physical Recovery", "Muscle Recovery" -> BiologicalStackPlan(
                morningCaption = "Dynamic mobility and morning hydration.",
                morningItems = listOf(
                    DashboardStackItem("rec_sun_mobility", "Sunlight + Joint Mobilization", "10 min hips & spine in natural daylight", Icons.Default.WbSunny, hasTimer = true, defaultMinutes = 10),
                    DashboardStackItem("rec_creatine_water", "Hydration + Creatine & Protein", "500ml water + electrolytes & dietary protein", Icons.Default.WaterDrop),
                    DashboardStackItem("rec_zone2_flush", "Zone 2 Low-Impact Flush", "20 min easy aerobic movement for circulation", Icons.Default.DirectionsRun, hasTimer = true, defaultMinutes = 20)
                ),
                middayTitle = "Midday Tissue Recovery",
                middayCaption = "Fascial release & circulation support.",
                middayItems = listOf(
                    DashboardStackItem("rec_tissue_release", "15 Min Foam Roll & Mobility", "Target tight muscle groups and posture", Icons.Default.FitnessCenter, hasTimer = true, defaultMinutes = 15),
                    DashboardStackItem("rec_contrast_flush", "Contrast Shower / Rinse", "Alternating warm & cool water to stimulate circulation", Icons.Default.Opacity)
                ),
                eveningCaption = "Evening nervous system down-regulation & relaxation.",
                eveningItems = listOf(
                    DashboardStackItem("rec_mag_glycinate", "Magnesium Bisglycinate", "Supports muscle relaxation and evening calm", Icons.Default.Bedtime, hasInfo = true),
                    DashboardStackItem("rec_legs_wall", "10 Min Legs-Up-the-Wall", "Gentle passive inversion to promote relaxation", Icons.Default.DirectionsRun, hasTimer = true, defaultMinutes = 10),
                    DashboardStackItem("rec_cool_room", "Cool Sleep Environment (18°C / 65°F)", "Supports natural evening drop in core body temperature", Icons.Default.DeviceThermostat)
                ),
                focusTagline = "Optimized for Muscular Recovery & Evening Restoration"
            )
            "Mental Clarity", "Mental Clarity & Focus" -> BiologicalStackPlan(
                morningCaption = "Morning alertness and cognitive focus calibration.",
                morningItems = listOf(
                    DashboardStackItem("clarity_lux_splash", "Morning Natural Light + Splash", "Supports circadian alertness and morning wakefulness", Icons.Default.WbSunny, hasTimer = true, defaultMinutes = 10),
                    DashboardStackItem("clarity_alpha_coffee", "Caffeine + Hydration (60-90m post-wake)", "Measured caffeine intake to support sustained morning focus", Icons.Default.Coffee),
                    DashboardStackItem("clarity_deep_work", "90 Min Focused Work Block", "Dedicated block for high-priority cognitive tasks", Icons.Default.Bolt, hasTimer = true, defaultMinutes = 90)
                ),
                middayTitle = "Midday Focus & Reset",
                middayCaption = "Mental rest & outdoor oxygen recharge.",
                middayItems = listOf(
                    DashboardStackItem("clarity_dopamine_reset", "15 Min Non-Sleep Deep Rest (NSDR)", "Eyes-closed relaxed breathing to restore attentional focus", Icons.Default.SelfImprovement, hasTimer = true, defaultMinutes = 15),
                    DashboardStackItem("clarity_air_walk", "10 Min Outdoor Walk", "Physical movement in fresh air for midday cognitive refresh", Icons.Default.Air)
                ),
                eveningCaption = "Digital sunset and mental decompression.",
                eveningItems = listOf(
                    DashboardStackItem("clarity_digital_sunset", "Digital Sunset & Work Shutdown", "Close work tabs; transition attention toward rest", Icons.Default.ScreenSearchDesktop),
                    DashboardStackItem("clarity_brain_dump", "Evening Reflection Journaling", "Briefly write down tomorrow's priorities to clear thoughts", Icons.Default.Nightlight),
                    DashboardStackItem("clarity_neuro_down", "Warm Shower & Dim Lights", "Assists autonomic transition into sleep readiness", Icons.Default.DeviceThermostat)
                ),
                focusTagline = "Optimized for Sustained Focus & Mindful Decompression"
            )
            "All Stacks", "All Stacks (Combined)" -> BiologicalStackPlan(
                morningCaption = "Synergistic circadian anchor, hydration & light cardio.",
                morningItems = listOf(
                    DashboardStackItem("all_sunlight", "Morning Daylight + Mobility", "Circadian clock anchor + gentle movement", Icons.Default.WbSunny, hasTimer = true, defaultMinutes = 10),
                    DashboardStackItem("all_hydration", "Morning Mineral Hydration", "Water and essential electrolytes to begin the day", Icons.Default.WaterDrop),
                    DashboardStackItem("all_zone2", "Zone 2 Low-Intensity Movement", "20 min easy aerobic base work", Icons.Default.DirectionsRun, hasTimer = true, defaultMinutes = 20)
                ),
                middayTitle = "Midday Calibration",
                middayCaption = "Afternoon caffeine boundary & autonomic reset.",
                middayItems = listOf(
                    DashboardStackItem("all_caffeine_cutoff", "Afternoon Caffeine Cutoff (2:00 PM)", "Reducing late-day caffeine helps protect deep sleep quality", Icons.Default.Timer),
                    DashboardStackItem("all_nsdr", "15 Min NSDR / Guided Relaxation", "Restorative parasympathetic reset to maintain afternoon clarity", Icons.Default.SelfImprovement, hasTimer = true, defaultMinutes = 15)
                ),
                eveningCaption = "Dimmed lighting, targeted minerals & cool room.",
                eveningItems = listOf(
                    DashboardStackItem("all_blue_block", "Dim Warm Lighting & Screen Boundary", "Reduces harsh evening light exposure to support melatonin", Icons.Default.Nightlight),
                    DashboardStackItem("all_magnesium", "Evening Mineral Support", "Magnesium to encourage neuromuscular relaxation", Icons.Default.Bedtime, hasInfo = true),
                    DashboardStackItem("all_temp", "Cool Bedroom (18°C / 65°F)", "Supports core temperature drop associated with deep sleep", Icons.Default.DeviceThermostat)
                ),
                focusTagline = "Comprehensive Protocol: Daily Energy, Recovery & Restful Sleep"
            )
            else -> BiologicalStackPlan(
                morningCaption = "Anchor your circadian clock and support natural evening sleep drive.",
                morningItems = listOf(
                    DashboardStackItem("sleep_sunlight", "10 Min Morning Natural Light", "Helps synchronize your central circadian pacemaker (SCN)", Icons.Default.WbSunny, hasTimer = true, defaultMinutes = 10),
                    DashboardStackItem("sleep_delay_caffeine", "Delay Caffeine 60–90 Min", "Supports natural morning alertness and avoids afternoon dips", Icons.Default.Coffee),
                    DashboardStackItem("sleep_hydration", "Morning Mineral Hydration", "500ml water with pinch of electrolytes for morning replenishment", Icons.Default.WaterDrop)
                ),
                middayTitle = "Midday Calibration",
                middayCaption = "Protecting sleep drive for tonight's rest.",
                middayItems = listOf(
                    DashboardStackItem("sleep_caffeine_cutoff", "Afternoon Caffeine Cutoff (2:00 PM)", "Reducing late-day caffeine helps protect deep sleep architecture", Icons.Default.Timer),
                    DashboardStackItem("sleep_nsdr", "10 Min NSDR / Guided Rest", "Recharges mental energy and calms nervous system", Icons.Default.SelfImprovement, hasTimer = true, defaultMinutes = 10)
                ),
                eveningCaption = "Protect natural melatonin production and prepare for restorative sleep.",
                eveningItems = listOf(
                    DashboardStackItem("sleep_blue_light", "Dim Overhead Lighting (2h Pre-Bed)", "Minimize bright artificial illumination to support sleep onset", Icons.Default.Nightlight),
                    DashboardStackItem("sleep_magnesium", "Evening Relaxation Nutrition", "Magnesium to support neuromuscular calm and quiet the mind", Icons.Default.Bedtime, hasInfo = true),
                    DashboardStackItem("sleep_temp", "Cool Bedroom (18°C / 65°F)", "Supports the natural core temperature decline associated with restful sleep", Icons.Default.DeviceThermostat)
                ),
                focusTagline = "Optimized for Restorative Sleep Architecture & Daily Rhythm"
            )
        }

        fun adaptItem(item: DashboardStackItem): DashboardStackItem {
            val isTargetPriority = priorityId != null && item.id == priorityId
            val scaledMinutes = adaptiveState.getScaledMinutes(item.id, item.defaultMinutes)
            return item.copy(
                defaultMinutes = scaledMinutes,
                isPriority = isTargetPriority,
                priorityTag = if (isTargetPriority) priorityLabel else null
            )
        }

        rawPlan.copy(
            morningItems = rawPlan.morningItems.map { adaptItem(it) },
            middayItems = rawPlan.middayItems.map { adaptItem(it) },
            eveningItems = rawPlan.eveningItems.map { adaptItem(it) }
        )
    }

    val morningItems = activePlan.morningItems
    val middayItems = activePlan.middayItems
    val eveningItems = activePlan.eveningItems

    val todayDateFormatted = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()).uppercase()
    }

    val unreadCount = notificationLogs.count { !it.isRead }

    if (showBreathPacer) {
        BreathPacerScreen(onClose = { showBreathPacer = false })
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        val isCompact = maxWidth < 380.dp
        val horizontalPadding = if (isCompact) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val userEmail = userProfile?.email
                    val hasUser = !userEmail.isNullOrBlank()
                    val isGoogle = userEmail?.endsWith("@gmail.com", ignoreCase = true) == true
                    val displayName = if (hasUser) (userEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Member") else "Member"

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = todayDateFormatted,
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.7.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasUser) "Hello, $displayName" else "Good morning.",
                            color = palette.foreground,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // User Account / Google Identity Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(15.dp))
                                .border(1.dp, if (hasUser) palette.accent.copy(alpha = 0.5f) else palette.border, RoundedCornerShape(15.dp))
                                .background(palette.surface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Button
                                ) {
                                    triggerHaptic(context, 0)
                                    currentTab = "account"
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isGoogle) {
                                    GoogleLogoIcon(sizeDp = 16.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Profile",
                                        tint = if (hasUser) palette.accent else palette.mutedForeground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasUser) displayName else "Sign In",
                                    color = if (hasUser) palette.foreground else palette.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Notifications
                        Box(
                            modifier = Modifier
                                .size(43.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .border(1.dp, palette.border, RoundedCornerShape(15.dp))
                                .background(palette.surface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Button
                                ) {
                                    triggerHaptic(context, 0)
                                    showNotificationCenter = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = if (unreadCount > 0) palette.accent else palette.mutedForeground,
                                modifier = Modifier.size(19.dp)
                            )
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp)
                                        .background(palette.accent, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Settings
                        Box(
                            modifier = Modifier
                                .size(43.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .border(1.dp, palette.border, RoundedCornerShape(15.dp))
                                .background(palette.surface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Button
                                ) {
                                    triggerHaptic(context, 0)
                                    showSettings = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = palette.mutedForeground,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                when (currentTab) {
                    "protocols" -> {
                        // Theme Mode Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VIEW MODE",
                        color = palette.mutedForeground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ThemeOptionButton(
                            icon = Icons.Default.DarkMode,
                            label = "Dark",
                            isSelected = activeThemeMode == ThemeMode.DARK,
                            onClick = {
                                triggerHaptic(context, 0)
                                onThemeChanged(ThemeMode.DARK)
                            }
                        )
                        ThemeOptionButton(
                            icon = Icons.Default.LightMode,
                            label = "Light",
                            isSelected = activeThemeMode == ThemeMode.LIGHT,
                            onClick = {
                                triggerHaptic(context, 0)
                                onThemeChanged(ThemeMode.LIGHT)
                            }
                        )
                        ThemeOptionButton(
                            icon = Icons.Default.Palette,
                            label = "Cozy",
                            isSelected = activeThemeMode == ThemeMode.COZY,
                            onClick = {
                                triggerHaptic(context, 0)
                                onThemeChanged(ThemeMode.COZY)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rhythm & Streak Card (Clickable to Share)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .clip(RoundedCornerShape(23.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(23.dp))
                        .background(palette.surface)
                        .clickable {
                            triggerHaptic(context, 0)
                            showStreakShare = true
                        }
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "YOUR RHYTHM",
                                    color = palette.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.8.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (realStreak) {
                                        0 -> "0 day streak"
                                        1 -> "1 day streak"
                                        else -> "$realStreak day streak"
                                    },
                                    color = palette.foreground,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(palette.accentSoft)
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (realStreak > 0) "ON TRACK" else "START TODAY",
                                    color = palette.accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 7-day strip with real completed dates from database
                        CalendarStripView(completedDates = allCompletedDates.toSet())
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-Time 24-Hour Circadian Biological Clock Curve
                CircadianCurveCard(
                    wakeTime = userProfile?.wakeTime ?: "06:00",
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Circadian Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pill 1: Caffeine Lockout
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(17.dp))
                            .background(palette.surface)
                            .clickable {
                                triggerHaptic(context, 0)
                                showCircadianTransparencyDialog = true
                            }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Coffee,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CAFFEINE LOCKOUT",
                                    color = palette.mutedForeground,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = circadianState.caffeineCountdownLabel,
                                    color = palette.foreground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Wake + ${circadianState.caffeineOffsetMinutes}m • General guidance",
                                    color = palette.mutedForeground,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }

                    // Pill 2: Sunlight Window
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(17.dp))
                            .background(palette.surface)
                            .clickable {
                                triggerHaptic(context, 0)
                                activeTimerItem = DashboardStackItem(
                                    id = "sunlight",
                                    title = "10 Min Direct Sunlight",
                                    subtitle = "Before screens or caffeine",
                                    icon = Icons.Default.WbSunny,
                                    hasTimer = true,
                                    defaultMinutes = 10
                                )
                            }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SUNLIGHT WINDOW",
                                    color = palette.mutedForeground,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = circadianState.luxWindowLabel,
                                    color = palette.foreground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Wake + ${circadianState.lightOffsetMinutes}m • General guidance",
                                    color = palette.mutedForeground,
                                    fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(palette.surfaceRaised)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(circadianState.luxProgress)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(palette.accent)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Wearable Biometric Telemetry & Circadian Sync Hub
                LiveWearableHUDCard(
                    viewModel = viewModel,
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Active Biological Track Switcher
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE BIOLOGICAL TRACK",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )

                        Text(
                            text = "PRO ALL-ACCESS",
                            color = palette.mutedForeground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal scrolling row of track pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        protocolTracks.forEach { track ->
                            val isSelected = currentFocus.equals(track.id, ignoreCase = true) ||
                                (track.id == "Physical Recovery" && currentFocus.contains("Recovery", ignoreCase = true)) ||
                                (track.id == "Mental Clarity" && currentFocus.contains("Mental", ignoreCase = true)) ||
                                (track.id == "Deep Sleep" && currentFocus.contains("Sleep", ignoreCase = true)) ||
                                (track.id == "All Stacks" && currentFocus.contains("All", ignoreCase = true))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) palette.accent else palette.surface)
                                    .border(
                                        1.dp,
                                        if (isSelected) palette.accent else palette.border,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.RadioButton
                                    ) {
                                        triggerHaptic(context, 1)
                                        viewModel.updateFocus(track.id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = track.icon,
                                    contentDescription = track.title,
                                    tint = if (isSelected) palette.accentForeground else palette.mutedForeground,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = track.title,
                                    color = if (isSelected) palette.accentForeground else palette.foreground,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = activePlan.focusTagline,
                        color = palette.mutedForeground,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Closed-Loop Adaptive Protocol Engine Feedback Loop (Requirement 18)
                AdaptiveEngineFeedbackCard(
                    adaptiveState = adaptiveState,
                    horizontalPadding = horizontalPadding
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Morning Ignition Stack
                ProtocolStackCard(
                    title = activePlan.morningTitle,
                    caption = activePlan.morningCaption,
                    items = morningItems,
                    completedItems = completedItems,
                    horizontalPadding = horizontalPadding,
                    onToggle = { itemId ->
                        triggerHaptic(context, 1)
                        viewModel.toggleProtocolItem(itemId)
                        // Check if all morning items are now completed
                        val nextCompleted = completedItems + itemId
                        if (morningItems.all { nextCompleted.contains(it.id) }) {
                            triggerHaptic(context, 4) // Success burst
                            showMilestoneCelebration = true
                        }
                    },
                    onInfoClick = {},
                    onTimerClick = { item ->
                        triggerHaptic(context, 0)
                        activeTimerItem = item
                    }
                )

                if (middayItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Midday Synchronization Stack
                    ProtocolStackCard(
                        title = activePlan.middayTitle,
                        caption = activePlan.middayCaption,
                        items = middayItems,
                        completedItems = completedItems,
                        horizontalPadding = horizontalPadding,
                        onToggle = { itemId ->
                            triggerHaptic(context, 1)
                            viewModel.toggleProtocolItem(itemId)
                        },
                        onInfoClick = {},
                        onTimerClick = { item ->
                            triggerHaptic(context, 0)
                            activeTimerItem = item
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Evening Recovery Stack
                ProtocolStackCard(
                    title = activePlan.eveningTitle,
                    caption = activePlan.eveningCaption,
                    items = eveningItems,
                    completedItems = completedItems,
                    horizontalPadding = horizontalPadding,
                    onToggle = { itemId ->
                        triggerHaptic(context, 1)
                        viewModel.toggleProtocolItem(itemId)
                    },
                    onInfoClick = { _ ->
                        triggerHaptic(context, 0)
                        showScienceNote = true
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Tactical Breath Pacer Initiation Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .clip(RoundedCornerShape(21.dp))
                        .border(1.dp, palette.accent, RoundedCornerShape(21.dp))
                        .background(palette.accentSoft)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            showBreathPacer = true
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = palette.accentForeground,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Calm Your Mind & Body",
                                color = palette.foreground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Guided 4 × 4 box breathing exercise",
                                color = palette.mutedForeground,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Start Breath Pacer",
                            tint = palette.accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            "biometrics" -> {
                BiometricsScreenContent(
                    viewModel = viewModel,
                    horizontalPadding = horizontalPadding
                )
            }
            "account" -> {
                AccountScreenContent(
                    viewModel = viewModel,
                    activeThemeMode = activeThemeMode,
                    onThemeChanged = onThemeChanged,
                    onResetBaseline = onResetBaseline,
                    onOpenAuth = onOpenAuth,
                    horizontalPadding = horizontalPadding
                )
            }
        }
    }

    // Modern Bottom Navigation Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Default.Bolt,
            label = "Protocols",
            isSelected = currentTab == "protocols",
            onClick = {
                triggerHaptic(context, 0)
                currentTab = "protocols"
            }
        )
        BottomNavItem(
            icon = Icons.Default.Watch,
            label = "Biometrics",
            isSelected = currentTab == "biometrics",
            onClick = {
                triggerHaptic(context, 0)
                currentTab = "biometrics"
            }
        )
        BottomNavItem(
            icon = Icons.Default.Person,
            label = "Account",
            isSelected = currentTab == "account",
            onClick = {
                triggerHaptic(context, 0)
                currentTab = "account"
            }
        )
    }
}
}

    // Modals
    if (showCircadianTransparencyDialog) {
        com.example.ui.components.CircadianTransparencyDialog(
            circadianState = circadianState,
            onDismiss = { showCircadianTransparencyDialog = false },
            onSaveOffsets = { caff, light, wind ->
                viewModel.updateCircadianOffsets(caff, light, wind)
            }
        )
    }

    if (showScienceNote) {
        ScienceNoteDialog(onDismiss = { showScienceNote = false })
    }

    if (showNotificationCenter) {
        NotificationCenterDialog(
            viewModel = viewModel,
            onDismiss = { showNotificationCenter = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onResetBaseline = {
                showSettings = false
                onResetBaseline()
            }
        )
    }

    // Interactive Timer Overlay Dialog for Sunlight & Cardio
    activeTimerItem?.let { timerItem ->
        com.example.ui.components.TaskSessionTimerDialog(
            taskTitle = timerItem.title,
            initialMinutes = timerItem.defaultMinutes,
            onCompleted = {
                viewModel.toggleProtocolItem(timerItem.id)
                activeTimerItem = null
            },
            onDismiss = {
                activeTimerItem = null
            }
        )
    }

    // Viral Share Sheet Card Dialog
    if (showStreakShare) {
        com.example.ui.components.StreakShareDialog(
            streakDays = realStreak,
            completedRatio = "${completedItems.size}/${morningItems.size + middayItems.size + eveningItems.size}",
            onDismiss = { showStreakShare = false }
        )
    }

    // Milestone Celebration Dialog
    if (showMilestoneCelebration) {
        com.example.ui.components.MilestoneCelebrationDialog(
            onDismiss = { showMilestoneCelebration = false },
            onShare = {
                showStreakShare = true
            }
        )
    }

    // Top-floating In-App Message Banner
    com.example.ui.components.ProtocolInAppMessageBanner(
        message = activeInAppMessage,
        onActionClick = { iam ->
            triggerHaptic(context, 1)
            viewModel.handleInAppMessageAction(iam)
            if (iam.actionRoute == "PACER") {
                showBreathPacer = true
            } else if (iam.actionRoute == "TIMER" && iam.targetTaskId != null) {
                val found = morningItems.find { it.id == iam.targetTaskId }
                    ?: middayItems.find { it.id == iam.targetTaskId }
                    ?: eveningItems.find { it.id == iam.targetTaskId }
                activeTimerItem = found
            }
        },
        onDismiss = {
            viewModel.dismissInAppMessage()
        }
    )
}

@Composable
private fun ThemeOptionButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = ProtocolTheme.palette
    Box(
        modifier = Modifier
            .size(width = 33.dp, height = 30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) palette.accent else palette.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) palette.accentForeground else palette.mutedForeground,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun CalendarStripView(completedDates: Set<String> = emptySet()) {
    val palette = ProtocolTheme.palette
    val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = sdf.format(c.time)
            val dayOfWeekInitial = dayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
            val dayNum = String.format(Locale.US, "%02d", c.get(Calendar.DAY_OF_MONTH))
            val isToday = i == 0
            val isCompleted = completedDates.contains(dateKey)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dayOfWeekInitial,
                    color = if (isToday) palette.accent else palette.faintForeground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(if (isToday) 32.dp else 28.dp)
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            if (isCompleted) palette.accent else palette.border,
                            CircleShape
                        )
                        .background(if (isCompleted) palette.accent else palette.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = palette.accentForeground,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = dayNum,
                            color = palette.mutedForeground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolStackCard(
    title: String,
    caption: String,
    items: List<DashboardStackItem>,
    completedItems: Set<String>,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    onToggle: (String) -> Unit,
    onInfoClick: (String) -> Unit,
    onTimerClick: (DashboardStackItem) -> Unit = {}
) {
    val palette = ProtocolTheme.palette
    val completedCount = items.count { completedItems.contains(it.id) }

    Column(modifier = Modifier.padding(horizontal = horizontalPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = title,
                    color = palette.foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.35).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = caption,
                    color = palette.mutedForeground,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "$completedCount/${items.size}",
                color = palette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(21.dp))
                .border(1.dp, palette.border, RoundedCornerShape(21.dp))
                .background(palette.surface)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    val isDone = completedItems.contains(item.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Checkbox
                            ) {
                                onToggle(item.id)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (isDone) palette.accentSoft else palette.surfaceRaised),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isDone) palette.accent else palette.mutedForeground,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    color = if (isDone) palette.faintForeground else palette.foreground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (item.isPriority) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.accentSoft)
                                            .border(1.dp, palette.accent, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.priorityTag ?: "ADAPTIVE PRIORITY",
                                            color = palette.accent,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.subtitle,
                                color = palette.mutedForeground,
                                fontSize = 11.sp
                            )
                        }

                        if (item.hasTimer) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(palette.surfaceRaised)
                                    .border(1.dp, palette.border, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        onTimerClick(item)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer",
                                    tint = palette.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (item.hasInfo) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, palette.border, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        role = Role.Button
                                    ) {
                                        onInfoClick(item.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = palette.mutedForeground,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(
                                    1.5.dp,
                                    if (isDone) palette.accent else palette.border,
                                    CircleShape
                                )
                                .background(if (isDone) palette.accent else palette.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Checked",
                                    tint = palette.accentForeground,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (index < items.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(palette.border)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) palette.accent else palette.mutedForeground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) palette.foreground else palette.mutedForeground,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun AdaptiveEngineFeedbackCard(
    adaptiveState: com.example.data.adaptive.AdaptiveProtocolState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette
    val yesterday = adaptiveState.yesterdaySummary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .border(1.2.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header: Title and Difficulty Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Adaptive Engine",
                            tint = palette.accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "ADAPTIVE PROTOCOL ENGINE",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = adaptiveState.headline,
                            color = palette.foreground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = adaptiveState.difficulty.badge,
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feedback Loop 3-Metric Overview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YESTERDAY",
                        color = palette.mutedForeground,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${yesterday.completedCount}/${yesterday.totalRequired} (${yesterday.adherencePercentage}%)",
                        color = if (yesterday.isStreakQualified) palette.success else palette.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOAD STATUS",
                        color = palette.mutedForeground,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = adaptiveState.difficulty.title,
                        color = palette.foreground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DURATION SCALING",
                        color = palette.mutedForeground,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val scalingText = when (adaptiveState.difficulty) {
                        com.example.data.adaptive.AdaptiveDifficulty.DELOAD_MICRO_ANCHOR -> "-35% (Friction Deload)"
                        com.example.data.adaptive.AdaptiveDifficulty.BALANCED_CALIBRATION -> "1.0x (Standard)"
                        com.example.data.adaptive.AdaptiveDifficulty.PROGRESSIVE_OVERLOAD -> "+35% (Progressive)"
                    }
                    Text(
                        text = scalingText,
                        color = palette.foreground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (adaptiveState.threeDayAdherence != null || adaptiveState.sevenDayAdherence != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3-DAY: ${adaptiveState.threeDayAdherence?.let { "${(it * 100).toInt()}%" } ?: "--"}",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "7-DAY: ${adaptiveState.sevenDayAdherence?.let { "${(it * 100).toInt()}%" } ?: "--"}",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "PROTOCOL SCORE: ${adaptiveState.protocolScore}",
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rationale explanation
            Text(
                text = adaptiveState.rationale,
                color = palette.mutedForeground,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tomorrow's Dynamic Projection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tomorrow: ${adaptiveState.tomorrowProjection}",
                    color = palette.mutedForeground,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}


