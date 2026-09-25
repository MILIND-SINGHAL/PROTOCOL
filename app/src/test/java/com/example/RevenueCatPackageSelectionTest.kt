package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.revenuecat.PurchaseResult
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AppNavDestination
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
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

/**
 * Test Suite validating RevenueCat Package-Selection Fallback Fix.
 *
 * Verifies that the purchase logic strictly requires the EXACT requested package,
 * with ZERO fallback to `availablePackages.firstOrNull()` or any other package substitution.
 *
 * TEST 1: Requested plan = Annual, Annual package exists -> Annual package is purchased.
 * TEST 2: Requested plan = Annual, Annual package does NOT exist, Weekly exists -> Purchase does NOT occur, returns unavailable error.
 * TEST 3: Requested plan = Weekly, Weekly exists -> Weekly package is purchased.
 * TEST 4: Requested plan = Weekly, Weekly does NOT exist, Annual exists -> Purchase does NOT occur, do NOT buy Annual.
 * TEST 5: Requested package missing -> isPro remains unchanged, no subscription success result.
 * TEST 6: Requested package missing -> Dashboard is NOT opened.
 * TEST 7: Both packages exist -> selected plan determines the exact package purchased.
 * TEST 8: RevenueCat returns purchase failure -> existing error handling is preserved.
 */
@OptIn(com.revenuecat.purchases.InternalRevenueCatAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RevenueCatPackageSelectionTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var revenueCatManager: RevenueCatManager

    /**
     * Creates a mock StoreProduct for testing without requiring Google Play connection.
     */
    private fun createMockStoreProduct(productId: String): StoreProduct {
        return Proxy.newProxyInstance(
            StoreProduct::class.java.classLoader,
            arrayOf(StoreProduct::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getId", "getSku" -> productId
                "getType" -> ProductType.SUBS
                "getName", "getTitle" -> "Subscription $productId"
                "getDescription" -> "Protocol Subscription Description"
                "getPrice" -> Price("$39.99", 39990000, "USD")
                "toString" -> "StoreProduct($productId)"
                "hashCode" -> productId.hashCode()
                "equals" -> true
                else -> null
            }
        } as StoreProduct
    }

    /**
     * Creates a real RevenueCat Package object.
     */
    private fun createPackage(identifier: String, productId: String = identifier): com.revenuecat.purchases.Package {
        val product = createMockStoreProduct(productId)
        return com.revenuecat.purchases.Package(
            identifier = identifier,
            packageType = if (identifier.contains("annual")) PackageType.ANNUAL else PackageType.WEEKLY,
            product = product,
            offering = "default"
        )
    }

    /**
     * Creates a real RevenueCat Offerings container with the specified available packages.
     */
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

    /**
     * Test machine mirroring ProtocolViewModel purchase execution and navigation handling.
     */
    private class TestPurchaseCoordinator(
        val repository: ProtocolRepository,
        val revenueCatManager: RevenueCatManager
    ) {
        private val _appNavState = MutableStateFlow<String>(AppNavDestination.Paywall.route)
        val appNavState = _appNavState.asStateFlow()

        var purchaseSuccessCallbackInvoked = false

        suspend fun purchasePlan(packageId: String): PurchaseResult {
            purchaseSuccessCallbackInvoked = false
            val result = revenueCatManager.purchasePackage(null, packageId)
            if (result is PurchaseResult.Success) {
                repository.setSubscription(isPro = true, plan = packageId)
                _appNavState.value = AppNavDestination.Dashboard.route
                purchaseSuccessCallbackInvoked = true
            }
            return result
        }
    }

    private lateinit var coordinator: TestPurchaseCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        revenueCatManager = RevenueCatManager()
        coordinator = TestPurchaseCoordinator(repository, revenueCatManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * TEST 1:
     * Requested plan = Annual
     * Annual package exists
     * -> Annual package is purchased.
     */
    @Test
    fun test1_RequestedPlanAnnual_AnnualPackageExists_Purchased() = runBlocking {
        repository.ensureInitialized()
        val annualPkg = createPackage("\$rc_annual")
        val offerings = createOfferings(listOf(annualPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        // Verify exact package resolution
        val resolvedPkg = revenueCatManager.findExactPackage("\$rc_annual")
        assertNotNull("Annual package must be resolved", resolvedPkg)
        assertEquals("\$rc_annual", resolvedPkg?.identifier)

        // Mock successful purchase transaction for the exact package
        var purchasedPackageId: String? = null
        revenueCatManager.purchaseResolverForTesting = { pkgId ->
            purchasedPackageId = pkgId
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "tx_annual_123")
        }

        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue("Purchase must succeed", result is PurchaseResult.Success)
        assertEquals("\$rc_annual", purchasedPackageId)
        assertTrue(coordinator.purchaseSuccessCallbackInvoked)
        assertEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    /**
     * TEST 2:
     * Requested plan = Annual
     * Annual package does NOT exist, Weekly exists
     * -> Purchase must NOT occur.
     * -> Return unavailable/error.
     * -> Must NOT fall back to Weekly.
     */
    @Test
    fun test2_RequestedPlanAnnual_AnnualDoesNotExist_WeeklyExists_PurchaseDoesNotOccur() = runBlocking {
        repository.ensureInitialized()
        // Only weekly exists in remote offerings
        val weeklyPkg = createPackage("\$rc_weekly")
        val offerings = createOfferings(listOf(weeklyPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        // Verify exact package resolution fails
        val resolvedPkg = revenueCatManager.findExactPackage("\$rc_annual")
        assertNull("Annual package must NOT resolve when missing", resolvedPkg)

        var purchaseExecutionAttempted = false
        revenueCatManager.purchaseResolverForTesting = {
            purchaseExecutionAttempted = true
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "should_never_happen")
        }

        val result = coordinator.purchasePlan("\$rc_annual")

        // Must return unavailable error
        assertTrue("Purchase must fail with Error", result is PurchaseResult.Error)
        val errorResult = result as PurchaseResult.Error
        assertEquals("Selected subscription is currently unavailable.", errorResult.message)
        assertEquals("Selected subscription is currently unavailable.", revenueCatManager.lastError.value)

        // Must NOT attempt purchase flow or substitute weekly
        assertFalse("Purchase execution must NOT be attempted when package is missing", purchaseExecutionAttempted)
        assertFalse("Success callback must NOT be invoked", coordinator.purchaseSuccessCallbackInvoked)
    }

    /**
     * TEST 3:
     * Requested plan = Weekly
     * Weekly exists
     * -> Weekly package is purchased.
     */
    @Test
    fun test3_RequestedPlanWeekly_WeeklyExists_Purchased() = runBlocking {
        repository.ensureInitialized()
        val weeklyPkg = createPackage("\$rc_weekly")
        val offerings = createOfferings(listOf(weeklyPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        val resolvedPkg = revenueCatManager.findExactPackage("\$rc_weekly")
        assertNotNull("Weekly package must be resolved", resolvedPkg)
        assertEquals("\$rc_weekly", resolvedPkg?.identifier)

        var purchasedPackageId: String? = null
        revenueCatManager.purchaseResolverForTesting = { pkgId ->
            purchasedPackageId = pkgId
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "tx_weekly_456")
        }

        val result = coordinator.purchasePlan("\$rc_weekly")

        assertTrue("Weekly purchase must succeed", result is PurchaseResult.Success)
        assertEquals("\$rc_weekly", purchasedPackageId)
        assertTrue(coordinator.purchaseSuccessCallbackInvoked)
    }

    /**
     * TEST 4:
     * Requested plan = Weekly
     * Weekly does NOT exist, Annual exists
     * -> Purchase must NOT occur.
     * -> Do NOT buy Annual.
     */
    @Test
    fun test4_RequestedPlanWeekly_WeeklyDoesNotExist_AnnualExists_PurchaseDoesNotOccur() = runBlocking {
        repository.ensureInitialized()
        // Only annual exists in remote offerings
        val annualPkg = createPackage("\$rc_annual")
        val offerings = createOfferings(listOf(annualPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        val resolvedPkg = revenueCatManager.findExactPackage("\$rc_weekly")
        assertNull("Weekly package must NOT resolve when missing", resolvedPkg)

        var purchaseExecutionAttempted = false
        revenueCatManager.purchaseResolverForTesting = {
            purchaseExecutionAttempted = true
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "should_never_happen")
        }

        val result = coordinator.purchasePlan("\$rc_weekly")

        assertTrue("Purchase must fail with Error", result is PurchaseResult.Error)
        val errorResult = result as PurchaseResult.Error
        assertEquals("Selected subscription is currently unavailable.", errorResult.message)

        // Must NOT buy annual
        assertFalse("Purchase execution must NOT be attempted when package is missing", purchaseExecutionAttempted)
        assertFalse("Success callback must NOT be invoked", coordinator.purchaseSuccessCallbackInvoked)
    }

    /**
     * TEST 5:
     * Requested package missing
     * -> isPro remains unchanged.
     * -> No subscription success result.
     */
    @Test
    fun test5_RequestedPackageMissing_IsProRemainsUnchanged_NoSubscriptionSuccess() = runBlocking {
        repository.ensureInitialized()
        // User starts with Free plan and isPro = false
        repository.setSubscription(isPro = false, plan = "free")

        val weeklyPkg = createPackage("\$rc_weekly")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(weeklyPkg)))

        // User requests unavailable annual package
        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue(result is PurchaseResult.Error)
        assertFalse(coordinator.purchaseSuccessCallbackInvoked)

        // isPro and subscription plan in Room must remain untouched
        val profile = repository.getUserProfile()
        assertFalse("isPro must remain false when purchase is rejected", profile?.isPro ?: true)
        assertEquals("free", profile?.subscriptionPlan)
    }

    /**
     * TEST 6:
     * Requested package missing
     * -> Dashboard is NOT opened.
     */
    @Test
    fun test6_RequestedPackageMissing_DashboardIsNotOpened() = runBlocking {
        repository.ensureInitialized()
        val weeklyPkg = createPackage("\$rc_weekly")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(weeklyPkg)))

        assertEquals(AppNavDestination.Paywall.route, coordinator.appNavState.value)

        coordinator.purchasePlan("\$rc_annual")

        // Destination must remain on Paywall, never Dashboard
        assertEquals("Must remain on Paywall screen", AppNavDestination.Paywall.route, coordinator.appNavState.value)
        assertNotEquals(AppNavDestination.Dashboard.route, coordinator.appNavState.value)
    }

    /**
     * TEST 7:
     * Both packages exist
     * -> selected plan determines the exact package purchased.
     */
    @Test
    fun test7_BothPackagesExist_SelectedPlanDeterminesExactPackagePurchased() = runBlocking {
        repository.ensureInitialized()
        val annualPkg = createPackage("\$rc_annual")
        val weeklyPkg = createPackage("\$rc_weekly")
        val offerings = createOfferings(listOf(annualPkg, weeklyPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        var lastPurchasedPackageId: String? = null
        revenueCatManager.purchaseResolverForTesting = { pkgId ->
            lastPurchasedPackageId = pkgId
            PurchaseResult.Success(RevenueCatManager.ENTITLEMENT_ID, "tx_$pkgId")
        }

        // Test purchasing annual selects annual
        coordinator.purchasePlan("\$rc_annual")
        assertEquals("\$rc_annual", lastPurchasedPackageId)

        // Test purchasing weekly selects weekly
        coordinator.purchasePlan("\$rc_weekly")
        assertEquals("\$rc_weekly", lastPurchasedPackageId)
    }

    /**
     * TEST 8:
     * RevenueCat returns purchase failure
     * -> existing error handling is preserved.
     */
    @Test
    fun test8_RevenueCatReturnsPurchaseFailure_ExistingErrorHandlingPreserved() = runBlocking {
        repository.ensureInitialized()
        val annualPkg = createPackage("\$rc_annual")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(annualPkg)))

        // Exact package exists, but RevenueCat / Google Play returns a billing failure
        revenueCatManager.purchaseResolverForTesting = {
            PurchaseResult.Error("Payment was declined by issuing bank.")
        }

        val result = coordinator.purchasePlan("\$rc_annual")

        assertTrue("Failure result must be returned", result is PurchaseResult.Error)
        val errorResult = result as PurchaseResult.Error
        assertEquals("Payment was declined by issuing bank.", errorResult.message)
        assertEquals("Payment was declined by issuing bank.", revenueCatManager.lastError.value)

        // Access must NOT be granted
        assertFalse(coordinator.purchaseSuccessCallbackInvoked)
        assertFalse(repository.getUserProfile()?.isPro ?: true)
        assertEquals(AppNavDestination.Paywall.route, coordinator.appNavState.value)
    }
}
