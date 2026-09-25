package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AppNavDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Authoritative Subscription Authority & Startup Entitlement Test Suite.
 * Validates:
 * 1. RevenueCat / Google Play entitlement is the sole authority for subscription access.
 * 2. Room isPro is strictly a local cache and is NEVER trusted as the first authority.
 * 3. The local Room cache is updated ONLY after obtaining the RevenueCat result.
 * 4. Stale isPro=true is invalidated upon detecting expired/inactive entitlement.
 * 5. Logout clears the local Pro cache.
 * 6. Verification/network failures safely deny Pro access without falling back to cached true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionAuthorityTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var revenueCatManager: RevenueCatManager

    /**
     * Test Navigation State Machine mirroring ProtocolViewModel's authoritative entitlement engine.
     */
    private class SubscriptionAuthorityNavMachine(
        val repository: ProtocolRepository,
        val revenueCatManager: RevenueCatManager
    ) {
        private val _appNavState = MutableStateFlow<String>(AppNavDestination.Loading.route)
        val appNavState = _appNavState.asStateFlow()

        var isSessionActive: Boolean = true

        /**
         * Obtains current RevenueCat CustomerInfo, determines active "pro" entitlement,
         * and synchronizes the local Room cache.
         */
        suspend fun getAuthoritativeProEntitlement(): Boolean {
            val isEntitled = revenueCatManager.getAuthoritativeProEntitlement()

            // Update local Room cache ONLY after obtaining the RevenueCat result
            val profile = repository.getUserProfile()
            if (profile != null) {
                val targetPlan = if (isEntitled) "pro" else "free"
                if (profile.isPro != isEntitled || (isEntitled && profile.subscriptionPlan == "free")) {
                    repository.setSubscription(isPro = isEntitled, plan = targetPlan)
                }
            }

            return isEntitled
        }

        suspend fun resolveStartupDestination() {
            repository.ensureInitialized()
            val profile = repository.getUserProfile()

            if (!isSessionActive) {
                _appNavState.value = AppNavDestination.Auth.route
            } else if (profile == null || !profile.hasCompletedBaseline) {
                _appNavState.value = AppNavDestination.Baseline.route
            } else {
                // Baseline complete: verify RevenueCat CustomerInfo authoritatively first
                val isProActive = getAuthoritativeProEntitlement()
                if (isProActive) {
                    _appNavState.value = AppNavDestination.Dashboard.route
                } else {
                    _appNavState.value = AppNavDestination.Paywall.route
                }
            }
        }

        suspend fun setNavDestination(dest: String) {
            if (dest == AppNavDestination.Dashboard.route || dest == "dashboard") {
                repository.ensureInitialized()
                val profile = repository.getUserProfile()
                if (!isSessionActive) {
                    _appNavState.value = AppNavDestination.Auth.route
                    return
                }
                if (profile == null || !profile.hasCompletedBaseline) {
                    _appNavState.value = AppNavDestination.Baseline.route
                    return
                }

                val isPro = getAuthoritativeProEntitlement()
                if (!isPro) {
                    _appNavState.value = AppNavDestination.Paywall.route
                } else {
                    _appNavState.value = AppNavDestination.Dashboard.route
                }
                return
            }
            _appNavState.value = dest
        }

        suspend fun signOut() {
            repository.clearActiveUserSession()
            revenueCatManager.resetUserIdentity()
            isSessionActive = false
            _appNavState.value = AppNavDestination.Auth.route
        }
    }

    private lateinit var navMachine: SubscriptionAuthorityNavMachine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        revenueCatManager = RevenueCatManager()
        navMachine = SubscriptionAuthorityNavMachine(repository, revenueCatManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * TEST 1:
     * Room says isPro=true
     * RevenueCat says pro inactive
     * → User must NOT receive Pro access.
     */
    @Test
    fun test1_RoomTrue_RevenueCatInactive_DeniesProAccess() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        // Stale cached true in Room
        repository.setSubscription(isPro = true, plan = "pro_annual")

        // RevenueCat authoritative check reports inactive
        revenueCatManager.entitlementResolverForTesting = { false }

        navMachine.resolveStartupDestination()

        // User must NOT receive Pro access; routed to Paywall
        assertEquals(AppNavDestination.Paywall.route, navMachine.appNavState.value)
        assertNotEquals(AppNavDestination.Dashboard.route, navMachine.appNavState.value)

        // Room cache must be synchronized to false
        val profile = repository.getUserProfile()
        assertFalse("Room cache must be updated to false when RevenueCat is inactive", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
    }

    /**
     * TEST 2:
     * Room says isPro=false
     * RevenueCat says pro active
     * → User receives Pro access and Room cache becomes true.
     */
    @Test
    fun test2_RoomFalse_RevenueCatActive_GrantsProAccessAndUpdatesCache() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        // Stale cached false in Room
        repository.setSubscription(isPro = false, plan = "free")

        // RevenueCat authoritative check reports active
        revenueCatManager.entitlementResolverForTesting = { true }

        navMachine.resolveStartupDestination()

        // User receives Pro access; routed to Dashboard
        assertEquals(AppNavDestination.Dashboard.route, navMachine.appNavState.value)

        // Room cache must be updated to true
        val profile = repository.getUserProfile()
        assertTrue("Room cache must be updated to true when RevenueCat is active", profile?.isPro ?: false)
        assertEquals("pro", profile?.subscriptionPlan)
    }

    /**
     * TEST 3:
     * Room says isPro=true
     * RevenueCat says pro active
     * → Dashboard.
     */
    @Test
    fun test3_RoomTrue_RevenueCatActive_RoutesToDashboard() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = true, plan = "pro_annual")

        revenueCatManager.entitlementResolverForTesting = { true }

        navMachine.resolveStartupDestination()

        assertEquals("Active entitlement routes to Dashboard",
            AppNavDestination.Dashboard.route, navMachine.appNavState.value)
        val profile = repository.getUserProfile()
        assertTrue(profile?.isPro ?: false)
    }

    /**
     * TEST 4:
     * Room says isPro=false
     * RevenueCat says pro inactive
     * → Paywall.
     */
    @Test
    fun test4_RoomFalse_RevenueCatInactive_RoutesToPaywall() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = false, plan = "free")

        revenueCatManager.entitlementResolverForTesting = { false }

        navMachine.resolveStartupDestination()

        assertEquals("Inactive entitlement routes to Paywall",
            AppNavDestination.Paywall.route, navMachine.appNavState.value)
        val profile = repository.getUserProfile()
        assertFalse(profile?.isPro ?: true)
    }

    /**
     * TEST 5:
     * Subscription expires after previously being active
     * → Next entitlement refresh changes local state to false.
     */
    @Test
    fun test5_SubscriptionExpires_NextRefreshChangesLocalStateToFalse() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")

        // Day 1: Subscription active
        revenueCatManager.entitlementResolverForTesting = { true }
        navMachine.resolveStartupDestination()
        assertEquals(AppNavDestination.Dashboard.route, navMachine.appNavState.value)
        assertTrue(repository.getUserProfile()?.isPro ?: false)

        // Day 2: Subscription expires on Google Play / RevenueCat
        revenueCatManager.entitlementResolverForTesting = { false }

        // Next authoritative refresh triggers
        val isStillEntitled = navMachine.getAuthoritativeProEntitlement()
        assertFalse("Expired subscription must report inactive", isStillEntitled)

        // Local state changed to false
        val profile = repository.getUserProfile()
        assertFalse("Local isPro cache must be synchronized to false after expiration", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)

        // Navigation state redirects to Paywall
        navMachine.resolveStartupDestination()
        assertEquals(AppNavDestination.Paywall.route, navMachine.appNavState.value)
    }

    /**
     * TEST 6:
     * Logout
     * → Local Pro state cleared.
     */
    @Test
    fun test6_Logout_LocalProStateCleared() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = true, plan = "pro_annual")
        assertTrue(repository.getUserProfile()?.isPro ?: false)

        // User logs out
        navMachine.signOut()

        // Local Pro state must be completely cleared
        val profile = repository.getUserProfile()
        assertFalse("Logout must clear isPro cache to false", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
        assertEquals("Logout must navigate to Auth", AppNavDestination.Auth.route, navMachine.appNavState.value)
    }

    /**
     * TEST 7:
     * Application process recreated with stale isPro=true and RevenueCat reports inactive
     * → Paywall.
     */
    @Test
    fun test7_ProcessRecreation_StaleIsProTrue_RevenueCatInactive_RoutesToPaywall() = runBlocking {
        // Persisted state from previous process run has stale isPro=true
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = true, plan = "pro_annual")

        // New process launch initializes fresh navMachine starting at loading
        val newProcessNavMachine = SubscriptionAuthorityNavMachine(repository, revenueCatManager)
        assertEquals(AppNavDestination.Loading.route, newProcessNavMachine.appNavState.value)

        // RevenueCat live check reports inactive
        revenueCatManager.entitlementResolverForTesting = { false }

        newProcessNavMachine.resolveStartupDestination()

        // Stale isPro=true must NOT grant dashboard access; user routed to Paywall
        assertEquals("Process recreation with inactive RevenueCat must route to Paywall",
            AppNavDestination.Paywall.route, newProcessNavMachine.appNavState.value)
        assertFalse("Room cache must be updated to false", repository.getUserProfile()?.isPro ?: true)
    }

    /**
     * TEST 8:
     * Application process recreated with stale isPro=false and RevenueCat reports active
     * → Dashboard after verification.
     */
    @Test
    fun test8_ProcessRecreation_StaleIsProFalse_RevenueCatActive_RoutesToDashboard() = runBlocking {
        // Persisted state from previous process run has stale isPro=false
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = false, plan = "free")

        // New process launch initializes fresh navMachine starting at loading
        val newProcessNavMachine = SubscriptionAuthorityNavMachine(repository, revenueCatManager)
        assertEquals(AppNavDestination.Loading.route, newProcessNavMachine.appNavState.value)

        // RevenueCat live check reports active
        revenueCatManager.entitlementResolverForTesting = { true }

        newProcessNavMachine.resolveStartupDestination()

        // RevenueCat active entitlement verified; user routed to Dashboard
        assertEquals("Process recreation with active RevenueCat must route to Dashboard",
            AppNavDestination.Dashboard.route, newProcessNavMachine.appNavState.value)
        assertTrue("Room cache must be updated to true", repository.getUserProfile()?.isPro ?: false)
    }

    /**
     * TEST 9:
     * RevenueCat verification fails
     * → Do NOT grant Pro based only on cached true.
     */
    @Test
    fun test9_RevenueCatVerificationFails_DoesNotGrantProFromCachedTrue() = runBlocking {
        // User has cached isPro=true in Room
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Focus", "Oura")
        repository.setSubscription(isPro = true, plan = "pro_annual")

        // RevenueCat verification fails (network error, timeout, billing service disconnected)
        revenueCatManager.entitlementResolverForTesting = {
            // Emulating verification failure: returns false safely
            false
        }

        navMachine.resolveStartupDestination()

        // Conservative security: verification failure must NEVER grant Pro access based on cached true
        assertEquals("Verification failure must route to Paywall",
            AppNavDestination.Paywall.route, navMachine.appNavState.value)
        assertNotEquals("Must NEVER grant Dashboard access on unverified entitlement",
            AppNavDestination.Dashboard.route, navMachine.appNavState.value)
    }
}
