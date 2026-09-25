package com.example

import com.example.data.revenuecat.PurchaseResult
import com.example.data.revenuecat.RevenueCatManager
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.StoreProduct
import org.junit.Assert.assertEquals
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
 * Test Suite validating RevenueCat Dynamic Pricing Source of Truth.
 *
 * Enforces:
 * RevenueCat Offering -> RevenueCat Package -> StoreProduct -> Store-provided formatted price -> Protocol Paywall UI
 *
 * TEST 1: RevenueCat annual package returns a store price -> Paywall displays that exact formatted price.
 * TEST 2: RevenueCat weekly package returns a store price -> Paywall displays that exact formatted price.
 * TEST 3: Store currency is not USD -> Paywall uses the store-provided localized currency/price.
 * TEST 4: Annual package price changes in RevenueCat/store -> UI uses the new returned price rather than a hardcoded value.
 * TEST 5: Offering has not loaded -> UI does not display a fake/hardcoded subscription price ("Loading price…").
 * TEST 6: Requested package unavailable -> UI does not substitute another package's price ("Unavailable").
 * TEST 7: Annual selected -> Annual package price is displayed, same package is purchased.
 * TEST 8: Weekly selected -> Weekly package price is displayed, same package is purchased.
 */
@OptIn(com.revenuecat.purchases.InternalRevenueCatAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RevenueCatPricingTest {

    private lateinit var revenueCatManager: RevenueCatManager

    @Before
    fun setUp() {
        revenueCatManager = RevenueCatManager()
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

    /**
     * TEST 1:
     * RevenueCat annual package returns a store price
     * -> Paywall displays that exact formatted price.
     */
    @Test
    fun test1_AnnualPackage_ReturnsExactStoreFormattedPrice() {
        val storePrice = "$44.99"
        val annualPkg = createPackage("\$rc_annual", formattedPrice = storePrice, amountMicros = 44990000L)
        val offerings = createOfferings(listOf(annualPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        val displayedPrice = revenueCatManager.getFormattedPrice("\$rc_annual")
        assertEquals(storePrice, displayedPrice)
        assertEquals(annualPkg.product.price.formatted, displayedPrice)
    }

    /**
     * TEST 2:
     * RevenueCat weekly package returns a store price
     * -> Paywall displays that exact formatted price.
     */
    @Test
    fun test2_WeeklyPackage_ReturnsExactStoreFormattedPrice() {
        val storePrice = "$7.99"
        val weeklyPkg = createPackage("\$rc_weekly", formattedPrice = storePrice, amountMicros = 7990000L)
        val offerings = createOfferings(listOf(weeklyPkg))
        revenueCatManager.setRemoteOfferingsForTesting(offerings)

        val displayedPrice = revenueCatManager.getFormattedPrice("\$rc_weekly")
        assertEquals(storePrice, displayedPrice)
        assertEquals(weeklyPkg.product.price.formatted, displayedPrice)
    }

    /**
     * TEST 3:
     * Store currency is not USD
     * -> Paywall uses the store-provided localized currency/price.
     */
    @Test
    fun test3_NonUsdCurrencies_PreserveStoreProvidedLocalizedPrice() {
        // Test EUR currency
        val eurPkg = createPackage("\$rc_annual", formattedPrice = "€39,99", amountMicros = 39990000L, currencyCode = "EUR")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(eurPkg)))
        assertEquals("€39,99", revenueCatManager.getFormattedPrice("\$rc_annual"))

        // Test GBP currency
        val gbpPkg = createPackage("\$rc_annual", formattedPrice = "£34.99", amountMicros = 34990000L, currencyCode = "GBP")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(gbpPkg)))
        assertEquals("£34.99", revenueCatManager.getFormattedPrice("\$rc_annual"))

        // Test JPY currency
        val jpyPkg = createPackage("\$rc_annual", formattedPrice = "¥4,500", amountMicros = 4500000000L, currencyCode = "JPY")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(jpyPkg)))
        assertEquals("¥4,500", revenueCatManager.getFormattedPrice("\$rc_annual"))

        // Test INR currency
        val inrPkg = createPackage("\$rc_annual", formattedPrice = "₹2,999.00", amountMicros = 2999000000L, currencyCode = "INR")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(inrPkg)))
        assertEquals("₹2,999.00", revenueCatManager.getFormattedPrice("\$rc_annual"))
    }

    /**
     * TEST 4:
     * Annual package price changes in RevenueCat/store
     * -> UI uses the new returned price rather than a hardcoded value.
     */
    @Test
    fun test4_PriceChangeInStore_ReflectedImmediately() {
        // Initial price configured
        val initialPkg = createPackage("\$rc_annual", formattedPrice = "$39.99", amountMicros = 39990000L)
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(initialPkg)))
        assertEquals("$39.99", revenueCatManager.getFormattedPrice("\$rc_annual"))

        // Price updated on RevenueCat / Google Play Console to $49.99
        val updatedPkg = createPackage("\$rc_annual", formattedPrice = "$49.99", amountMicros = 49990000L)
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(updatedPkg)))

        val newPrice = revenueCatManager.getFormattedPrice("\$rc_annual")
        assertEquals("$49.99", newPrice)
        assertNotEquals("$39.99", newPrice)
    }

    /**
     * TEST 5:
     * Offering has not loaded
     * -> UI does not display a fake/hardcoded subscription price ("Loading price…").
     */
    @Test
    fun test5_OfferingsNotLoaded_DisplaysLoadingState_NeverFakePrice() {
        // Offerings are null before network response
        revenueCatManager.setRemoteOfferingsForTesting(null)

        val annualPrice = revenueCatManager.getFormattedPrice("\$rc_annual")
        val weeklyPrice = revenueCatManager.getFormattedPrice("\$rc_weekly")

        assertEquals("Loading price…", annualPrice)
        assertEquals("Loading price…", weeklyPrice)
        assertNotEquals("$39.99", annualPrice)
        assertNotEquals("$6.99", weeklyPrice)
    }

    /**
     * TEST 6:
     * Requested package unavailable
     * -> UI does not substitute another package's price ("Unavailable").
     */
    @Test
    fun test6_RequestedPackageUnavailable_DoesNotSubstituteOtherPrice() {
        // Only weekly package exists in remote offerings
        val weeklyPkg = createPackage("\$rc_weekly", formattedPrice = "$6.99", amountMicros = 6990000L)
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(weeklyPkg)))

        val annualPrice = revenueCatManager.getFormattedPrice("\$rc_annual")
        val weeklyPrice = revenueCatManager.getFormattedPrice("\$rc_weekly")

        assertEquals("Unavailable", annualPrice)
        assertEquals("$6.99", weeklyPrice)
        // Must NOT substitute the weekly price for annual
        assertNotEquals("$6.99", annualPrice)
    }

    /**
     * TEST 7:
     * Annual selected
     * -> Annual package price is displayed and exact package corresponds to purchase.
     */
    @Test
    fun test7_AnnualSelected_DisplaysAnnualPrice_MatchesPurchasePackage() {
        val annualPkg = createPackage("\$rc_annual", formattedPrice = "$42.00", amountMicros = 42000000L)
        val weeklyPkg = createPackage("\$rc_weekly", formattedPrice = "$8.00", amountMicros = 8000000L)
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(annualPkg, weeklyPkg)))

        val selectedPlan = "annual"
        val selectedPackageId = if (selectedPlan == "annual") "\$rc_annual" else "\$rc_weekly"
        val displayedPrice = revenueCatManager.getFormattedPrice(selectedPackageId)

        assertEquals("$42.00", displayedPrice)
        val exactPackage = revenueCatManager.findExactPackage(selectedPackageId)
        assertNotNull(exactPackage)
        assertEquals("\$rc_annual", exactPackage?.identifier)
        assertEquals(exactPackage?.product?.price?.formatted, displayedPrice)
    }

    /**
     * TEST 8:
     * Weekly selected
     * -> Weekly package price is displayed and exact package corresponds to purchase.
     */
    @Test
    fun test8_WeeklySelected_DisplaysWeeklyPrice_MatchesPurchasePackage() {
        val annualPkg = createPackage("\$rc_annual", formattedPrice = "$42.00", amountMicros = 42000000L)
        val weeklyPkg = createPackage("\$rc_weekly", formattedPrice = "$8.00", amountMicros = 8000000L)
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(annualPkg, weeklyPkg)))

        val selectedPlan = "weekly"
        val selectedPackageId = if (selectedPlan == "annual") "\$rc_annual" else "\$rc_weekly"
        val displayedPrice = revenueCatManager.getFormattedPrice(selectedPackageId)

        assertEquals("$8.00", displayedPrice)
        val exactPackage = revenueCatManager.findExactPackage(selectedPackageId)
        assertNotNull(exactPackage)
        assertEquals("\$rc_weekly", exactPackage?.identifier)
        assertEquals(exactPackage?.product?.price?.formatted, displayedPrice)
    }

    /**
     * Validates dynamic monthly breakdown calculation.
     */
    @Test
    fun testMonthlyCalculation_FromStorePrice() {
        val annualPkg = createPackage("\$rc_annual", formattedPrice = "$39.99", amountMicros = 39990000L, currencyCode = "USD")
        revenueCatManager.setRemoteOfferingsForTesting(createOfferings(listOf(annualPkg)))

        val monthlyFormatted = revenueCatManager.getFormattedPricePerMonth("\$rc_annual", java.util.Locale.US)
        assertNotNull(monthlyFormatted)
        assertEquals("$3.33", monthlyFormatted)
    }
}
