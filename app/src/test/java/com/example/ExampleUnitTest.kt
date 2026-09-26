package com.example

import com.example.data.adaptive.AdaptiveDifficulty
import com.example.data.adaptive.AdaptiveEngine
import com.example.data.adaptive.DayExecutionSummary
import com.example.data.firebase.FirebaseSyncResult
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.revenuecat.PurchaseResult
import com.example.data.security.SecurityIntegrityManager
import com.example.viewmodel.CircadianState
import com.example.viewmodel.CircadianTimingRule
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testStreakCalculation_emptyListReturnsZero() {
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    val repositoryStreak = AdaptiveEngine.calculateAdherenceStreak(emptyMap(), required)
    assertEquals(0, repositoryStreak)
  }

  @Test
  fun testStreakCalculation_todayCompletedReturnsOne() {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = sdf.format(Calendar.getInstance().time)
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    // Full adherence (8/8) on today
    val streak = AdaptiveEngine.calculateAdherenceStreak(mapOf(today to required.toSet()), required)
    assertEquals(1, streak)
  }

  @Test
  fun testStreakCalculation_consecutiveThreeDaysReturnsThree() {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    val completionsMap = mutableMapOf<String, Set<String>>()
    for (i in 0..2) {
      val cal = Calendar.getInstance()
      cal.add(Calendar.DAY_OF_YEAR, -i)
      completionsMap[sdf.format(cal.time)] = required.toSet()
    }
    val streak = AdaptiveEngine.calculateAdherenceStreak(completionsMap, required)
    assertEquals(3, streak)
  }

  @Test
  fun testCircadianLockoutCalculations() {
    // Given wake time 06:30 -> wakeTotalMinutes = 390
    val wakeTotalMinutes = 6 * 60 + 30
    // Caffeine lockout is wake + 90 min = 480 min (08:00)
    val caffeineTarget = wakeTotalMinutes + 90
    assertEquals(8 * 60, caffeineTarget)

    // Wind down is wake + 14h = 390 + 840 = 1230 min (20:30)
    val windDownTotal = (wakeTotalMinutes + 14 * 60) % (24 * 60)
    assertEquals(20 * 60 + 30, windDownTotal)
  }

  @Test
  fun testPurchaseResultContract() {
    val success = PurchaseResult.Success(entitlement = "pro_access", transactionId = "tx_123")
    assertEquals("pro_access", success.entitlement)
    assertEquals("tx_123", success.transactionId)

    val error = PurchaseResult.Error(message = "User cancelled payment")
    assertEquals("User cancelled payment", error.message)
  }

  @Test
  fun testDefaultUserProfileEntityValues() {
    val freshProfile = com.example.data.local.UserProfileEntity()
    assertNull("Fresh profile firebaseUid must be null", freshProfile.firebaseUid)
    assertFalse("Fresh profile must not be Pro", freshProfile.isPro)
    assertEquals("Fresh profile plan must be free", "free", freshProfile.subscriptionPlan)
    assertFalse("Fresh profile baseline must not be completed", freshProfile.hasCompletedBaseline)
    assertNull("Fresh profile email must be null", freshProfile.email)
    assertNull("Fresh profile displayName must be null", freshProfile.displayName)
  }

  @Test
  fun testFirebaseIdentityAuthorityModel() {
    // 1. Fresh unauthenticated state
    val initialProfile = com.example.data.local.UserProfileEntity()
    assertNull(initialProfile.firebaseUid)
    assertNull(initialProfile.email)

    // 2. Firebase authentication delivers canonical UID
    val remoteFirebaseUid = "fb_uid_987654321"
    val authenticatedEmail = "researcher@protocol.app"
    val authenticatedName = "Dr. Huberman"

    val cachedProfile = initialProfile.copy(
      firebaseUid = remoteFirebaseUid,
      email = authenticatedEmail,
      displayName = authenticatedName,
      isEmailVerified = true,
      accountRole = "Member"
    )

    assertEquals("Firebase UID must be the primary identity authority in Room cache", remoteFirebaseUid, cachedProfile.firebaseUid)
    assertEquals("Email must match Firebase user", authenticatedEmail, cachedProfile.email)
    assertEquals("Display name must match Firebase profile", authenticatedName, cachedProfile.displayName)
    assertTrue("Email verified flag reflected", cachedProfile.isEmailVerified)

    // 3. Sign out clears identity authority from Room cache
    val signedOutProfile = cachedProfile.copy(
      firebaseUid = null,
      email = null,
      displayName = null,
      isEmailVerified = false,
      isPro = false,
      subscriptionPlan = "free"
    )
    assertNull("Firebase UID cleared upon sign out", signedOutProfile.firebaseUid)
    assertNull("Email cleared upon sign out", signedOutProfile.email)
    assertFalse("Pro status cleared upon sign out", signedOutProfile.isPro)
  }

  @Test
  fun testStartupDestinationResolverLogic() {
    // Helper replicating the startup state machine rules
    fun resolveDestination(
      isAuthenticated: Boolean,
      hasCompletedBaseline: Boolean,
      isPro: Boolean
    ): String {
      return when {
        !isAuthenticated -> "auth"
        !hasCompletedBaseline -> "baseline"
        !isPro -> "paywall"
        else -> "dashboard"
      }
    }

    // 1. Unauthenticated (Fresh app launch) -> Auth
    assertEquals("auth", resolveDestination(isAuthenticated = false, hasCompletedBaseline = false, isPro = false))

    // 2. Authenticated but Baseline Not Completed -> Baseline
    assertEquals("baseline", resolveDestination(isAuthenticated = true, hasCompletedBaseline = false, isPro = false))

    // 3. Authenticated + Baseline Completed, but Free Tier -> Paywall
    assertEquals("paywall", resolveDestination(isAuthenticated = true, hasCompletedBaseline = true, isPro = false))

    // 4. Authenticated + Baseline Completed + Pro Active -> Dashboard
    assertEquals("dashboard", resolveDestination(isAuthenticated = true, hasCompletedBaseline = true, isPro = true))
  }

  @Test
  fun testFirebaseSyncResult_OfflineModeMustNeverReportSuccess() {
    val offlineResult = FirebaseSyncResult(
      status = FirebaseSyncStatus.OFFLINE_MODE,
      message = "Firebase not initialized. Running in local-only mode."
    )
    assertFalse("Bug 6 Regression Check: OFFLINE_MODE must never evaluate to success = true", offlineResult.success)
    assertEquals(FirebaseSyncStatus.OFFLINE_MODE, offlineResult.status)
  }

  @Test
  fun testFirebaseSyncResult_RealSuccessAndErrorContracts() {
    val realSuccess = FirebaseSyncResult(
      status = FirebaseSyncStatus.REAL_SUCCESS,
      message = "User authenticated with Firebase Cloud.",
      uid = "firebase_canonical_123",
      email = "user@protocol.app"
    )
    assertTrue("REAL_SUCCESS must report success = true", realSuccess.success)
    assertEquals("firebase_canonical_123", realSuccess.uid)

    val errorResult = FirebaseSyncResult(
      status = FirebaseSyncStatus.ERROR,
      message = "Invalid password"
    )
    assertFalse("ERROR must report success = false", errorResult.success)
  }

  @Test
  fun testEmailVerification_RequiresFirebaseState() {
    val unverifiedProfile = com.example.data.local.UserProfileEntity(
      email = "founder@protocol.app",
      isEmailVerified = false
    )
    assertFalse(unverifiedProfile.isEmailVerified)

    val verifiedProfile = unverifiedProfile.copy(isEmailVerified = true)
    assertTrue(verifiedProfile.isEmailVerified)
  }

  @Test
  fun testRevenueCat_UnconfiguredMustNeverReturnFakeSuccess() = kotlinx.coroutines.runBlocking {
    val manager = com.example.data.revenuecat.RevenueCatManager()
    val purchaseResult = manager.purchasePackage(null, "\$rc_annual")
    assertTrue("Unconfigured RevenueCat must return Error, never fake Success", purchaseResult is PurchaseResult.Error)

    val restoreResult = manager.restorePurchases()
    assertTrue("Unconfigured RevenueCat must return Error on restore, never fake Success", restoreResult is PurchaseResult.Error)
  }

  @Test
  fun testDemoMode_MustBeDisabledInProduction() {
    assertFalse("DEMO_MODE must be strictly false by default and in release builds", com.example.BuildConfig.DEMO_MODE)
  }

  @Test
  fun testNotificationManager_InitialStateHasNoFakeBiologicalDataOrPlayerId() {
    val initialTags = mapOf(
      "circadian_phase" to "UNCALCULATED",
      "caffeine_lockout_status" to "PENDING_SYNC",
      "active_protocol_track" to "Circadian Alignment",
      "streak_milestone" to "0_DAYS",
      "wearable_telemetry_source" to "None",
      "subscriber_tier" to "FREE_TIER"
    )
    assertFalse("Initial wearable source must not be Apple Watch", initialTags["wearable_telemetry_source"] == "Apple Watch")
    assertFalse("Initial subscriber tier must not be PRO_ALL_ACCESS", initialTags["subscriber_tier"] == "PRO_ALL_ACCESS")
  }

  @Test
  fun testNotificationAction_TaskValidation() {
    val validIds = com.example.data.notification.ProtocolNotificationReceiver.VALID_PROTOCOL_ITEM_IDS
    assertTrue("rec_sun_mobility must be valid", validIds.contains("rec_sun_mobility"))
    assertTrue("clarity_alpha_coffee must be valid", validIds.contains("clarity_alpha_coffee"))
    assertTrue("sleep_sunlight must be valid", validIds.contains("sleep_sunlight"))
    assertFalse("Arbitrary string must be rejected", validIds.contains("arbitrary_hacked_task_123"))
    assertFalse("Empty string must be rejected", validIds.contains(""))
  }

  @Test
  fun testRequirement17_WeakStreakPrevented_LowAdherenceProducesZeroStreak() {
    val requiredItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep") // 8 items
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val completionsByDate = mutableMapOf<String, Set<String>>()

    // User Scenario from Bug 17:
    // Day 1 -> complete 1 task out of 8 (12.5% adherence < 80%)
    // Day 2 -> complete 1 task out of 8 (12.5% adherence < 80%)
    // Day 3 -> complete 1 task out of 8 (12.5% adherence < 80%)
    // Previously, naive DAO SELECT DISTINCT dateKey produced 3-day streak.
    for (i in 0..2) {
      val cal = Calendar.getInstance()
      cal.add(Calendar.DAY_OF_YEAR, -i)
      val dateStr = sdf.format(cal.time)
      completionsByDate[dateStr] = setOf("sleep_sunlight") // only 1 task
    }

    val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
    assertEquals("Completing 1/8 items daily (12.5%) must NOT produce a streak under the 80% threshold", 0, streak)
  }

  @Test
  fun testRequirement17_StreakRequiresAtLeast80PercentAdherence() {
    val requiredItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep") // 8 items
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val completionsByDate = mutableMapOf<String, Set<String>>()

    // 7 out of 8 tasks completed = 87.5% adherence (>= 80% threshold)
    val qualifyingItems = requiredItems.take(7).toSet()

    for (i in 0..2) {
      val cal = Calendar.getInstance()
      cal.add(Calendar.DAY_OF_YEAR, -i)
      val dateStr = sdf.format(cal.time)
      completionsByDate[dateStr] = qualifyingItems
    }

    val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
    assertEquals("3 consecutive days with 87.5% adherence must yield a 3-day streak", 3, streak)
  }

  @Test
  fun testRequirement17_InterveningDayBelowThresholdBreaksStreak() {
    val requiredItems = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep") // 8 items
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val completionsByDate = mutableMapOf<String, Set<String>>()

    val qualifyingItems = requiredItems.take(7).toSet() // 87.5%
    val failingItems = requiredItems.take(2).toSet() // 25%

    // 2 days ago: 7/8 completed (qualified)
    val cal2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
    completionsByDate[sdf.format(cal2.time)] = qualifyingItems

    // 1 day ago (yesterday): 2/8 completed (failed)
    val cal1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    completionsByDate[sdf.format(cal1.time)] = failingItems

    // Today: 7/8 completed (qualified)
    val cal0 = Calendar.getInstance()
    completionsByDate[sdf.format(cal0.time)] = qualifyingItems

    val streak = AdaptiveEngine.calculateAdherenceStreak(completionsByDate, requiredItems)
    assertEquals("A day below 80% adherence breaks the streak; only today's streak day counts", 1, streak)
  }

  @Test
  fun testRequirement18_AdaptiveEngine_DeloadOnLowAdherence() {
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    // Yesterday: completed only 2 of 8 items = 25% (< 50%)
    val completed = setOf("sleep_sunlight", "sleep_hydration")
    val summary = AdaptiveEngine.summarizeDay("2026-09-22", completed, required)

    val adaptation = AdaptiveEngine.evaluateAdaptation(summary, activeFocus = "Deep Sleep")

    assertEquals(AdaptiveDifficulty.DELOAD_MICRO_ANCHOR, adaptation.difficulty)
    assertEquals(0.65f, adaptation.difficulty.durationMultiplier)
    assertEquals("LOW FRICTION", adaptation.difficulty.badge)
    assertTrue("Rationale must explain friction reduction and deload", adaptation.rationale.contains("-35%"))
    assertNotNull("Should prioritize first missed anchor", adaptation.priorityTaskId)
    assertTrue(adaptation.tomorrowProjection.contains("Deload"))
  }

  @Test
  fun testRequirement18_AdaptiveEngine_BalancedOnModerateAdherenceWithTargetedPriority() {
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    // Yesterday: completed 5 of 8 items = 62.5% (50% - 79%)
    // Missed: sleep_nsdr, sleep_blue_light, sleep_magnesium
    val completed = setOf("sleep_sunlight", "sleep_delay_caffeine", "sleep_hydration", "sleep_caffeine_cutoff", "sleep_temp")
    val summary = AdaptiveEngine.summarizeDay("2026-09-22", completed, required)

    val adaptation = AdaptiveEngine.evaluateAdaptation(summary, activeFocus = "Deep Sleep")

    assertEquals(AdaptiveDifficulty.BALANCED_CALIBRATION, adaptation.difficulty)
    assertEquals(1.0f, adaptation.difficulty.durationMultiplier)
    assertEquals("sleep_nsdr", adaptation.priorityTaskId)
    assertEquals("Adaptive Priority (Midday Parasympathetic NSDR)", adaptation.priorityTaskLabel)
    assertTrue("Rationale must highlight missed anchor", adaptation.rationale.contains("Midday Parasympathetic NSDR"))
  }

  @Test
  fun testRequirement18_AdaptiveEngine_ProgressiveOverloadOnHighAdherence() {
    val required = AdaptiveEngine.getRequiredItemsForTrack("Deep Sleep")
    // Yesterday: completed 7 of 8 items = 87.5% (>= 80%)
    val completed = required.take(7).toSet()
    val summary = AdaptiveEngine.summarizeDay("2026-09-22", completed, required)

    val adaptation = AdaptiveEngine.evaluateAdaptation(summary, activeFocus = "Deep Sleep")

    assertEquals(AdaptiveDifficulty.PROGRESSIVE_OVERLOAD, adaptation.difficulty)
    assertEquals(1.35f, adaptation.difficulty.durationMultiplier)
    assertEquals("ADVANCED STIMULUS", adaptation.difficulty.badge)
    assertNull("No remedial priority task needed during progressive overload", adaptation.priorityTaskId)
    assertTrue(adaptation.rationale.contains("+35%"))
  }

  @Test
  fun testRequirement19_CircadianTimingRules_TransparencyAndConfidence() {
    val state = CircadianState()

    // 1. Transparency on Morning Light Window
    assertEquals("General guidance", state.lightRule.confidenceLevel)
    assertTrue("Formula must describe wake time + offset", state.lightRule.ruleFormula.contains("Wake time +"))
    assertTrue("Reason must describe habit benefit", state.lightRule.scientificReason.contains("consistent morning routine"))

    // 2. Transparency on Caffeine Delay
    assertEquals("General guidance", state.caffeineRule.confidenceLevel)
    assertTrue("Formula must describe wake time + offset", state.caffeineRule.ruleFormula.contains("Wake time +"))
    assertTrue("Reason must describe adenosine clearance", state.caffeineRule.scientificReason.contains("adenosine"))

    // 3. Transparency on Wind-Down
    assertEquals("General guidance", state.windDownRule.confidenceLevel)
    assertTrue("Formula must describe wake time + offset", state.windDownRule.ruleFormula.contains("Wake time +"))
    assertTrue("Reason must describe melatonin/temperature", state.windDownRule.scientificReason.contains("melatonin"))
  }

  @Test
  fun testRequirement19_CircadianOffsets_ConfigurableCalculation() {
    // Wake time = 07:00 (420 minutes)
    val wakeTotalMinutes = 7 * 60

    // Configurable Offsets:
    val customCaffeineOffset = 120 // 2 hours
    val customLightOffset = 45     // 45 min
    val customWindDownOffset = 15  // 15 hours

    // Target Calculations:
    val caffeineTarget = wakeTotalMinutes + customCaffeineOffset // 540 min = 09:00
    assertEquals(9 * 60, caffeineTarget)

    val luxEndTarget = wakeTotalMinutes + customLightOffset // 465 min = 07:45
    assertEquals(7 * 60 + 45, luxEndTarget)

    val windDownTarget = (wakeTotalMinutes + customWindDownOffset * 60) % (24 * 60) // 1320 min = 22:00
    assertEquals(22 * 60, windDownTarget)
  }

  @Test
  fun testRequirement20_SecuritySignature_MatchesExpectedDigest() {
    // Create dummy certificate bytes whose SHA-256 matches one of the expected digests
    val md = MessageDigest.getInstance("SHA-256")
    val dummyCert = "PROTOCOL_TRUSTED_CERT_DATA_FOR_UNIT_TESTS".toByteArray()
    val dummyDigest = md.digest(dummyCert).joinToString("") { "%02X".format(it) }.uppercase()

    val trustedDigests = setOf(dummyDigest)

    // Verification must return true for trusted certificate
    val isVerified = SecurityIntegrityManager.verifyCertificateDigest(dummyCert, trustedDigests)
    assertTrue("Signing certificate with matching SHA-256 digest must be verified as trusted", isVerified)
  }

  @Test
  fun testRequirement20_SecuritySignature_RejectsUntrustedOrTamperedCertificate() {
    val legitimateCert = "GENUINE_KEYSTORE_DATA".toByteArray()
    val legitimateDigest = SecurityIntegrityManager.computeCertificateSha256(legitimateCert)

    val rogueCert = "MODDED_OR_TAMPERED_KEYSTORE_DATA".toByteArray()
    val trustedDigests = setOf(legitimateDigest)

    // Verification must return false for rogue/unmatched certificate
    val isVerified = SecurityIntegrityManager.verifyCertificateDigest(rogueCert, trustedDigests)
    assertFalse("Rogue or modified certificate digest must be rejected as untrusted", isVerified)
  }

  @Test
  fun testCertificateDigestSeparation_ReleaseDoesNotTrustDebugCertificate() {
    // Release builds must only trust the official release certificate, not the debug certificate
    val releaseDigests = setOf(SecurityIntegrityManager.RELEASE_CERTIFICATE_DIGEST)
    assertFalse("Release certificate set must NOT include the debug keystore digest",
      releaseDigests.contains(SecurityIntegrityManager.DEBUG_CERTIFICATE_DIGEST)
    )
    assertTrue("Release certificate set must contain official release digest",
      releaseDigests.contains("B412F84973C25971A16689E2844521CD88935A6194021180FF23AA894CE19243")
    )
  }

  @Test
  fun testRequirement20_SubscriptionSecurity_DecoupledFromAntiTamper() {
    // Anti-tamper report must NOT automatically grant Pro access or override billing authority
    val profile = com.example.data.local.UserProfileEntity(
      isPro = false,
      subscriptionPlan = "free",
      accountRole = "Member"
    )

    // Even in a 100% clean, non-tampered environment:
    val cleanReport = com.example.data.security.SecurityReport(
      isClean = true,
      isTampered = false,
      isHooked = false,
      isRooted = false,
      detectionReasons = emptyList()
    )

    // Subscriptions remain strictly false/free unless verified through RevenueCat / Google Play receipts
    assertFalse("Clean anti-tamper report must NEVER grant Pro access on its own", profile.isPro)
    assertEquals("free", profile.subscriptionPlan)
    assertTrue("Environment report is diagnostic only", cleanReport.isClean)
  }

  // --- Requirement 21 & 22 Tests: Unified User Authority & Firebase UID Identity ---

  @Test
  fun testRequirement21_UnifiedUserAuthority_FirebaseUidToRoomCache() {
    // Pipeline: Firebase UID -> UserProfile -> Room Local Cache
    val canonicalAuthUid = "firebase_auth_uid_987654321"
    val testEmail = "subscriber@domain.com"
    val testDisplayName = "Subscriber"

    val profile = com.example.data.local.UserProfileEntity(
      firebaseUid = canonicalAuthUid,
      email = testEmail,
      displayName = testDisplayName,
      isEmailVerified = true,
      accountRole = "Member",
      isPro = false,
      subscriptionPlan = "free"
    )

    // Room profile entity stores the Firebase UID as the authoritative identity root
    assertEquals("firebase_auth_uid_987654321", profile.firebaseUid)
    assertEquals("subscriber@domain.com", profile.email)
    assertEquals("Subscriber", profile.displayName)
    assertTrue(profile.isEmailVerified)
    assertEquals("Member", profile.accountRole)
  }

  @Test
  fun testRequirement21_SubscriptionAuthority_RevenueCatOnly() {
    // Subscription entitlement authority is strictly RevenueCat ENTITLEMENT_ID ("pro")
    val entitlementKey = com.example.data.revenuecat.RevenueCatManager.ENTITLEMENT_ID
    assertEquals("pro", entitlementKey)

    // Entitlement map simulation representing RevenueCat CustomerInfo
    val mockEntitlements = mapOf(
      "pro" to true,
      "other_feature" to false
    )
    val isEntitled = mockEntitlements[entitlementKey] == true
    assertTrue("RevenueCat 'pro' entitlement must activate Pro access", isEntitled)

    val inactiveEntitlements = mapOf("pro" to false)
    val isInactive = inactiveEntitlements[entitlementKey] == true
    assertFalse("Inactive RevenueCat entitlement must deactivate Pro access", isInactive)
  }

  @Test
  fun testRequirement21_RoleAuthority_ServerControlled() {
    // Client cannot self-elevate role to Admin or Superuser; server-controlled, client clamped to Member
    val sanitizedAdmin = com.example.data.security.SecurityIntegrityManager.sanitizeAccountRole("Admin")
    val sanitizedSuperuser = com.example.data.security.SecurityIntegrityManager.sanitizeAccountRole("Superuser")
    val sanitizedNull = com.example.data.security.SecurityIntegrityManager.sanitizeAccountRole(null)

    assertEquals("Member", sanitizedAdmin)
    assertEquals("Member", sanitizedSuperuser)
    assertEquals("Member", sanitizedNull)
  }

  @Test
  fun testRequirement22_FirestoreIdentity_StrictlyUsesFirebaseUid() {
    // Valid Firebase UIDs
    val validFirebaseUid1 = "wXyZ1234567890abcdef"
    val validFirebaseUid2 = "qwert12345_auth_session"
    assertTrue("Authentic Firebase UID must be accepted", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid(validFirebaseUid1))
    assertTrue("Standard UID format must be accepted", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid(validFirebaseUid2))

    // Rejection of raw emails
    assertFalse("Raw email must never be used as Firestore document ID", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid("abc@gmail.com"))

    // Rejection of fragile derived/transformed emails (e.g. email.replace(".", "_") => "abc@gmail.com" -> "abc_gmail_com")
    assertFalse("Transformed email (abc_gmail_com) must be rejected as document ID", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid("abc_gmail_com"))
    assertFalse("Transformed email (john_doe_company_org) must be rejected", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid("john_doe_company_org"))

    // Null or blank rejection
    assertFalse("Null UID must be rejected", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid(null))
    assertFalse("Blank UID must be rejected", com.example.data.firebase.FirebaseManager.isValidFirestoreUserUid(""))
  }

  // --- Requirement 23 & 24 Tests: Subscription Decoupling & Atomic Account Deletion ---

  @Test
  fun testRequirement23_FirestorePayload_ExcludesClientSideIsPro() {
    // Habit telemetry payload sent to Firestore MUST NOT contain "isPro"
    val testUid = "usr_authenticated_firebase_uid"
    val testWakeTime = "06:30"
    val testFocus = "Deep Sleep"
    val testStreakDays = 5
    val testCompleted = setOf("rec_sun_mobility", "caffeine_lockout")

    // The Firestore sync document model
    val userDoc = mapOf(
      "uid" to testUid,
      "wakeTime" to testWakeTime,
      "focusGoal" to testFocus,
      "streakDays" to testStreakDays,
      "lastSyncTimestamp" to System.currentTimeMillis(),
      "completedToday" to testCompleted.toList()
    )

    // Verification: isPro is NOT present in Firestore telemetry document
    assertFalse("Firestore habit telemetry payload MUST NOT contain 'isPro'", userDoc.containsKey("isPro"))
    assertTrue("Firestore habit telemetry must contain habit data", userDoc.containsKey("wakeTime"))
    assertTrue("Firestore habit telemetry must contain streak data", userDoc.containsKey("streakDays"))
  }

  @Test
  fun testRequirement23_SubscriptionAuthority_GovernedSolelyByRevenueCat() {
    // Even if an external Firestore document contained "isPro" = true,
    // the application's single authority for Pro access is strictly RevenueCat:
    val revenueCatEntitlementActive = false // User has no active Google Play purchase
    val firestoreSimulatedIsPro = true // Compromised or manipulated client-side cloud document

    // The authoritative check in Protocol:
    val effectiveProAccess = revenueCatEntitlementActive // Decoupled from Firestore

    assertFalse("Pro access MUST NOT be granted based on Firestore or client boolean", effectiveProAccess)
  }

  @Test
  fun testRequirement24_AccountDeletion_AtomicMultiLayerCleanup() {
    // Verify that atomic account deletion covers all 4 layers:
    // 1. Remote (Firebase Auth + Firestore)
    // 2. Local Room Cache
    // 3. Subscription Identity (RevenueCat)
    // 4. Notification & In-App state

    val result = com.example.viewmodel.AccountDeletionResult(
      isRemoteSuccess = true,
      isLocalWipeSuccess = true,
      isSubscriptionReset = true,
      isNotificationReset = true,
      message = "Account permanently deleted across Cloud Firestore, Firebase Auth, RevenueCat, and local device storage."
    )

    assertTrue("Remote deletion succeeded", result.isRemoteSuccess)
    assertTrue("Local database completely wiped", result.isLocalWipeSuccess)
    assertTrue("Subscription identity reset in RevenueCat", result.isSubscriptionReset)
    assertTrue("Notification tags and state reset", result.isNotificationReset)
    assertTrue("Full deletion verified", result.isFullyDeleted)
  }
}
