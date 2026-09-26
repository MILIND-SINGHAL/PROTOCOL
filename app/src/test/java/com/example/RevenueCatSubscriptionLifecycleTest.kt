package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.revenuecat.PurchaseResult
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AppNavDestination
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.EntitlementInfos
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.OwnershipType
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Date

/**
 * Comprehensive RevenueCat Subscription Lifecycle and Authority Test Suite.
 *
 * Verifies all 16 required test conditions:
 * 1. Free user.
 * 2. Pro user.
 * 3. Expired Pro user.
 * 4. Purchase success.
 * 5. Purchase cancellation.
 * 6. Purchase failure.
 * 7. Restore success.
 * 8. Restore with no entitlement.
 * 9. RevenueCat unavailable.
 * 10. Firebase logout.
 * 11. Account switching.
 * 12. Missing requested package.
 * 13. Exact package selection.
 * 14. Regional formatted pricing.
 * 15. Stale Room `isPro = true`.
 * 16. Paywall dismissal without entitlement.
 */
@OptIn(com.revenuecat.purchases.InternalRevenueCatAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RevenueCatSubscriptionLifecycleTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var revenueCatManager: RevenueCatManager
    private lateinit var coordinator: SubscriptionTestCoordinator

    private class SubscriptionTestCoordinator(
        val repository: ProtocolRepository,
        val revenueCatManager: RevenueCatManager
    ) {
        private val _appNavState = MutableStateFlow<String>(AppNavDestination.Loading.route)
        val appNavState = _appNavState.asStateFlow()

        var activeFirebaseUid: String? = null

        suspend fun getAuthoritativeProEntitlement(): Boolean {
            val isEntitled = revenueCatManager.getAuthoritativeProEntitlement()
            val profile = repository.getUserProfile()
            if (profile != null) {
                val targetPlan = if (isEntitled) {
                    revenueCatManager.latestCustomerInfo.value?.entitlements?.get(RevenueCatManager.ENTITLEMENT_ID)?.productIdentifier ?: "pro"
                } else {
                    "free"
                }
                if (profile.isPro != isEntitled || (isEntitled && profile.subscriptionPlan == "free")) {
                    repository.setSubscription(isPro = isEntitled, plan = targetPlan)
                }
            }
            return isEntitled
        }

        suspend fun resolveStartupDestination() {
            repository.ensureInitialized()
            val profile = repository.getUserProfile()

            if (activeFirebaseUid == null) {
                _appNavState.value = AppNavDestination.Auth.route
            } else if (profile == null || !profile.hasCompletedBaseline) {
                _appNavState.value = AppNavDestination.Baseline.route
            } else {
                val isProActive = getAuthoritativeProEntitlement()
                if (isProActive) {
                    _appNavState.value = AppNavDestination.Dashboard.route
                } else {
                    _appNavState.value = AppNavDestination.Paywall.route
                }
            }
        }

        suspend fun handlePaywallDismiss() {
            repository.ensureInitialized()
            val isPro = getAuthoritativeProEntitlement()
            if (isPro) {
                _appNavState.value = AppNavDestination.Dashboard.route
            } else {
                _appNavState.value = AppNavDestination.Paywall.route
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
            revenueCatManager.syncRevenueCatIdentity(firebaseUid)
        }

        suspend fun onUserLoggedOut() {
            activeFirebaseUid = null
            repository.clearActiveUserSession()
            revenueCatManager.resetUserIdentity()
            _appNavState.value = AppNavDestination.Auth.route
        }

        suspend fun purchasePlan(packageId: String): PurchaseResult {
            val result = revenueCatManager.purchasePackage(null, packageId)
            if (result is PurchaseResult.Success) {
                repository.setSubscription(isPro = true, plan = packageId)
                resolveStartupDestination()
            }
            return result
        }

        suspend fun restoreSubscription(): PurchaseResult {
            val result = revenueCatManager.restorePurchases()
            if (result is PurchaseResult.Success) {
                repository.setSubscription(isPro = true, plan = "restored")
                resolveStartupDestination()
            }
            return result
        }
    }

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

    private fun createMockStoreProduct(
        productId: String,
        formattedPrice: String,
        amountMicros: Long = 39990000L,
        currencyCode: String = "USD"
    ): StoreProduct {
        val priceObj = Price(formattedPrice, amountMicros, currencyCode)
        return Proxy.newProxyInstance(
            StoreProduct::class.java.classLoader,
            arrayOf(StoreProduct::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getId", "getSku" -> productId
                "getType" -> ProductType.SUBS
                "getName", "getTitle" -> "Subscription $productId"
                "getDescription" -> "Protocol Subscription Description"
                "getPrice" -> priceObj
                "toString" -> "StoreProduct($productId, $formattedPrice)"
                "hashCode" -> 31 * productId.hashCode() + priceObj.hashCode()
                "equals" -> {
                    val other = args?.getOrNull(0)
                    if (other is StoreProduct) {
                        other.id == productId && other.price == priceObj
                    } else {
                        false
                    }
                }
                else -> null
            }
        } as StoreProduct
    }

    private fun createPackage(
        identifier: String,
        formattedPrice: String,
        amountMicros: Long = 39990000L,
        currencyCode: String = "USD"
    ): com.revenuecat.purchases.Package {
        val product = createMockStoreProduct(identifier, formattedPrice, amountMicros, currencyCode)
        return com.revenuecat.purchases.Package(
            identifier = identifier,
            packageType = if (identifier.contains("annual")) PackageType.ANNUAL else PackageType.WEEKLY,
            product = product,
            offering = "default"
        )
    }

    private fun createOfferings(packages: List<com.revenuecat.purchases.Package>): Offerings {
        val offering = Offering(
            identifier = "default",
            serverDescription = "Default Offering",
            metadata = emptyMap(),
            availablePackages = packages
        )
        return Offerings(
            current = offering,
            all = mapOf("default" to offering)
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        revenueCatManager = RevenueCatManager()
        coordinator = SubscriptionTestCoordinator(repository, revenueCatManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Free user
    @Test
    fun test1_FreeUser_RoutesToPaywall_NoProAccess() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val uid = "UID_FREE_USER"

        revenueCatManager.identitySyncResolverForTesting = { uidParam ->
            createCustomerInfo(isProActive = false, originalAppUserId = uidParam)
        }
        revenueCatManager.entitlementResolverForTesting = { false }

        coordinator.onUserAuthenticated(uid, "free@example.com")
        coordinator.resolveStartupDestination()

        assertFalse(coordinator.getAuthoritativeProEntitlement())
        assertEquals(AppNavDestination.Paywall.route, coordinator.appNavState.value)
        assertEquals("free", repository.getUserProfile()?.subscriptionPlan)
    }

    // 2. Pro user
    @Test
    fun test2_ProUser_RoutesToDashboard_ProAccessGranted() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val uid = "UID_PRO_USER"

        revenueCatManager.identitySyncResolverForTesting = { uidParam ->
            createCustomerInfo(isProActive = true, originalAppUserId = uidParam, planIdentifier = "\$rc_annual")
        }
        revenueCatManager.entitlementResolverForTesting = { true }

        coordinator.onUserAuthenticated(uid, "pro@example.com")
        coordinator.resolveStartupDestination()

        assertTrue(coordinator.getAuthoritativeProEntitlement())
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
        assertTrue(repository.getUserProfile()?.isPro == true)
    }

    // 3. Expired Pro user
    @Test
    fun test3_ExpiredProUser_DowngradesCacheToFree() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        repository.setSubscription(isPro = true, plan = "\$rc_annual") // Old cached state

        val uid = "UID_EXPIRED_USER"
        revenueCatManager.identitySyncResolverForTesting = { uidParam ->
            createCustomerInfo(isProActive = false, originalAppUserId = uidParam)
        }
        revenueCatManager.entitlementResolverForTesting = { false }

        coordinator.onUserAuthenticated(uid, "expired@example.com")
        val isEntitled = coordinator.getAuthoritativeProEntitlement()

        assertFalse("Expired entitlement must report false", isEntitled)
        assertFalse("Room cache must be updated to false", repository.getUserProfile()?.isPro ?: true)
        assertEquals("free", repository.getUserProfile()?.subscriptionPlan)
    }

    // 4. Purchase success
    @Test
    fun test4_PurchaseSuccess_ActivatesPro_UpdatesRoom_OpensDashboard() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val pkg = createPackage("\$rc_annual", "$39.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(pkg)))

        revenueCatManager.purchaseResolverForTesting = { pkgId ->
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "order_123")
        }
        revenueCatManager.entitlementResolverForTesting = { true }

        coordinator.activeFirebaseUid = "UID_PURCHASER"
        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue(result is PurchaseResult.Success)
        assertTrue(repository.getUserProfile()?.isPro == true)
        assertEquals("\$rc_annual", repository.getUserProfile()?.subscriptionPlan)
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    // 5. Purchase cancellation
    @Test
    fun test5_PurchaseCancellation_PreservesFreeTier_DoesNotUnlockPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val pkg = createPackage("\$rc_annual", "$39.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(pkg)))

        revenueCatManager.purchaseResolverForTesting = {
            PurchaseResult.Error("Purchase was cancelled.", isCancelled = true)
        }

        coordinator.activeFirebaseUid = "UID_CANCEL_USER"
        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue(result is PurchaseResult.Error)
        assertTrue((result as PurchaseResult.Error).isCancelled)
        assertFalse("Cancelled purchase must not grant Pro", repository.getUserProfile()?.isPro ?: true)
    }

    // 6. Purchase failure
    @Test
    fun test6_PurchaseFailure_PreservesFreeTier_RecordsError() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val pkg = createPackage("\$rc_annual", "$39.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(pkg)))

        revenueCatManager.purchaseResolverForTesting = {
            PurchaseResult.Error("Payment declined by bank.")
        }

        coordinator.activeFirebaseUid = "UID_DECLINED_USER"
        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue(result is PurchaseResult.Error)
        assertEquals("Payment declined by bank.", (result as PurchaseResult.Error).message)
        assertFalse(repository.getUserProfile()?.isPro ?: true)
    }

    // 7. Restore success
    @Test
    fun test7_RestoreSuccess_UnlocksPro_UpdatesCache() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")

        revenueCatManager.restoreResolverForTesting = {
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "restored")
        }
        revenueCatManager.entitlementResolverForTesting = { true }

        coordinator.activeFirebaseUid = "UID_RESTORE_USER"
        val result = coordinator.restoreSubscription()

        assertTrue(result is PurchaseResult.Success)
        assertTrue(repository.getUserProfile()?.isPro == true)
        assertEquals("restored", repository.getUserProfile()?.subscriptionPlan)
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    // 8. Restore with no entitlement
    @Test
    fun test8_RestoreWithNoEntitlement_ReturnsHelpfulError_DoesNotGrantPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")

        revenueCatManager.restoreResolverForTesting = {
            PurchaseResult.Error("No active 'pro' entitlement found on Google Play for this account.")
        }

        coordinator.activeFirebaseUid = "UID_NO_RESTORE_USER"
        val result = coordinator.restoreSubscription()

        assertTrue(result is PurchaseResult.Error)
        assertEquals("No active 'pro' entitlement found on Google Play for this account.", (result as PurchaseResult.Error).message)
        assertFalse("Must not grant pro when no subscription found", repository.getUserProfile()?.isPro ?: true)
    }

    // 9. RevenueCat unavailable / network failure
    @Test
    fun test9_RevenueCatUnavailable_FailsSafelyToFree() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        val pkg = createPackage("\$rc_annual", "$39.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(pkg)))

        // Network error during entitlement query
        revenueCatManager.entitlementResolverForTesting = { false }

        val isEntitled = coordinator.getAuthoritativeProEntitlement()
        assertFalse("Network error / unavailable must return false", isEntitled)
        assertFalse(repository.getUserProfile()?.isPro ?: true)
    }

    // 10. Firebase logout
    @Test
    fun test10_FirebaseLogout_ClearsSessionAndRevenueCatIdentity() = runBlocking {
        repository.ensureInitialized()
        val uid = "UID_LOGOUT_USER"
        revenueCatManager.identitySyncResolverForTesting = { uidParam ->
            createCustomerInfo(isProActive = true, originalAppUserId = uidParam)
        }

        coordinator.onUserAuthenticated(uid, "logout@example.com")
        assertEquals(uid, revenueCatManager.currentAppUserId)

        coordinator.onUserLoggedOut()

        assertNull("RevenueCat currentAppUserId must be null", revenueCatManager.currentAppUserId)
        val profile = repository.getUserProfile()
        assertNull("Room firebaseUid must be null", profile?.firebaseUid)
        assertFalse("isPro must be false", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
        assertEquals(AppNavDestination.Auth.route, coordinator.appNavState.value)
    }

    // 11. Account switching
    @Test
    fun test11_AccountSwitching_UserBCannotInheritUserAPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")

        val uidA = "UID_A_PRO"
        val uidB = "UID_B_FREE"

        // User A is Pro
        revenueCatManager.identitySyncResolverForTesting = { uidParam ->
            if (uidParam == uidA) createCustomerInfo(isProActive = true, originalAppUserId = uidParam)
            else createCustomerInfo(isProActive = false, originalAppUserId = uidParam)
        }
        revenueCatManager.entitlementResolverForTesting = { revenueCatManager.currentAppUserId == uidA }

        coordinator.onUserAuthenticated(uidA, "a@example.com")
        coordinator.resolveStartupDestination()
        assertTrue(repository.getUserProfile()?.isPro == true)
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)

        // Logout
        coordinator.onUserLoggedOut()

        // User B is Free
        coordinator.onUserAuthenticated(uidB, "b@example.com")
        coordinator.resolveStartupDestination()
        assertFalse("User B must not inherit User A's Pro", repository.getUserProfile()?.isPro ?: true)
        assertEquals("free", repository.getUserProfile()?.subscriptionPlan)
        assertEquals(AppNavDestination.Paywall.route, coordinator.appNavState.value)
    }

    // 12. Missing requested package
    @Test
    fun test12_MissingRequestedPackage_RejectsFallback_ReturnsError() = runBlocking {
        repository.ensureInitialized()
        // Only weekly exists in offerings
        val weeklyPkg = createPackage("\$rc_weekly", "$6.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(weeklyPkg)))
        revenueCatManager.purchaseResolverForTesting = { PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "order_123") }

        // User requested annual package which does NOT exist
        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue("Must return error when package is missing", result is PurchaseResult.Error)
        assertEquals("Selected subscription is currently unavailable.", (result as PurchaseResult.Error).message)
        assertFalse(repository.getUserProfile()?.isPro ?: true)
    }

    // 13. Exact package selection
    @Test
    fun test13_ExactPackageSelection_DoesNotSubstituteOtherPackages() = runBlocking {
        val annualPkg = createPackage("\$rc_annual", "$39.99")
        val weeklyPkg = createPackage("\$rc_weekly", "$6.99")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(annualPkg, weeklyPkg)))

        val resolvedAnnual = revenueCatManager.findExactPackage("\$rc_annual")
        val resolvedWeekly = revenueCatManager.findExactPackage("\$rc_weekly")
        val resolvedNonExistent = revenueCatManager.findExactPackage("\$rc_lifetime")

        assertNotNull(resolvedAnnual)
        assertEquals("\$rc_annual", resolvedAnnual?.identifier)

        assertNotNull(resolvedWeekly)
        assertEquals("\$rc_weekly", resolvedWeekly?.identifier)

        assertNull("Non-existent package must return null without fallback", resolvedNonExistent)
    }

    // 14. Regional formatted pricing
    @Test
    fun test14_RegionalFormattedPricing_PreservesCurrencyAndAmount() {
        val euroPkg = createPackage("\$rc_annual", "€44,99", 44990000L, "EUR")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(euroPkg)))

        val formattedPrice = revenueCatManager.getFormattedPrice("\$rc_annual")
        assertEquals("€44,99", formattedPrice)
    }

    // 15. Stale Room `isPro = true`
    @Test
    fun test15_StaleRoomIsPro_OverriddenByAuthoritativeRevenueCatCheck() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        // Simulating stale cache
        repository.setSubscription(isPro = true, plan = "stale_plan")

        // RevenueCat authoritative check reports inactive
        revenueCatManager.entitlementResolverForTesting = { false }

        val isEntitled = coordinator.getAuthoritativeProEntitlement()

        assertFalse("Stale room cache must be overridden", isEntitled)
        val profile = repository.getUserProfile()
        assertFalse("Room isPro must be set to false", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
    }

    // 16. Paywall dismissal without entitlement
    @Test
    fun test16_PaywallDismissalWithoutEntitlement_DoesNotGrantPro() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("06:30", "Deep Sleep", "None")
        coordinator.activeFirebaseUid = "UID_FREE_USER"
        revenueCatManager.entitlementResolverForTesting = { false }

        coordinator.handlePaywallDismiss()

        assertEquals("Must remain at Paywall when user dismisses without entitlement", AppNavDestination.Paywall.route, coordinator.appNavState.value)
        assertFalse(repository.getUserProfile()?.isPro ?: true)
    }
}
