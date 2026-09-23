package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.local.NotificationLogEntity
import com.example.data.local.OneSignalSettingsEntity
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.local.UserProfileEntity
import com.example.data.notification.NotificationCampaign
import com.example.data.notification.InAppNotificationMessage
import com.example.data.notification.ProtocolNotificationManager
import com.example.data.revenuecat.PurchaseResult
import com.example.data.revenuecat.RevenueCatManager
import com.example.ui.theme.ThemeMode
import com.revenuecat.purchases.CustomerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class CircadianTimingRule(
    val title: String,
    val ruleFormula: String,
    val scientificReason: String,
    val confidenceLevel: String = "General guidance"
)

data class CircadianState(
    val caffeineCountdownLabel: String = "Unlocked",
    val caffeineMinutesRemaining: Int = 0,
    val isCaffeineUnlocked: Boolean = true,
    val luxWindowLabel: String = "Window complete",
    val luxProgress: Float = 1.0f,
    val isLuxWindowActive: Boolean = false,
    val windDownTime: String = "20:30",
    val caffeineOffsetMinutes: Int = 90,
    val lightOffsetMinutes: Int = 60,
    val windDownOffsetHours: Int = 14,
    val caffeineRule: CircadianTimingRule = CircadianTimingRule(
        title = "Caffeine Intake Window",
        ruleFormula = "Wake time + 90 min (configurable offset)",
        scientificReason = "Allows natural adenosine clearance to prevent afternoon fatigue crashes.",
        confidenceLevel = "General guidance"
    ),
    val lightRule: CircadianTimingRule = CircadianTimingRule(
        title = "Morning Sunlight Window",
        ruleFormula = "Wake time + 60 min (configurable offset)",
        scientificReason = "Supports a consistent morning routine and entrains the central circadian clock.",
        confidenceLevel = "General guidance"
    ),
    val windDownRule: CircadianTimingRule = CircadianTimingRule(
        title = "Evening Wind-Down",
        ruleFormula = "Wake time + 14 hours (configurable offset)",
        scientificReason = "Prepares nervous system for nocturnal melatonin synthesis and core temperature drop.",
        confidenceLevel = "General guidance"
    )
)

data class AccountDeletionResult(
    val isRemoteSuccess: Boolean,
    val isLocalWipeSuccess: Boolean,
    val isSubscriptionReset: Boolean,
    val isNotificationReset: Boolean,
    val message: String
)

class ProtocolViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProtocolRepository
    val revenueCatManager = RevenueCatManager()
    val notificationManager: ProtocolNotificationManager
    val oneSignalManager: ProtocolNotificationManager get() = notificationManager
    val firebaseManager = com.example.data.firebase.FirebaseManager(application)

    val userProfile: StateFlow<UserProfileEntity?>
    val todayCompleted: StateFlow<Set<String>>
    val notificationLogs: StateFlow<List<NotificationLogEntity>>
    val oneSignalSettings: StateFlow<OneSignalSettingsEntity?>
    val realStreak: StateFlow<Int>
    val allCompletedDates: StateFlow<List<String>>
    val adaptiveProtocolState: StateFlow<com.example.data.adaptive.AdaptiveProtocolState>

    private val _circadianState = MutableStateFlow(CircadianState())
    val circadianState: StateFlow<CircadianState> = _circadianState.asStateFlow()

    private val _securityReport = MutableStateFlow<com.example.data.security.SecurityReport?>(null)
    val securityReport: StateFlow<com.example.data.security.SecurityReport?> = _securityReport.asStateFlow()

    private val _isGuestSession = MutableStateFlow(false)
    val isGuestSession: StateFlow<Boolean> = _isGuestSession.asStateFlow()

    private val _appNavState = MutableStateFlow<String>(AppNavDestination.Loading.route)
    val appNavState: StateFlow<String> = _appNavState.asStateFlow()

    fun continueAsGuest() {
        _isGuestSession.value = true
        resolveStartupDestination(allowGuest = true)
    }

    init {
        val db = ProtocolDatabase.getDatabase(application)
        repository = ProtocolRepository(db.protocolDao())
        notificationManager = ProtocolNotificationManager(application, repository)
        revenueCatManager.onCustomerInfoUpdated = { customerInfo ->
            applyRevenueCatCustomerInfo(customerInfo)
        }
        revenueCatManager.initialize(application)

        userProfile = repository.userProfileFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        todayCompleted = repository.getCompletionsForTodayFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

        notificationLogs = repository.notificationLogsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        oneSignalSettings = repository.oneSignalSettingsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        realStreak = repository.realStreakFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        allCompletedDates = repository.allCompletedDatesFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val yesterdayKey = repository.getYesterdayKey()
        val defaultSummary = com.example.data.adaptive.AdaptiveEngine.summarizeDay(
            dateKey = yesterdayKey,
            completedItemIds = emptySet(),
            requiredItems = com.example.data.adaptive.AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
        )
        val initialAdaptiveState = com.example.data.adaptive.AdaptiveEngine.evaluateAdaptation(defaultSummary)

        adaptiveProtocolState = repository.adaptiveProtocolStateFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialAdaptiveState)

        viewModelScope.launch {
            repository.ensureInitialized()
        }

        // Anti-tampering & environment security audit
        viewModelScope.launch {
            val report = com.example.data.security.SecurityIntegrityManager.auditAppEnvironment(application)
            _securityReport.value = report
            if (!report.isClean && report.isTampered) {
                // If binary tampering or package spoofing is detected, ensure role is clamped to Member
                userProfile.value?.let { profile ->
                    if (profile.accountRole != "Member") {
                        repository.setActiveUserSession(
                            firebaseUid = profile.firebaseUid,
                            email = profile.email ?: "",
                            displayName = profile.displayName,
                            isEmailVerified = profile.isEmailVerified,
                            role = "Member"
                        )
                    }
                }
            }
        }

        // Live circadian clock updater (runs every 10 seconds)
        viewModelScope.launch {
            while (isActive) {
                userProfile.value?.let { profile ->
                    updateCircadianMath(
                        wakeTime = profile.wakeTime,
                        caffeineOffset = profile.caffeineDelayMinutes,
                        lightOffset = profile.lightWindowMinutes,
                        windDownOffset = profile.windDownHours
                    )
                    oneSignalManager.syncCircadianTags(
                        wakeTime = profile.wakeTime,
                        focus = profile.focus,
                        completedCount = todayCompleted.value.size,
                        streakDays = realStreak.value,
                        wearable = profile.wearable
                    )
                }
                delay(10_000)
            }
        }
    }

    fun updateCircadianMath(
        wakeTime: String,
        caffeineOffset: Int = 90,
        lightOffset: Int = 60,
        windDownOffset: Int = 14
    ) {
        val parts = wakeTime.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return

        val wakeHours = parts[0]
        val wakeMinutes = parts[1]
        val wakeTotalMinutes = wakeHours * 60 + wakeMinutes

        val cal = Calendar.getInstance()
        val currentTotalMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 1. Caffeine Lockout: configurable minutes after wake time (default 90m)
        val caffeineTarget = wakeTotalMinutes + caffeineOffset
        val caffeineDelta = caffeineTarget - currentTotalMinutes

        val (caffeineLabel, isCaffUnlocked) = if (caffeineDelta <= 0) {
            "Unlocked" to true
        } else {
            val hours = caffeineDelta / 60
            val mins = caffeineDelta % 60
            val formatted = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            "Unlocks in $formatted" to false
        }

        // 2. Lux Window: 0 to configurable minutes after wake time (default 60m)
        val luxEnd = wakeTotalMinutes + lightOffset
        val luxRemaining = luxEnd - currentTotalMinutes
        val luxStartDelta = wakeTotalMinutes - currentTotalMinutes

        val (luxLabel, luxProgress, isLuxActive) = when {
            luxStartDelta > 0 -> {
                val hours = luxStartDelta / 60
                val mins = luxStartDelta % 60
                val formatted = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                Triple("Starts in $formatted", 0.0f, false)
            }
            luxRemaining > 0 -> {
                val progress = ((currentTotalMinutes - wakeTotalMinutes).toFloat() / lightOffset.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                Triple("${luxRemaining}m left", progress, true)
            }
            else -> {
                Triple("Window complete", 1.0f, false)
            }
        }

        // 3. Wind down: configurable hours after wake time (default 14h)
        val windDownTotal = (wakeTotalMinutes + windDownOffset * 60) % (24 * 60)
        val windDownStr = String.format("%02d:%02d", windDownTotal / 60, windDownTotal % 60)

        _circadianState.value = CircadianState(
            caffeineCountdownLabel = caffeineLabel,
            caffeineMinutesRemaining = caffeineDelta.coerceAtLeast(0),
            isCaffeineUnlocked = isCaffUnlocked,
            luxWindowLabel = luxLabel,
            luxProgress = luxProgress,
            isLuxWindowActive = isLuxActive,
            windDownTime = windDownStr,
            caffeineOffsetMinutes = caffeineOffset,
            lightOffsetMinutes = lightOffset,
            windDownOffsetHours = windDownOffset,
            caffeineRule = CircadianTimingRule(
                title = "Caffeine Intake Window",
                ruleFormula = "Wake time + ${caffeineOffset}m offset",
                scientificReason = "Allows natural adenosine clearance to prevent afternoon fatigue crashes.",
                confidenceLevel = "General guidance"
            ),
            lightRule = CircadianTimingRule(
                title = "Morning Sunlight Window",
                ruleFormula = "Wake time + ${lightOffset}m offset",
                scientificReason = "Supports a consistent morning routine and entrains the central circadian clock.",
                confidenceLevel = "General guidance"
            ),
            windDownRule = CircadianTimingRule(
                title = "Evening Wind-Down",
                ruleFormula = "Wake time + ${windDownOffset}h offset",
                scientificReason = "Prepares nervous system for nocturnal melatonin synthesis and core temperature drop.",
                confidenceLevel = "General guidance"
            )
        )
    }

    fun updateCircadianOffsets(caffeineDelayMin: Int, lightWindowMin: Int, windDownH: Int) {
        viewModelScope.launch {
            repository.updateCircadianOffsets(caffeineDelayMin, lightWindowMin, windDownH)
            userProfile.value?.let { profile ->
                updateCircadianMath(
                    wakeTime = profile.wakeTime,
                    caffeineOffset = caffeineDelayMin,
                    lightOffset = lightWindowMin,
                    windDownOffset = windDownH
                )
            }
        }
    }

    fun setNavDestination(dest: String) {
        _appNavState.value = dest
    }

    /**
     * Explicit startup navigation state machine:
     * App launch
     *   ↓
     * Loading / Splash
     *   ↓
     * Firebase session active?
     *   ├── No → Auth
     *   └── Yes
     *         ↓
     *   Baseline completed?
     *     ├── No → Baseline
     *     └── Yes
     *           ↓
     *   Active Pro Entitlement?
     *     ├── No → Paywall
     *     └── Yes → Dashboard
     */
    fun resolveStartupDestination(allowGuest: Boolean = false) {
        viewModelScope.launch {
            repository.ensureInitialized()
            val profile = repository.getUserProfile()
            val firebaseUser = firebaseManager.currentUser

            // Keep Room cache synchronized with Firebase identity
            if (firebaseUser != null && (profile?.firebaseUid != firebaseUser.uid || profile.email != firebaseUser.email || profile.isEmailVerified != firebaseUser.isEmailVerified)) {
                repository.setActiveUserSession(
                    firebaseUid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName,
                    isEmailVerified = firebaseUser.isEmailVerified
                )
            }

            // Firebase session is active if Firebase Auth currentUser != null, or guest mode was explicitly chosen
            val isSessionActive = firebaseUser != null || allowGuest || _isGuestSession.value

            if (!isSessionActive) {
                _appNavState.value = AppNavDestination.Auth.route
            } else if (profile == null || !profile.hasCompletedBaseline) {
                _appNavState.value = AppNavDestination.Baseline.route
            } else if (!profile.isPro) {
                _appNavState.value = AppNavDestination.Paywall.route
            } else {
                _appNavState.value = AppNavDestination.Dashboard.route
            }
        }
    }

    fun saveBaseline(wakeTime: String, focus: String, wearable: String) {
        viewModelScope.launch {
            repository.saveBaseline(wakeTime, focus, wearable)
            oneSignalManager.setTags(
                mapOf(
                    "wake_time" to wakeTime,
                    "focus_outcome" to focus,
                    "wearable" to wearable
                )
            )
            updateCircadianMath(wakeTime)
            resolveStartupDestination(allowGuest = true)
        }
    }

    fun updateFocus(newFocus: String) {
        viewModelScope.launch {
            repository.updateFocus(newFocus)
            oneSignalManager.setTags(mapOf("focus_outcome" to newFocus))
        }
    }

    fun toggleProtocolItem(itemId: String) {
        viewModelScope.launch {
            repository.toggleItem(itemId)
        }
    }

    fun setTheme(themeMode: ThemeMode) {
        val modeStr = when (themeMode) {
            ThemeMode.DARK -> "dark"
            ThemeMode.LIGHT -> "light"
            ThemeMode.COZY -> "cozy"
        }
        viewModelScope.launch {
            repository.updateTheme(modeStr)
        }
    }

    private fun applyRevenueCatCustomerInfo(customerInfo: CustomerInfo) {
        viewModelScope.launch {
            val proEntitlement = customerInfo.entitlements[RevenueCatManager.ENTITLEMENT_ID]
            val isProActive = proEntitlement?.isActive == true
            val plan = if (isProActive) (proEntitlement?.productIdentifier ?: "pro") else "free"

            repository.setSubscription(isPro = isProActive, plan = plan)
            oneSignalManager.setTags(
                mapOf(
                    "tier" to if (isProActive) "pro" else "free",
                    "plan" to plan
                )
            )
        }
    }

    fun purchasePlan(activity: Activity?, packageId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = revenueCatManager.purchasePackage(activity, packageId)
            if (result is PurchaseResult.Success) {
                // Room cache is kept strictly synchronized with RevenueCat entitlement status
                _appNavState.value = "dashboard"
                onSuccess()
            }
        }
    }

    fun restoreSubscription(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = revenueCatManager.restorePurchases()
            if (result is PurchaseResult.Success) {
                // Room cache is kept strictly synchronized with RevenueCat entitlement status
                _appNavState.value = "dashboard"
                onSuccess()
            }
        }
    }

    /**
     * Dedicated demo mode access. Strictly gated behind BuildConfig.DEMO_MODE (false in release).
     * Cannot ship in production.
     */
    fun activateDemoPassIfEnabled(): Boolean {
        if (BuildConfig.DEMO_MODE) {
            viewModelScope.launch {
                repository.setSubscription(isPro = true, plan = "demo_debug_mode")
            }
            return true
        }
        return false
    }

    fun resetSubscriptionToFree() {
        viewModelScope.launch {
            repository.setSubscription(isPro = false, plan = "free")
            oneSignalManager.setTags(mapOf("tier" to "free"))
        }
    }

    fun navigateToPaywall() {
        _appNavState.value = "paywall"
    }

    fun resetBaseline() {
        viewModelScope.launch {
            repository.resetBaseline()
            _appNavState.value = "baseline"
        }
    }

    fun updateWearable(wearable: String) {
        viewModelScope.launch {
            repository.updateWearable(wearable)
            oneSignalManager.setTags(mapOf("wearable_telemetry_source" to wearable))
        }
    }

    fun updateWakeTime(wakeTime: String) {
        viewModelScope.launch {
            repository.updateWakeTime(wakeTime)
            oneSignalManager.setTags(mapOf("wake_time" to wakeTime))
        }
    }

    fun sendEmailVerification(onResult: (status: FirebaseSyncStatus, message: String) -> Unit) {
        viewModelScope.launch {
            val res = firebaseManager.sendEmailVerification()
            onResult(res.status, res.message)
        }
    }

    fun refreshEmailVerificationStatus(onResult: (status: FirebaseSyncStatus, isVerified: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            if (!firebaseManager.isFirebaseInitialized) {
                onResult(
                    FirebaseSyncStatus.OFFLINE_MODE,
                    false,
                    "Firebase Auth is unconfigured or offline. Email verification requires active Firebase connection."
                )
                return@launch
            }

            val isVerified = firebaseManager.checkEmailVerification()
            repository.updateEmailVerificationStatus(isVerified)
            if (isVerified) {
                onResult(
                    FirebaseSyncStatus.REAL_SUCCESS,
                    true,
                    "Email address verified successfully via Firebase Auth."
                )
            } else {
                onResult(
                    FirebaseSyncStatus.REAL_SUCCESS,
                    false,
                    "Email not yet verified. Please tap the verification link sent to your inbox, then refresh."
                )
            }
        }
    }

    /**
     * Requirement 22: Synchronize habit completion and profile telemetry to Firestore.
     * Document ID strictly uses Firebase UID (FirebaseAuth.getInstance().currentUser.uid).
     * Never derives document IDs from email addresses (e.g. email.replace(".", "_")).
     */
    fun syncCloudData(onResult: (status: FirebaseSyncStatus, message: String) -> Unit) {
        viewModelScope.launch {
            val currentFirebaseUser = try {
                firebaseManager.currentUser
            } catch (_: Exception) {
                null
            }
            val profile = userProfile.value
            val canonicalUid = currentFirebaseUser?.uid ?: profile?.firebaseUid

            if (canonicalUid.isNullOrBlank() || canonicalUid.startsWith("usr_") || canonicalUid.startsWith("goog_") || canonicalUid == "guest") {
                onResult(
                    FirebaseSyncStatus.OFFLINE_MODE,
                    "Cloud sync requires an authenticated Firebase session. Please sign in to sync your protocol."
                )
                return@launch
            }

            val completed = todayCompleted.value
            val res = firebaseManager.syncProfileToCloud(
                userId = canonicalUid,
                wakeTime = profile?.wakeTime ?: "06:30",
                focusGoal = profile?.focus ?: "Energy",
                streakDays = profile?.streakDays ?: 1,
                completedItems = completed
            )
            onResult(res.status, res.message)
        }
    }

    fun triggerOneSignalCampaign(campaign: NotificationCampaign) {
        viewModelScope.launch {
            notificationManager.triggerCampaign(campaign)
        }
    }

    fun dismissInAppMessage() {
        notificationManager.dismissInAppMessage()
    }

    fun handleInAppMessageAction(iam: InAppNotificationMessage) {
        notificationManager.dismissInAppMessage()
        if (!iam.targetTaskId.isNullOrBlank() && (iam.actionRoute == "COMPLETE" || iam.actionRoute == "TIMER")) {
            toggleProtocolItem(iam.targetTaskId)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
        }
    }

    suspend fun exportDataJson(): String {
        return repository.exportDataAsJson()
    }

    /**
     * Requirement 24: Explicit, atomic multi-layer account deletion.
     * 1. Remote Firestore document + Firebase Auth user deletion.
     * 2. Local Room cache wipe (completions, notifications, profile reset).
     * 3. RevenueCat subscription identity reset.
     * 4. Notification tags & in-app state reset.
     */
    suspend fun deleteAccountPermanently(): AccountDeletionResult {
        // 1. Remote deletion with fallback local wipe hook
        val remoteResult = try {
            firebaseManager.deleteAccount(onLocalCleanup = {
                repository.wipeAllUserData()
            })
        } catch (e: Exception) {
            com.example.data.firebase.FirebaseSyncResult(
                status = com.example.data.firebase.FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Remote deletion encountered an error."
            )
        }

        // 2. Explicit local repository wipe
        repository.wipeAllUserData()

        // 3. Reset RevenueCat subscription identity
        revenueCatManager.resetUserIdentity()

        // 4. Reset Notification tags and in-app states
        notificationManager.resetIdentityAndTags()

        // 5. Navigate to auth (account no longer exists)
        _isGuestSession.value = false
        _appNavState.value = AppNavDestination.Auth.route

        return AccountDeletionResult(
            isRemoteSuccess = remoteResult.status == com.example.data.firebase.FirebaseSyncStatus.REAL_SUCCESS,
            isLocalWipeSuccess = true,
            isSubscriptionReset = true,
            isNotificationReset = true,
            message = if (remoteResult.status == com.example.data.firebase.FirebaseSyncStatus.REAL_SUCCESS)
                "Account permanently deleted across Cloud Firestore, Firebase Auth, RevenueCat, and local device storage."
            else
                "Local database, RevenueCat session, and notification tags wiped. Remote status: ${remoteResult.message}"
        )
    }

    suspend fun wipeUserData() {
        deleteAccountPermanently()
    }

    // --- Authentication & User Session ---
    fun signIn(
        emailInput: String,
        passwordInput: String,
        onResult: (status: FirebaseSyncStatus, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val email = emailInput.trim()
            val password = passwordInput.trim()

            if (email.isBlank() || !email.contains("@")) {
                onResult(FirebaseSyncStatus.ERROR, "Please enter a valid email address.")
                return@launch
            }
            if (password.length < 6) {
                onResult(FirebaseSyncStatus.ERROR, "Password must be at least 6 characters.")
                return@launch
            }

            val authResult = firebaseManager.signIn(email, password)
            if (authResult.status == FirebaseSyncStatus.ERROR) {
                onResult(FirebaseSyncStatus.ERROR, authResult.message)
                return@launch
            }

            // Sync Firebase UID authority to Room UserProfile cache
            repository.setActiveUserSession(
                firebaseUid = authResult.uid,
                email = authResult.email ?: email,
                displayName = authResult.displayName,
                isEmailVerified = authResult.isEmailVerified,
                role = "Member"
            )

            oneSignalManager.setTags(
                mapOf(
                    "email" to email,
                    "firebase_uid" to (authResult.uid ?: ""),
                    "auth_status" to authResult.status.name
                )
            )

            onResult(authResult.status, authResult.message)
        }
    }

    fun signUp(
        emailInput: String,
        passwordInput: String,
        nameInput: String,
        onResult: (status: FirebaseSyncStatus, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val email = emailInput.trim()
            val password = passwordInput.trim()
            val name = nameInput.trim()

            if (email.isBlank() || !email.contains("@")) {
                onResult(FirebaseSyncStatus.ERROR, "Please enter a valid email address.")
                return@launch
            }
            if (password.length < 6) {
                onResult(FirebaseSyncStatus.ERROR, "Password must be at least 6 characters.")
                return@launch
            }

            val authResult = firebaseManager.signUp(email, password, name)
            if (authResult.status == FirebaseSyncStatus.ERROR) {
                onResult(FirebaseSyncStatus.ERROR, authResult.message)
                return@launch
            }

            // Sync Firebase UID authority to Room UserProfile cache
            repository.setActiveUserSession(
                firebaseUid = authResult.uid,
                email = authResult.email ?: email,
                displayName = authResult.displayName ?: name,
                isEmailVerified = authResult.isEmailVerified,
                role = "Member"
            )

            oneSignalManager.setTags(
                mapOf(
                    "email" to email,
                    "firebase_uid" to (authResult.uid ?: ""),
                    "auth_status" to authResult.status.name
                )
            )

            onResult(authResult.status, authResult.message)
        }
    }

    fun sendPasswordReset(email: String, onResult: (status: FirebaseSyncStatus, message: String) -> Unit) {
        viewModelScope.launch {
            val res = firebaseManager.sendPasswordReset(email.trim())
            onResult(res.status, res.message)
        }
    }

    fun signInWithGoogle(
        googleEmail: String = "member@protocol.app",
        googleName: String = "Protocol Member",
        onResult: (status: FirebaseSyncStatus, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val authResult = firebaseManager.signInWithGoogleSession(googleEmail, googleName)
            if (authResult.status == FirebaseSyncStatus.ERROR) {
                onResult(FirebaseSyncStatus.ERROR, authResult.message)
                return@launch
            }

            // Sync Firebase UID authority to Room UserProfile cache
            repository.setActiveUserSession(
                firebaseUid = authResult.uid,
                email = authResult.email ?: googleEmail,
                displayName = authResult.displayName ?: googleName,
                isEmailVerified = authResult.isEmailVerified,
                role = "Member"
            )

            oneSignalManager.setTags(
                mapOf(
                    "email" to googleEmail,
                    "auth_provider" to "google",
                    "firebase_uid" to (authResult.uid ?: ""),
                    "auth_status" to authResult.status.name
                )
            )

            onResult(authResult.status, authResult.message)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            firebaseManager.signOut()
            repository.clearActiveUserSession()
            _isGuestSession.value = false
            _appNavState.value = AppNavDestination.Auth.route
        }
    }
}

/**
 * Formal Navigation Destinations for the Startup and Onboarding State Machine.
 */
sealed class AppNavDestination(val route: String) {
    object Loading : AppNavDestination("loading")
    object Splash : AppNavDestination("splash")
    object Auth : AppNavDestination("auth")
    object Baseline : AppNavDestination("baseline")
    object Paywall : AppNavDestination("paywall")
    object Dashboard : AppNavDestination("dashboard")

    companion object {
        fun fromRoute(route: String): AppNavDestination = when (route) {
            "loading" -> Loading
            "splash" -> Splash
            "auth" -> Auth
            "baseline" -> Baseline
            "paywall" -> Paywall
            "dashboard" -> Dashboard
            else -> Loading
        }
    }
}
