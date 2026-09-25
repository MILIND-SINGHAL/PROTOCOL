package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import com.example.data.firebase.FirebaseSyncStatus
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.ProtocolViewModel

private fun Context.findActivity(): Activity? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

@Composable
fun PaywallScreen(
    viewModel: ProtocolViewModel,
    onSubscribed: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    BackHandler {
        if (onDismiss != null) {
            onDismiss()
        } else {
            viewModel.handlePaywallDismiss()
        }
    }

    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val isPurchasing by viewModel.revenueCatManager.isPurchasing.collectAsState()
    val errorMsg by viewModel.revenueCatManager.lastError.collectAsState()
    val activeOfferingId by viewModel.revenueCatManager.activeOfferingId.collectAsState()
    val remoteOfferings by viewModel.revenueCatManager.remoteOfferings.collectAsState()

    // Plan package IDs and store pricing
    val annualPackageId = if (activeOfferingId == "shipaton_experiment_offer" && viewModel.revenueCatManager.findExactPackage("\$rc_annual_promo") != null) {
        "\$rc_annual_promo"
    } else {
        "\$rc_annual"
    }
    val weeklyPackageId = "\$rc_weekly"

    val annualPrice = viewModel.revenueCatManager.getFormattedPrice(annualPackageId)
    val weeklyPrice = viewModel.revenueCatManager.getFormattedPrice(weeklyPackageId)

    val annualMonthlyPrice = viewModel.revenueCatManager.getFormattedPricePerMonth(annualPackageId)
    val annualSub = if (activeOfferingId == "shipaton_experiment_offer") {
        if (annualMonthlyPrice != null) "Executive tier ($annualMonthlyPrice/mo)" else "Executive tier"
    } else {
        if (annualMonthlyPrice != null) "Includes 4-day free trial ($annualMonthlyPrice/mo)" else "Includes 4-day free trial"
    }

    var selectedPlan by remember { mutableStateOf("annual") } // "annual" or "weekly"
    var showComparisonMatrix by remember { mutableStateOf(false) }
    var showPrivacyModal by remember { mutableStateOf(false) }

    // Account & Trial Protection Gate State
    val userProfile by viewModel.userProfile.collectAsState()
    var showAuthGateModal by remember { mutableStateOf(false) }
    var showTrialUsedWarningModal by remember { mutableStateOf(false) }
    var pendingPaymentAction by remember { mutableStateOf<String?>(null) }

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
                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDismiss != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceRaised)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                role = Role.Button
                            ) {
                                triggerHaptic(context, 0)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button
                        ) {
                            triggerHaptic(context, 1)
                            viewModel.restoreSubscription(onSuccess = {
                                triggerHaptic(context, 4)
                                onSubscribed()
                            })
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Restore",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Shield Lock Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield Lock",
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "YOUR BASELINE IS READY",
                color = palette.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Unlock your\noptimized protocol.",
                color = palette.foreground,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.1).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Protocol turns your inputs into a daily operating system for energy, focus, and recovery.",
                color = palette.mutedForeground,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1 Subscription = All Tracks Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.accentSoft)
                    .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POWERED BY REVENUECAT • ALL 4 TRACKS UNLOCKED",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User Profile Link & Trial Status Indicator
            val activeEmail = userProfile?.email?.trim().orEmpty()
            val isAuthenticated = activeEmail.isNotEmpty() && activeEmail.contains("@")
            val hasUsedTrial = userProfile?.isPro == true || (userProfile?.subscriptionPlan != null && userProfile?.subscriptionPlan != "free")

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button
                    ) {
                        triggerHaptic(context, 0)
                        showAuthGateModal = true
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAuthenticated) Icons.Default.CheckCircle else Icons.Default.Person,
                    contentDescription = "User Identity",
                    tint = if (isAuthenticated) palette.accent else palette.mutedForeground,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAuthenticated) "Authenticated: $activeEmail" else "Sign in required before trial activation",
                    color = if (isAuthenticated) palette.foreground else palette.mutedForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAuthenticated) "(Change)" else "(Sign In)",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4-DAY TRIAL JOURNEY TIMELINE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "HOW YOUR 4-DAY TRIAL WORKS",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", color = palette.accentForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Today: Instant Access", color = palette.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Unlock custom circadian stacks, timers, and breath pacer.", color = palette.mutedForeground, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = palette.accentForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Day 3: Friendly Reminder", color = palette.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Local push alert notifies you that trial has 1 day left.", color = palette.mutedForeground, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", color = palette.accentForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Day 4: Billing Begins", color = palette.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Subscription activates. Cancel anytime prior in settings.", color = palette.mutedForeground, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Plan A: Annual
            PlanCard(
                title = if (activeOfferingId == "shipaton_experiment_offer") "Executive Annual" else "Annual Protocol",
                subtitle = annualSub,
                price = annualPrice,
                frequency = "per year",
                badgeText = if (activeOfferingId == "shipaton_experiment_offer") "25% FOUNDER SAVINGS" else "BEST VALUE • SAVE 50%",
                isSelected = selectedPlan == "annual",
                onClick = {
                    triggerHaptic(context, 0)
                    selectedPlan = "annual"
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Plan B: Weekly
            PlanCard(
                title = "Weekly protocol",
                subtitle = "Flexible, cancel anytime",
                price = weeklyPrice,
                frequency = "per week",
                badgeText = null,
                isSelected = selectedPlan == "weekly",
                onClick = {
                    triggerHaptic(context, 0)
                    selectedPlan = "weekly"
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Free vs Pro Feature Matrix Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        triggerHaptic(context, 0)
                        showComparisonMatrix = !showComparisonMatrix
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (showComparisonMatrix) "Hide Feature Matrix ▲" else "Compare Free vs. Pro Matrix ▼",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showComparisonMatrix) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("FEATURE", color = palette.mutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text("FREE", color = palette.mutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text("PRO", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        MatrixRowItem("Protocol Tracks", "1 Single Track", "All 4 Tracks Unlocked")
                        MatrixRowItem("Real-Time Circadian", "Basic", "Precision")
                        MatrixRowItem("Adjustable Timers", "Standard", "Full Range + Haptic")
                        MatrixRowItem("Tactile Breath Pacer", "Limited", "Uncapped")
                        MatrixRowItem("Rhythm Streak History", "3 Days", "Unlimited")
                        MatrixRowItem("Circadian Push Engine", "Manual", "Automated Local Push")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Trust row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = palette.success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Personalized daily stacks",
                    color = palette.mutedForeground,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = palette.success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "No commitment",
                    color = palette.mutedForeground,
                    fontSize = 12.sp
                )
            }

            // Error banner if any
            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, palette.danger.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .background(palette.surfaceRaised)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = palette.danger,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMsg ?: "",
                        color = palette.mutedForeground,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Cancel anytime in Play Store subscriptions settings.",
                color = palette.faintForeground,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Official RevenueCat Verification Badge
            val isLiveConnected by viewModel.revenueCatManager.isLiveConnected.collectAsState()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isLiveConnected) palette.success else palette.accent, CircleShape)
                )
                Text(
                    text = if (isLiveConnected) "REVENUECAT SDK v8.12 • LIVE" else "REVENUECAT SDK v8.12 • SANDBOX READY",
                    color = palette.mutedForeground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom action button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val selectedPackageId = if (selectedPlan == "annual") annualPackageId else weeklyPackageId
            val selectedPrice = if (selectedPlan == "annual") annualPrice else weeklyPrice
            val isPriceLoading = selectedPrice == "Loading price…"
            val isPackageUnavailable = selectedPrice == "Unavailable"

            ProtocolPrimaryButton(
                text = if (isPurchasing) {
                    "Confirming..."
                } else if (isPriceLoading) {
                    "Loading price…"
                } else if (isPackageUnavailable) {
                    "Unavailable"
                } else if (selectedPlan == "annual") {
                    "Start 4-Day Free Trial"
                } else {
                    "Subscribe"
                },
                enabled = !isPurchasing && !isPriceLoading && !isPackageUnavailable,
                onClick = {
                    val email = userProfile?.email?.trim().orEmpty()
                    if (email.isEmpty() || !email.contains("@")) {
                        // User must create account or sign in first
                        triggerHaptic(context, 1)
                        pendingPaymentAction = "trial"
                        showAuthGateModal = true
                        return@ProtocolPrimaryButton
                    }

                    // Check for trial reuse abuse
                    if (selectedPlan == "annual" && (userProfile?.isPro == true || (userProfile?.subscriptionPlan != null && userProfile?.subscriptionPlan != "free"))) {
                        // Trial was already consumed by this customer account
                        triggerHaptic(context, 5)
                        showTrialUsedWarningModal = true
                        return@ProtocolPrimaryButton
                    }

                    val packageId = selectedPackageId
                    viewModel.purchasePlan(activity = activity, packageId = packageId, onSuccess = onSubscribed)
                },
                trailingIcon = {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            color = palette.accentForeground,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Unlock",
                            tint = palette.accentForeground,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Google Play & Shipathon Legal Transparency Links
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terms of Service",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        triggerHaptic(context, 0)
                        showPrivacyModal = true
                    }
                )
                Text(
                    text = "•",
                    color = palette.faintForeground,
                    fontSize = 10.sp
                )
                Text(
                    text = "Privacy Policy",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        triggerHaptic(context, 0)
                        showPrivacyModal = true
                    }
                )
                Text(
                    text = "•",
                    color = palette.faintForeground,
                    fontSize = 10.sp
                )
                Text(
                    text = "Restore",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        triggerHaptic(context, 1)
                        viewModel.restoreSubscription(onSuccess = {
                            triggerHaptic(context, 4)
                            onSubscribed()
                        })
                    }
                )
            }
        }
    }
    }

    if (showPrivacyModal) {
        com.example.ui.components.PrivacyAndLegalDialog(
            viewModel = viewModel,
            onDismiss = { showPrivacyModal = false }
        )
    }

    // MANDATORY ACCOUNT CREATION / SIGN IN MODAL
    if (showAuthGateModal) {
        PaywallAuthGateDialog(
            viewModel = viewModel,
            onSuccess = { authenticatedEmail ->
                showAuthGateModal = false
                triggerHaptic(context, 4)
                // Resume previous action if requested
                when (pendingPaymentAction) {
                    "trial" -> {
                        pendingPaymentAction = null
                        if (selectedPlan == "annual" && (userProfile?.isPro == true || (userProfile?.subscriptionPlan != null && userProfile?.subscriptionPlan != "free"))) {
                            showTrialUsedWarningModal = true
                        } else {
                            val packageId = if (selectedPlan == "annual") "\$rc_annual" else "\$rc_weekly"
                            viewModel.purchasePlan(activity = activity, packageId = packageId, onSuccess = onSubscribed)
                        }
                    }
                    else -> {
                        pendingPaymentAction = null
                    }
                }
            },
            onDismiss = {
                showAuthGateModal = false
                pendingPaymentAction = null
            }
        )
    }

    // TRIAL REUSE PREVENTION WARNING DIALOG
    if (showTrialUsedWarningModal) {
        TrialAlreadyClaimedDialog(
            email = userProfile?.email.orEmpty(),
            onProceedRegular = {
                showTrialUsedWarningModal = false
                // Proceed with direct paid subscription without free trial
                viewModel.purchasePlan(activity = activity, packageId = "\$rc_weekly", onSuccess = onSubscribed)
            },
            onUseDifferentAccount = {
                showTrialUsedWarningModal = false
                showAuthGateModal = true
            },
            onDismiss = { showTrialUsedWarningModal = false }
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    frequency: String,
    badgeText: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                1.5.dp,
                if (isSelected) palette.accent else palette.border,
                RoundedCornerShape(22.dp)
            )
            .background(if (isSelected) palette.accentSoft else palette.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(18.dp)
    ) {
        Column {
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = palette.accentForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = palette.foreground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = palette.mutedForeground,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price,
                        color = palette.foreground,
                        fontSize = if (price.length > 9) 13.sp else 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = frequency,
                        color = palette.mutedForeground,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixRowItem(feature: String, freeVal: String, proVal: String) {
    val palette = ProtocolTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            color = palette.foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = freeVal,
            color = palette.mutedForeground,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = proVal,
            color = palette.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PaywallAuthGateDialog(
    viewModel: ProtocolViewModel,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    var isSignUp by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceRaised)
                    .border(1.2.dp, palette.border, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent dismiss */ }
                    .padding(22.dp)
            ) {
                // Header Row
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
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MANDATORY VERIFICATION",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isSignUp) "Create Account" else "Sign In to Continue",
                                color = palette.foreground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.surface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "To activate your 4-day trial or membership, link your subscription to a verified email. This locks in your progress and guarantees your trial terms.",
                    color = palette.mutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle tabs: Create Account vs Sign In
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSignUp) palette.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isSignUp = true
                                errorMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Account",
                            color = if (isSignUp) palette.foreground else palette.mutedForeground,
                            fontSize = 12.sp,
                            fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSignUp) palette.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isSignUp = false
                                errorMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            color = if (!isSignUp) palette.foreground else palette.mutedForeground,
                            fontSize = 12.sp,
                            fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = palette.mutedForeground, modifier = Modifier.size(16.dp))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = palette.foreground,
                            unfocusedTextColor = palette.foreground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = palette.mutedForeground, modifier = Modifier.size(16.dp))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.foreground,
                        unfocusedTextColor = palette.foreground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = palette.mutedForeground, modifier = Modifier.size(16.dp))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.foreground,
                        unfocusedTextColor = palette.foreground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = palette.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                ProtocolPrimaryButton(
                    text = if (isSubmitting) "Verifying..." else if (isSignUp) "Create & Continue" else "Sign In & Continue",
                    enabled = !isSubmitting,
                    onClick = {
                        val cleanEmail = email.trim()
                        val cleanPass = password.trim()
                        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
                            errorMessage = "Please enter a valid email address."
                            return@ProtocolPrimaryButton
                        }
                        if (cleanPass.length < 4) {
                            errorMessage = "Password must be at least 4 characters."
                            return@ProtocolPrimaryButton
                        }

                        isSubmitting = true
                        errorMessage = null

                        if (isSignUp) {
                            viewModel.signUp(
                                emailInput = cleanEmail,
                                passwordInput = cleanPass,
                                nameInput = name.ifBlank { "User" },
                                onResult = { status, msg ->
                                    isSubmitting = false
                                    if (status == FirebaseSyncStatus.REAL_SUCCESS) {
                                        onSuccess(cleanEmail)
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            )
                        } else {
                            viewModel.signIn(
                                emailInput = cleanEmail,
                                passwordInput = cleanPass,
                                onResult = { status, msg ->
                                    isSubmitting = false
                                    if (status == FirebaseSyncStatus.REAL_SUCCESS) {
                                        onSuccess(cleanEmail)
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TrialAlreadyClaimedDialog(
    email: String,
    onProceedRegular: () -> Unit,
    onUseDifferentAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ProtocolTheme.palette

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceRaised)
                    .border(1.2.dp, palette.accent, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent dismiss */ }
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Trial Limit",
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.surface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "TRIAL LIMIT REACHED",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Free Trial Already Used",
                    color = palette.foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "The account $email has already redeemed a 4-day trial or active membership. In accordance with policy, each user is limited to one free trial period.",
                    color = palette.mutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                ProtocolPrimaryButton(
                    text = "Continue with Regular Subscription",
                    onClick = onProceedRegular
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .clickable { onUseDifferentAccount() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In With Another Account",
                        color = palette.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
