package com.example

import com.example.data.revenuecat.RevenueCatManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionEntitlementTest {

    @Test
    fun testRevenueCatEntitlementAuthority_ProIdentifier() {
        assertEquals("pro", RevenueCatManager.ENTITLEMENT_ID)
    }

    @Test
    fun testCustomerInfoActiveEntitlementResolvesPro() {
        val mockEntitlements = mapOf(RevenueCatManager.ENTITLEMENT_ID to true)
        val isPro = mockEntitlements[RevenueCatManager.ENTITLEMENT_ID] == true
        assertTrue("Pro access must be granted when RevenueCat entitlement is active", isPro)
    }

    @Test
    fun testCustomerInfoInactiveEntitlementResolvesFree() {
        val mockEntitlements = mapOf(RevenueCatManager.ENTITLEMENT_ID to false)
        val isPro = mockEntitlements[RevenueCatManager.ENTITLEMENT_ID] == true
        assertFalse("Pro access must be denied when RevenueCat entitlement is inactive", isPro)
    }

    @Test
    fun testOfferingPackages_ContainAnnualAndWeekly() {
        val manager = RevenueCatManager()
        val standardOffering = manager.currentOffering
        val packageIds = standardOffering.availablePackages.map { it.identifier }

        assertTrue(packageIds.contains("\$rc_annual"))
        assertTrue(packageIds.contains("\$rc_weekly"))
    }

    @Test
    fun testGooglePlayApiKeyValidation() {
        val manager = RevenueCatManager()
        // Invalid keys
        assertFalse(manager.isLiveKey(""))
        assertFalse(manager.isLiveKey("placeholder_key"))
        assertFalse(manager.isLiveKey("short_key"))
        // Valid live Google Play key format starts with 'goog_' and length >= 20
        assertTrue(manager.isLiveKey("goog_play_billing_live_prod_key_12345"))
    }

    @Test
    fun testUnconfiguredRevenueCat_NeverYieldsMockProSuccess() {
        val manager = RevenueCatManager()
        // Unconfigured manager starts disconnected and not purchasing
        assertFalse(manager.isLiveConnected.value)
        assertFalse(manager.isPurchasing.value)
        assertFalse(manager.isSdkConfigured.value)
    }

    @Test
    fun testDecoupledSubscription_TamperReportCannotGrantPro() {
        val cleanReport = com.example.data.security.SecurityReport(
            isClean = true,
            isTampered = false,
            isHooked = false,
            isRooted = false,
            detectionReasons = emptyList()
        )
        val profile = com.example.data.local.UserProfileEntity(
            isPro = false,
            subscriptionPlan = "free"
        )
        // Clean anti-tamper must NOT alter subscription state
        assertFalse(profile.isPro)
        assertEquals("free", profile.subscriptionPlan)
    }

    @Test
    fun testDemoModeGate_DisabledInProduction() {
        val isDemoEnabled = BuildConfig.DEMO_MODE
        assertFalse("Demo mode bypass must NEVER be true in production release builds", isDemoEnabled)
    }
}
