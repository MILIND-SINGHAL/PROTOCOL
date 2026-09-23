package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.FirebaseSyncStatus
import com.example.ui.components.GoogleAccountPickerDialog
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.ProtocolLogoMark
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.components.triggerHaptic
import com.example.ui.theme.ProtocolTheme
import com.example.viewmodel.ProtocolViewModel

@Composable
fun AuthScreen(
    viewModel: ProtocolViewModel,
    onAuthSuccess: () -> Unit,
    onSkipGuest: () -> Unit
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isSignUpTab by remember { mutableStateOf(true) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var agreeTerms by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackError by remember { mutableStateOf(false) }

    var showGooglePicker by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var forgotPasswordSent by remember { mutableStateOf(false) }
    var forgotPasswordError by remember { mutableStateOf<String?>(null) }
    var forgotPasswordMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Live Password Strength Calculation
    val passwordStrength = remember(passwordInput) {
        when {
            passwordInput.length < 4 -> 0 // None / Very Weak
            passwordInput.length in 4..7 -> 1 // Weak
            passwordInput.any { it.isDigit() } && passwordInput.any { it.isUpperCase() } -> 3 // Strong
            else -> 2 // Good
        }
    }

    fun performSubmit() {
        focusManager.clearFocus()
        isLoading = true
        feedbackMessage = null
        isFeedbackError = false

        if (isSignUpTab) {
            viewModel.signUp(
                emailInput = emailInput,
                passwordInput = passwordInput,
                nameInput = nameInput
            ) { status, message ->
                isLoading = false
                feedbackMessage = message
                if (status == FirebaseSyncStatus.ERROR) {
                    isFeedbackError = true
                    triggerHaptic(context, 5)
                } else {
                    isFeedbackError = false
                    triggerHaptic(context, 4)
                    onAuthSuccess()
                }
            }
        } else {
            viewModel.signIn(
                emailInput = emailInput,
                passwordInput = passwordInput
            ) { status, message ->
                isLoading = false
                feedbackMessage = message
                if (status == FirebaseSyncStatus.ERROR) {
                    isFeedbackError = true
                    triggerHaptic(context, 5)
                } else {
                    isFeedbackError = false
                    triggerHaptic(context, 4)
                    onAuthSuccess()
                }
            }
        }
    }

    // Google Account Picker Dialog (One Tap Flow)
    if (showGooglePicker) {
        GoogleAccountPickerDialog(
            defaultEmail = null,
            defaultName = null,
            onAccountSelected = { email, name ->
                showGooglePicker = false
                isGoogleLoading = true
                viewModel.signInWithGoogle(
                    googleEmail = email,
                    googleName = name
                ) { status, message ->
                    isGoogleLoading = false
                    feedbackMessage = message
                    if (status == FirebaseSyncStatus.ERROR) {
                        isFeedbackError = true
                        triggerHaptic(context, 5)
                    } else {
                        isFeedbackError = false
                        triggerHaptic(context, 4)
                        onAuthSuccess()
                    }
                }
            },
            onDismiss = { showGooglePicker = false }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        Dialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                forgotPasswordSent = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "Reset Password",
                        color = palette.foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter your registered email address and we'll send you an encrypted reset link.",
                        color = palette.mutedForeground,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    if (!forgotPasswordSent) {
                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = { forgotPasswordEmail = it },
                            label = { Text("Email address") },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (forgotPasswordError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = forgotPasswordError ?: "",
                                color = palette.danger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showForgotPasswordDialog = false
                                forgotPasswordError = null
                                forgotPasswordSent = false
                            }) {
                                Text("Cancel", color = palette.mutedForeground)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ProtocolPrimaryButton(
                                text = "Send Reset Link",
                                onClick = {
                                    if (forgotPasswordEmail.contains("@")) {
                                        forgotPasswordError = null
                                        viewModel.sendPasswordReset(forgotPasswordEmail) { status, msg ->
                                            when (status) {
                                                FirebaseSyncStatus.REAL_SUCCESS -> {
                                                    triggerHaptic(context, 4)
                                                    forgotPasswordMessage = msg
                                                    forgotPasswordSent = true
                                                    forgotPasswordError = null
                                                }
                                                FirebaseSyncStatus.OFFLINE_MODE, FirebaseSyncStatus.ERROR -> {
                                                    triggerHaptic(context, 5)
                                                    forgotPasswordError = msg
                                                    forgotPasswordSent = false
                                                }
                                            }
                                        }
                                    } else {
                                        forgotPasswordError = "Please enter a valid email address."
                                    }
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSoft)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = forgotPasswordMessage ?: "Password reset instructions sent to $forgotPasswordEmail. Please check your inbox.",
                                    color = palette.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                showForgotPasswordDialog = false
                                forgotPasswordSent = false
                                forgotPasswordError = null
                                forgotPasswordMessage = null
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Done", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.background,
                        palette.surfaceRaised.copy(alpha = 0.6f),
                        palette.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Back/Guest shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onSkipGuest
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explore as Guest",
                        color = palette.mutedForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SECURE ENCLAVE",
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brand Emblem & Title
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(12.dp, CircleShape, spotColor = palette.accent)
                    .clip(CircleShape)
                    .background(palette.surfaceRaised)
                    .border(1.5.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ProtocolLogoMark(sizeDp = 38)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PROTOCOL",
                color = palette.foreground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Autonomous Circadian & Physiological OS",
                color = palette.mutedForeground,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Professional Onboarding Trust Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "4-DAY FULL TRIAL INCLUDED",
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ZERO DATA BROKERAGE",
                        color = palette.mutedForeground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: GOOGLE SIGN IN (Google takes responsibility)
            GoogleSignInButton(
                text = if (isSignUpTab) "Sign up with Google" else "Continue with Google",
                subtitle = "Google handles authentication & protects your credentials",
                isLoading = isGoogleLoading,
                onClick = {
                    triggerHaptic(context, 1)
                    showGooglePicker = true
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Elegant Divider: "or continue with email"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = palette.border)
                Text(
                    text = "  or with email  ",
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = palette.border)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Selector: Sign In vs Create Account
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isSignUpTab) palette.surface else Color.Transparent)
                        .clickable {
                            isSignUpTab = false
                            feedbackMessage = null
                            triggerHaptic(context, 1)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        color = if (!isSignUpTab) palette.accent else palette.mutedForeground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSignUpTab) palette.surface else Color.Transparent)
                        .clickable {
                            isSignUpTab = true
                            feedbackMessage = null
                            triggerHaptic(context, 1)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account",
                        color = if (isSignUpTab) palette.accent else palette.mutedForeground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Input Fields Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                if (isSignUpTab) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("Your Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = palette.mutedForeground)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = palette.foreground,
                            unfocusedTextColor = palette.foreground,
                            focusedLabelColor = palette.accent,
                            unfocusedLabelColor = palette.mutedForeground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { 
                        emailInput = it 
                        feedbackMessage = null
                    },
                    label = { Text("Email address") },
                    placeholder = { Text("name@example.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = palette.mutedForeground)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.foreground,
                        unfocusedTextColor = palette.foreground,
                        focusedLabelColor = palette.accent,
                        unfocusedLabelColor = palette.mutedForeground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { 
                        passwordInput = it 
                        feedbackMessage = null
                    },
                    label = { Text("Password") },
                    placeholder = { Text("Minimum 4 characters") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = palette.mutedForeground)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = palette.mutedForeground
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.foreground,
                        unfocusedTextColor = palette.foreground,
                        focusedLabelColor = palette.accent,
                        unfocusedLabelColor = palette.mutedForeground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password strength meter on sign up
                if (isSignUpTab && passwordInput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            val active = passwordStrength > index
                            val color = when (passwordStrength) {
                                1 -> palette.danger
                                2 -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                else -> palette.accent
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (active) color else palette.border)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (passwordStrength) {
                                1 -> "Weak"
                                2 -> "Good"
                                3 -> "Strong"
                                else -> ""
                            },
                            color = when (passwordStrength) {
                                1 -> palette.danger
                                2 -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                else -> palette.accent
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Options Row: Remember Me & Forgot Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accent,
                                uncheckedColor = palette.mutedForeground
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remember me",
                            color = palette.foreground,
                            fontSize = 12.sp
                        )
                    }

                    if (!isSignUpTab) {
                        Text(
                            text = "Forgot password?",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    triggerHaptic(context, 0)
                                    forgotPasswordEmail = emailInput
                                    showForgotPasswordDialog = true
                                }
                                .padding(4.dp)
                        )
                    }
                }

                if (isSignUpTab) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { agreeTerms = !agreeTerms }
                    ) {
                        Checkbox(
                            checked = agreeTerms,
                            onCheckedChange = { agreeTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accent,
                                uncheckedColor = palette.mutedForeground
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I agree to Terms of Service & Privacy Policy",
                            color = palette.mutedForeground,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = feedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                feedbackMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFeedbackError) palette.danger.copy(alpha = 0.15f) else palette.accentSoft)
                            .border(1.dp, if (isFeedbackError) palette.danger else palette.accent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (isFeedbackError) palette.danger else palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Action Button
            if (isLoading) {
                CircularProgressIndicator(
                    color = palette.accent,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                ProtocolPrimaryButton(
                    text = if (isSignUpTab) "Create Account & Unlock OS" else "Sign In to Protocol",
                    onClick = { performSubmit() },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = palette.accentForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Continue as Guest link
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        triggerHaptic(context, 1)
                        onSkipGuest()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Continue without account (Guest Mode)",
                    color = palette.mutedForeground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
