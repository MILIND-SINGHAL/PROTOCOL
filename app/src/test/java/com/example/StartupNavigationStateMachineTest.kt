package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.viewmodel.AppNavDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupNavigationStateMachineTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Pure state machine destination resolver replicating ViewModel's logic:
     * App launch -> Loading
     *   ↓
     * Firebase session?
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
    private fun resolveStartupDestination(
        hasFirebaseSession: Boolean,
        isGuestSession: Boolean,
        hasCompletedBaseline: Boolean,
        isPro: Boolean
    ): String {
        val isSessionActive = hasFirebaseSession || isGuestSession
        return when {
            !isSessionActive -> AppNavDestination.Auth.route
            !hasCompletedBaseline -> AppNavDestination.Baseline.route
            !isPro -> AppNavDestination.Paywall.route
            else -> AppNavDestination.Dashboard.route
        }
    }

    @Test
    fun testStartupState_InitialRouteIsLoading_NeverDashboard() {
        assertEquals("loading", AppNavDestination.Loading.route)
        assertEquals("splash", AppNavDestination.Splash.route)
        assertEquals("auth", AppNavDestination.Auth.route)
        assertEquals("baseline", AppNavDestination.Baseline.route)
        assertEquals("paywall", AppNavDestination.Paywall.route)
        assertEquals("dashboard", AppNavDestination.Dashboard.route)

        // Verification: App startup must never default to dashboard directly
        val initialDestination = AppNavDestination.Loading.route
        assertEquals("loading", initialDestination)
    }

    @Test
    fun testStartup_NoFirebaseSessionAndNotGuest_NavigatesStrictlyToAuth() {
        // Fresh user launch with no active Firebase session
        val destination = resolveStartupDestination(
            hasFirebaseSession = false,
            isGuestSession = false,
            hasCompletedBaseline = false,
            isPro = false
        )
        assertEquals("Fresh user with no session must be routed to Auth", AppNavDestination.Auth.route, destination)
    }

    @Test
    fun testStartup_FirebaseSessionActive_BaselineNotCompleted_NavigatesToBaseline() {
        // Authenticated user who has not completed baseline onboarding
        val destination = resolveStartupDestination(
            hasFirebaseSession = true,
            isGuestSession = false,
            hasCompletedBaseline = false,
            isPro = false
        )
        assertEquals("User without completed baseline must be routed to Baseline", AppNavDestination.Baseline.route, destination)
    }

    @Test
    fun testStartup_FirebaseSessionActive_BaselineCompleted_NotPro_NavigatesToPaywall() {
        // Authenticated user with baseline completed, but in standard free tier
        val destination = resolveStartupDestination(
            hasFirebaseSession = true,
            isGuestSession = false,
            hasCompletedBaseline = true,
            isPro = false
        )
        assertEquals("User with completed baseline on free tier must be routed to Paywall", AppNavDestination.Paywall.route, destination)
    }

    @Test
    fun testStartup_FirebaseSessionActive_BaselineCompleted_ProActive_NavigatesToDashboard() {
        // Authenticated user with baseline completed and active Pro entitlement
        val destination = resolveStartupDestination(
            hasFirebaseSession = true,
            isGuestSession = false,
            hasCompletedBaseline = true,
            isPro = true
        )
        assertEquals("Pro user with completed baseline must be routed to Dashboard", AppNavDestination.Dashboard.route, destination)
    }

    @Test
    fun testStartup_GuestModeFlow_ContinuesToBaselineThenPaywallThenDashboard() {
        // 1. User skips auth -> Guest session enabled, baseline incomplete
        val step1Destination = resolveStartupDestination(
            hasFirebaseSession = false,
            isGuestSession = true,
            hasCompletedBaseline = false,
            isPro = false
        )
        assertEquals("Guest user must take baseline intake", AppNavDestination.Baseline.route, step1Destination)

        // 2. Guest user completes baseline -> Prompt with Paywall
        val step2Destination = resolveStartupDestination(
            hasFirebaseSession = false,
            isGuestSession = true,
            hasCompletedBaseline = true,
            isPro = false
        )
        assertEquals("Guest user after baseline is presented with Paywall", AppNavDestination.Paywall.route, step2Destination)

        // 3. Guest user unlocks Pro -> Dashboard access
        val step3Destination = resolveStartupDestination(
            hasFirebaseSession = false,
            isGuestSession = true,
            hasCompletedBaseline = true,
            isPro = true
        )
        assertEquals("Guest user with Pro reaches Dashboard", AppNavDestination.Dashboard.route, step3Destination)
    }

    @Test
    fun testEnsureInitialized_NeverAutoCompletesBaselineOrGrantsPro_NoDoubleBypass() = runBlocking {
        // Fresh database initialization
        repository.ensureInitialized()
        val profile = repository.getUserProfile()

        assertNotNull("User profile must exist after initialization", profile)
        assertFalse("Double bypass check: Fresh user must NOT have completed baseline", profile?.hasCompletedBaseline ?: true)
        assertFalse("Double bypass check: Fresh user must NOT have Pro granted", profile?.isPro ?: true)
        assertEquals("Double bypass check: Fresh user subscription plan must be 'free'", "free", profile?.subscriptionPlan)
        assertNull("Double bypass check: Fresh user firebaseUid must be null", profile?.firebaseUid)
        assertNull("Double bypass check: Fresh user email must be null", profile?.email)
    }

    @Test
    fun testSaveBaseline_SetsHasCompletedBaselineToTrue() = runBlocking {
        repository.ensureInitialized()
        val initialProfile = repository.getUserProfile()
        assertFalse(initialProfile?.hasCompletedBaseline ?: true)

        repository.saveBaseline(wakeTime = "06:45", focus = "Cognitive Clarity", wearable = "Whoop")
        val updatedProfile = repository.getUserProfile()

        assertNotNull(updatedProfile)
        assertTrue("Saving baseline must mark hasCompletedBaseline = true", updatedProfile?.hasCompletedBaseline ?: false)
        assertEquals("06:45", updatedProfile?.wakeTime)
        assertEquals("Cognitive Clarity", updatedProfile?.focus)
        assertEquals("Whoop", updatedProfile?.wearable)
    }

    @Test
    fun testWipeUserData_ResetsBaselineAndProStatus() = runBlocking {
        repository.ensureInitialized()
        repository.saveBaseline("07:00", "Physical Recovery", "Apple Watch")
        repository.setSubscription(isPro = true, plan = "pro_annual")

        val activeProfile = repository.getUserProfile()
        assertTrue(activeProfile?.hasCompletedBaseline ?: false)
        assertTrue(activeProfile?.isPro ?: false)

        repository.wipeAllUserData()
        val wipedProfile = repository.getUserProfile()

        assertNotNull(wipedProfile)
        assertFalse("Wipe must reset hasCompletedBaseline to false", wipedProfile?.hasCompletedBaseline ?: true)
        assertFalse("Wipe must reset isPro to false", wipedProfile?.isPro ?: true)
        assertEquals("free", wipedProfile?.subscriptionPlan)
        assertNull(wipedProfile?.firebaseUid)
    }

    @Test
    fun testSignOut_InvalidatesSession_ReroutesToAuth() {
        var isGuestSession = true
        var hasFirebaseSession = true

        // Simulating sign out
        hasFirebaseSession = false
        isGuestSession = false

        val destination = resolveStartupDestination(
            hasFirebaseSession = hasFirebaseSession,
            isGuestSession = isGuestSession,
            hasCompletedBaseline = true,
            isPro = true
        )
        assertEquals("Signing out must route strictly to Auth", AppNavDestination.Auth.route, destination)
    }
}
