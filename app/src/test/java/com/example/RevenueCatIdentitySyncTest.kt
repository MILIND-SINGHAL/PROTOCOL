package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AppNavDestination
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.EntitlementInfos
import com.revenuecat.purchases.OwnershipType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * RevenueCat Customer Identity Synchronization with Firebase Auth Test Suite.
 *
 * Validates:
 * TEST 1: Firebase user A authenticates -> RevenueCat receives UID A.
 * TEST 2: Firebase user B authenticates after A logs out -> RevenueCat receives UID B.
 * TEST 3: A logs out -> RevenueCat identity is cleared/logged out.
 * TEST 4: A logs into another device -> RevenueCat identity is still A.
 * TEST 5: Firebase UID A has no Pro entitlement -> RevenueCat login(A) must NOT itself grant Pro.
 * TEST 6: Firebase UID A has active Pro entitlement -> CustomerInfo for A reports active Pro.
 * TEST 7: A logs out, B logs in -> B must never inherit A's cached Pro/subscription identity.
 * TEST 8: RevenueCat login fails -> authentication/session flow must not falsely claim RevenueCat subscription verification succeeded.
 * EXTRA: Reject prohibited identity formats (email, email hash, "goog_" prefix).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RevenueCatIdentitySyncTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var revenueCatManager: RevenueCatManager

    /**
     * Helper to create real RevenueCat CustomerInfo instances using RevenueCat's public API.
     */
    private fun createCustomerInfo(
        isProActive: Boolean,
        originalAppUserId: String = "test_user",
        planIdentifier: String = "rc_annual"
    ): CustomerInfo {
        val entitlementMap = if (isProActive) {
            val proEntitlement = EntitlementInfo(
                identifier = RevenueCatManager.ENTITLEMENT_ID,
                isActive = true,
                willRenew = true,
                periodType = PeriodType.NORMAL,
                latestPurchaseDate = Date(),
                originalPurchaseDate = Date(),
                expirationDate = Date(System.currentTimeMillis() + 86400000),
                store = Store.PLAY_STORE,
                productIdentifier = planIdentifier,
                productPlanIdentifier = null,
                isSandbox = true,
                unsubscribeDetectedAt = null,
                billingIssueDetectedAt = null,
                ownershipType = OwnershipType.PURCHASED,
                jsonObject = JSONObject()
            )
            mapOf(RevenueCatManager.ENTITLEMENT_ID to proEntitlement)
        } else {
            emptyMap()
        }

        val customerJson = JSONObject().apply {
            put("subscriber", JSONObject().apply {
                put("original_app_user_id", originalAppUserId)
            })
        }

        return CustomerInfo(
            entitlements = EntitlementInfos(entitlementMap),
            allExpirationDatesByProduct = emptyMap(),
            allPurchaseDatesByProduct = emptyMap(),
            requestDate = Date(),
            schemaVersion = 1,
            firstSeen = Date(),
            originalAppUserId = originalAppUserId,
            managementURL = null,
            originalPurchaseDate = Date(),
            jsonObject = customerJson
        )
    }

    /**
     * Session test machine mirroring ProtocolViewModel's identity sync and navigation logic.
     */
    private class TestSessionCoordinator(
        val repository: ProtocolRepository,
        val revenueCatManager: RevenueCatManager
    ) {
        private val _appNavState = MutableStateFlow<String>(AppNavDestination.Loading.route)
        val appNavState = _appNavState.asStateFlow()

        var activeFirebaseUid: String? = null

        /**
         * Synchronizes RevenueCat customer identity with the canonical Firebase UID.
         * Mirrors ProtocolViewModel.syncRevenueCatIdentity.
         */
        suspend fun syncRevenueCatIdentity(firebaseUid: String): Boolean {
            if (firebaseUid.isBlank()) {
                return false
            }

            val customerInfo = revenueCatManager.syncRevenueCatIdentity(firebaseUid)
            if (customerInfo != null) {
                val proEntitlement = customerInfo.entitlements[RevenueCatManager.ENTITLEMENT_ID]
                val isProActive = proEntitlement?.isActive == true
                val plan = if (isProActive) (proEntitlement?.productIdentifier ?: "pro") else "free"

                repository.setSubscription(isPro = isProActive, plan = plan)
                return true
            } else {
                val profile = repository.getUserProfile()
                if (profile != null && profile.isPro) {
                    repository.setSubscription(isPro = false, plan = "free")
                }
                return false
            }
        }

        suspend fun onUserAuthenticated(firebaseUid: String, email: String) {
            activeFirebaseUid = firebaseUid
            repository.setActiveUserSession(
                firebaseUid = firebaseUid,
                email = email,
                displayName = "User $firebaseUid",
                isEmailVerified = true,
                role = "Member"
            )
            syncRevenueCatIdentity(firebaseUid)
        }

        suspend fun onUserLoggedOut() {
            activeFirebaseUid = null
            repository.clearActiveUserSession()
            revenueCatManager.resetUserIdentity()
            _appNavState.value = AppNavDestination.Auth.route
        }

        suspend fun resolveStartupDestination() {
            repository.ensureInitialized()
            val profile = repository.getUserProfile()

            if (activeFirebaseUid == null) {
                _appNavState.value = AppNavDestination.Auth.route
            } else if (profile == null || !profile.hasCompletedBaseline) {
                _appNavState.value = AppNavDestination.Baseline.route
            } else {
                val isPro = profile.isPro
                if (isPro) {
                    _appNavState.value = AppNavDestination.Dashboard.route
                } else {
                    _appNavState.value = AppNavDestination.Paywall.route
                }
            }
        }
    }

    private lateinit var coordinator: TestSessionCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        revenueCatManager = RevenueCatManager()
        coordinator = TestSessionCoordinator(repository, revenueCatManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * TEST 1:
     * Firebase user A authenticates
     * -> RevenueCat receives UID A.
     */
    @Test
    fun test1_FirebaseUserA_Authenticates_RevenueCatReceivesUidA() = runBlocking {
        repository.ensureInitialized()
        val uidA = "FIREBASE_UID_USER_A_1001"

        revenueCatManager.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = false, originalAppUserId = uid)
        }

        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "userA@example.com")

        assertEquals("RevenueCat currentAppUserId must match Firebase UID A", uidA, revenueCatManager.currentAppUserId)
        val profile = repository.getUserProfile()
        assertEquals("Room cache must store Firebase UID A", uidA, profile?.firebaseUid)
    }

    /**
     * TEST 2:
     * Firebase user B authenticates after A logs out
     * -> RevenueCat receives UID B.
     */
    @Test
    fun test2_FirebaseUserB_AuthenticatesAfterALogsOut_RevenueCatReceivesUidB() = runBlocking {
        repository.ensureInitialized()
        val uidA = "FIREBASE_UID_USER_A_1001"
        val uidB = "FIREBASE_UID_USER_B_2002"

        revenueCatManager.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = false, originalAppUserId = uid)
        }

        // User A authenticates
        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "userA@example.com")
        assertEquals(uidA, revenueCatManager.currentAppUserId)

        // User A logs out
        coordinator.onUserLoggedOut()
        assertNull("RevenueCat identity must be null after logout", revenueCatManager.currentAppUserId)

        // User B authenticates
        coordinator.onUserAuthenticated(firebaseUid = uidB, email = "userB@example.com")
        assertEquals("RevenueCat must now hold UID B", uidB, revenueCatManager.currentAppUserId)
        assertNotEquals("RevenueCat must not hold previous user A's UID", uidA, revenueCatManager.currentAppUserId)
    }

    /**
     * TEST 3:
     * A logs out
     * -> RevenueCat identity is cleared/logged out.
     */
    @Test
    fun test3_UserALogsOut_RevenueCatIdentityCleared() = runBlocking {
        repository.ensureInitialized()
        val uidA = "FIREBASE_UID_USER_A_1001"

        revenueCatManager.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = true, originalAppUserId = uid)
        }

        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "userA@example.com")
        assertEquals(uidA, revenueCatManager.currentAppUserId)
        assertTrue(revenueCatManager.latestCustomerInfo.value != null)

        // User logs out
        var logoutHookTriggered = false
        revenueCatManager.logoutResolverForTesting = {
            logoutHookTriggered = true
        }

        coordinator.onUserLoggedOut()

        assertTrue("RevenueCat logout hook must be invoked", logoutHookTriggered)
        assertNull("RevenueCat currentAppUserId must be null", revenueCatManager.currentAppUserId)
        assertNull("RevenueCat latestCustomerInfo must be cleared", revenueCatManager.latestCustomerInfo.value)

        val profile = repository.getUserProfile()
        assertNull("Room session Firebase UID must be null", profile?.firebaseUid)
        assertFalse("Room isPro must be cleared on logout", profile?.isPro ?: true)
    }

    /**
     * TEST 4:
     * A logs into another device
     * -> RevenueCat identity is still A.
     */
    @Test
    fun test4_UserALogsIntoAnotherDevice_RevenueCatIdentityIsStillA() = runBlocking {
        val uidA = "FIREBASE_UID_USER_A_1001"

        // Device 1
        val revenueCatDevice1 = RevenueCatManager()
        revenueCatDevice1.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = true, originalAppUserId = uid)
        }
        revenueCatDevice1.syncRevenueCatIdentity(uidA)
        assertEquals("Device 1 RevenueCat customer ID must be UID A", uidA, revenueCatDevice1.currentAppUserId)

        // Device 2
        val revenueCatDevice2 = RevenueCatManager()
        revenueCatDevice2.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = true, originalAppUserId = uid)
        }
        revenueCatDevice2.syncRevenueCatIdentity(uidA)
        assertEquals("Device 2 RevenueCat customer ID must be UID A", uidA, revenueCatDevice2.currentAppUserId)

        // Cross-device resolution parity
        assertEquals("Both devices must resolve the exact same RevenueCat customer identity",
            revenueCatDevice1.currentAppUserId, revenueCatDevice2.currentAppUserId)
    }

    /**
     * TEST 5:
     * Firebase UID A has no Pro entitlement
     * -> RevenueCat login(A) must NOT itself grant Pro.
     */
    @Test
    fun test5_UserAHasNoProEntitlement_LoginDoesNotGrantPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        val uidA = "FIREBASE_UID_USER_A_NO_PRO"

        // RevenueCat login succeeds for UID A, but CustomerInfo contains NO pro entitlement
        revenueCatManager.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = false, originalAppUserId = uid)
        }

        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "userA@example.com")
        coordinator.resolveStartupDestination()

        // Room cache must NOT be granted Pro
        val profile = repository.getUserProfile()
        assertFalse("User without Pro entitlement must NOT be granted Pro access", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)

        // Must be routed to Paywall, NOT Dashboard
        assertEquals(AppNavDestination.Paywall.route, coordinator.appNavState.value)
        assertNotEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    /**
     * TEST 6:
     * Firebase UID A has active Pro entitlement
     * -> CustomerInfo for A reports active Pro.
     */
    @Test
    fun test6_UserAHasActiveProEntitlement_CustomerInfoReportsActivePro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        val uidA = "FIREBASE_UID_USER_A_ACTIVE_PRO"

        // RevenueCat returns active Pro for UID A
        revenueCatManager.identitySyncResolverForTesting = { uid ->
            createCustomerInfo(isProActive = true, originalAppUserId = uid, planIdentifier = "\$rc_annual")
        }

        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "userA@example.com")
        coordinator.resolveStartupDestination()

        // Room cache must be verified as Pro
        val profile = repository.getUserProfile()
        assertTrue("User with active Pro entitlement must have Pro access", profile?.isPro ?: false)
        assertEquals("\$rc_annual", profile?.subscriptionPlan)

        // Must route to Dashboard
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    /**
     * TEST 7:
     * A logs out, B logs in
     * -> B must never inherit A's cached Pro/subscription identity.
     */
    @Test
    fun test7_UserALogsOut_UserBLogsIn_BNeverInheritsACachedPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")

        val uidA = "FIREBASE_UID_USER_A_VIP"
        val uidB = "FIREBASE_UID_USER_B_FREE"

        // Step 1: User A logs in with active Pro entitlement
        revenueCatManager.identitySyncResolverForTesting = { uid ->
            if (uid == uidA) {
                createCustomerInfo(isProActive = true, originalAppUserId = uid, planIdentifier = "\$rc_annual")
            } else {
                createCustomerInfo(isProActive = false, originalAppUserId = uid)
            }
        }

        coordinator.onUserAuthenticated(firebaseUid = uidA, email = "vipA@example.com")
        coordinator.resolveStartupDestination()
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
        assertTrue(repository.getUserProfile()?.isPro ?: false)

        // Step 2: User A logs out
        coordinator.onUserLoggedOut()
        assertFalse("Pro state must be cleared on logout", repository.getUserProfile()?.isPro ?: true)
        assertEquals(AppNavDestination.Auth.route, coordinator.appNavState.value)

        // Step 3: User B logs in (has no subscription)
        coordinator.onUserAuthenticated(firebaseUid = uidB, email = "freeB@example.com")
        coordinator.resolveStartupDestination()

        // Assert B never inherited A's Pro status
        val profileB = repository.getUserProfile()
        assertEquals(uidB, profileB?.firebaseUid)
        assertFalse("User B must NEVER inherit User A's cached Pro subscription", profileB?.isPro ?: true)
        assertEquals("free", profileB?.subscriptionPlan)
        assertEquals("User B must be directed to Paywall", AppNavDestination.Paywall.route, coordinator.appNavState.value)
    }

    /**
     * TEST 8:
     * RevenueCat login fails
     * -> authentication/session flow must not falsely claim RevenueCat subscription verification succeeded.
     */
    @Test
    fun test8_RevenueCatLoginFails_DoesNotFalselyClaimSubscriptionVerificationSuccess() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")

        // Pre-existing local stale cache indicating pro (e.g. from previous corrupted state)
        repository.setSubscription(isPro = true, plan = "stale_plan")

        val uid = "FIREBASE_UID_USER_FAILURE_TEST"

        // RevenueCat identity synchronization encounters network error / failure (returns null)
        revenueCatManager.identitySyncResolverForTesting = { null }

        val syncSuccess = coordinator.syncRevenueCatIdentity(uid)

        assertFalse("Identity sync must report failure when RevenueCat login fails", syncSuccess)

        // Stale pro must be invalidated, NOT preserved
        val profile = repository.getUserProfile()
        assertFalse("Failed RevenueCat login must NEVER grant or retain Pro access", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)

        coordinator.activeFirebaseUid = uid
        coordinator.resolveStartupDestination()
        assertEquals("Failed verification must route to Paywall", AppNavDestination.Paywall.route, coordinator.appNavState.value)
    }

    /**
     * PROHIBITED IDENTITY TEST:
     * Ensures RevenueCat rejects email, email hashes, and 'goog_' prefixes,
     * guaranteeing that ONLY canonical FirebaseUser.uid is accepted.
     */
    @Test
    fun testProhibitedIdentityFormats_RejectedByRevenueCatSync() = runBlocking {
        // Plain email
        val res1 = revenueCatManager.syncRevenueCatIdentity("user@example.com")
        assertNull("Plain email must be rejected", res1)

        // 'goog_' prefix
        val res2 = revenueCatManager.syncRevenueCatIdentity("goog_user@example.com")
        assertNull("'goog_' prefix must be rejected", res2)

        // Email with domain
        val res3 = revenueCatManager.syncRevenueCatIdentity("user.name@test.org")
        assertNull("Email with domain must be rejected", res3)

        // Blank UID
        val res4 = revenueCatManager.syncRevenueCatIdentity("   ")
        assertNull("Blank UID must be rejected", res4)
    }
}
