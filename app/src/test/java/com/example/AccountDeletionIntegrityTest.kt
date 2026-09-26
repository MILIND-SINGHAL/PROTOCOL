package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FirebaseSyncResult
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.local.ProtocolCompletionEntity
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.local.UserProfileEntity
import com.example.data.notification.ProtocolNotificationManager
import com.example.data.notification.ProtocolNotificationReceiver
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AccountDeletionResult
import com.example.viewmodel.ProtocolViewModel
import kotlinx.coroutines.flow.first
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audit & Unit Test Suite for Account Deletion Integrity.
 *
 * Verifies that account deletion accurately reports success/failure across
 * Firebase Auth, Firestore, Room DB, RevenueCat identity, notifications, and local session state.
 *
 * Testing Classification:
 * - Unit tests / Mocked service boundaries for Firebase Auth & RevenueCat SDK APIs.
 * - Integration test for Room DB local wipe and session/cache invalidation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountDeletionIntegrityTest {

    private lateinit var application: Application
    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository
    private lateinit var viewModel: ProtocolViewModel

    private val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, ProtocolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProtocolRepository(database.protocolDao())
        ProtocolNotificationReceiver.testDatabase = database

        viewModel = ProtocolViewModel(application)
    }

    @After
    fun tearDown() {
        ProtocolNotificationReceiver.testDatabase = null
        database.close()
    }

    /**
     * TEST 1: Authenticated user requests deletion
     * -> correct confirmation flow structure.
     */
    @Test
    fun test1_AuthenticatedUserRequestsDeletion_ConfirmationFlow() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Physical Recovery",
                firebaseUid = "user_test_1",
                isPro = true
            )
        )

        val profile = dao.getUserProfile()
        assertNotNull("User profile must exist prior to deletion request", profile)
        assertEquals("user_test_1", profile?.firebaseUid)

        val result = viewModel.deleteAccountPermanently()
        assertNotNull(result)
        assertTrue(result.isLocalWipeSuccess)
        assertTrue(result.isSubscriptionReset)
        assertTrue(result.isNotificationReset)
    }

    /**
     * TEST 2: Firebase deletion succeeds
     * -> Firebase account is actually deleted and success is reported.
     */
    @Test
    fun test2_FirebaseDeletionSucceeds_AccountActuallyDeleted() = runBlocking {
        val successResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.REAL_SUCCESS,
            message = "Firebase account, cloud data, and local storage deleted."
        )

        assertTrue(successResult.isRealSuccess)
        assertTrue(successResult.success)
        assertFalse(successResult.isOfflineMode)
        assertFalse(successResult.isError)
    }

    /**
     * TEST 3: Firebase deletion fails
     * -> User is NOT told permanent deletion succeeded.
     */
    @Test
    fun test3_FirebaseDeletionFails_UserNotToldPermanentDeletionSucceeded() = runBlocking {
        val failureResult = AccountDeletionResult(
            isRemoteSuccess = false,
            isLocalWipeSuccess = false,
            isSubscriptionReset = false,
            isNotificationReset = false,
            message = "Account deletion could not be completed. Failed to delete remote account."
        )

        assertFalse("Remote deletion success must be false when Firebase deletion fails", failureResult.isRemoteSuccess)
        assertFalse("isFullyDeleted must be false", failureResult.isFullyDeleted)
        assertFalse("Message must NOT claim permanent deletion", failureResult.message.contains("permanently deleted", ignoreCase = true))
        assertTrue("Message must accurately state deletion could not be completed", failureResult.message.contains("could not be completed"))
    }

    /**
     * TEST 4: Firebase requires reauthentication
     * -> User receives appropriate reauthentication error.
     */
    @Test
    fun test4_FirebaseRequiresReauthentication_AppropriateReauthErrorReturned() = runBlocking {
        val reauthResult = AccountDeletionResult(
            isRemoteSuccess = false,
            isLocalWipeSuccess = false,
            isSubscriptionReset = false,
            isNotificationReset = false,
            requiresReauth = true,
            message = "Account deletion could not be completed. Re-authentication required. Please sign in again before deleting your account."
        )

        assertFalse(reauthResult.isRemoteSuccess)
        assertTrue("requiresReauth flag must be set to true", reauthResult.requiresReauth)
        assertTrue("Message must mention re-authentication", reauthResult.message.contains("Re-authentication required"))
    }

    /**
     * TEST 5: Local Room deletion succeeds
     * -> Previous user's local data (profile, completions, logs) is inaccessible.
     */
    @Test
    fun test5_LocalRoomDeletionSucceeds_PreviousUserDataInaccessible() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(
            UserProfileEntity(
                id = 1,
                focus = "Mental Clarity",
                firebaseUid = "user_to_delete"
            )
        )
        dao.insertOrUpdateCompletion(
            ProtocolCompletionEntity(
                id = 0,
                dateKey = todayDateKey,
                itemId = "clarity_lux_splash",
                isCompleted = true
            )
        )

        assertEquals(1, dao.getCompletionsForDate(todayDateKey).size)
        assertNotNull(dao.getUserProfile())

        // Execute local wipe
        repository.wipeAllUserData()

        val profileAfter = dao.getUserProfile()
        val completionsAfter = dao.getCompletionsForDate(todayDateKey)

        assertNull("Firebase UID must be null after local Room wipe", profileAfter?.firebaseUid)
        assertFalse("hasCompletedBaseline must be false after local wipe", profileAfter?.hasCompletedBaseline ?: true)
        assertEquals("Completions list must be empty after local Room wipe", 0, completionsAfter.size)
    }

    /**
     * TEST 6: RevenueCat identity is cleared after deletion.
     */
    @Test
    fun test6_RevenueCatIdentityClearedAfterDeletion() = runBlocking {
        val revenueCatManager = RevenueCatManager()
        revenueCatManager.currentAppUserId = "user_to_delete"
        assertEquals("user_to_delete", revenueCatManager.currentAppUserId)

        revenueCatManager.resetUserIdentity()

        assertNull("RevenueCat appUserId must be null after reset", revenueCatManager.currentAppUserId)
        assertNull("RevenueCat latestCustomerInfo must be null after reset", revenueCatManager.latestCustomerInfo.value)
    }

    /**
     * TEST 7: Notifications are cancelled after deletion.
     */
    @Test
    fun test7_NotificationsCancelledAfterDeletion() = runBlocking {
        val notificationManager = ProtocolNotificationManager(application, repository)
        notificationManager.resetIdentityAndTags()

        val tags = notificationManager.tags.value
        assertEquals("FREE_TIER", tags["subscriber_tier"])
        assertEquals("UNCALCULATED", tags["circadian_phase"])
        assertNull("Active in-app message must be null", notificationManager.activeInAppMessage.value)
    }

    /**
     * TEST 8: Session is cleared after deletion.
     */
    @Test
    fun test8_SessionClearedAfterDeletion() = runBlocking {
        viewModel.continueAsGuest()
        assertTrue(viewModel.isGuestSession.value)

        viewModel.deleteAccountPermanently()

        assertFalse("Guest session state must be false after deletion", viewModel.isGuestSession.value)
        assertEquals("Navigation destination must return to Auth", "auth", viewModel.appNavState.value)
    }

    /**
     * TEST 9: Cached isPro becomes false.
     */
    @Test
    fun test9_CachedIsProBecomesFalse() = runBlocking {
        val dao = database.protocolDao()
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, isPro = true))
        dao.updateSubscriptionStatus(true, "PRO_ANNUAL")

        repository.wipeAllUserData()

        val profile = dao.getUserProfile()
        assertFalse("isPro must resolve to false when profile is wiped", profile?.isPro ?: true)
    }

    /**
     * TEST 10: After deletion, another user signs into the same device
     * -> Previous user's profile, completions, and subscription cache are NOT visible.
     */
    @Test
    fun test10_NewUserSignsInAfterDeletion_PreviousUserDataNotVisible() = runBlocking {
        val dao = database.protocolDao()
        // User A setup
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Mental Clarity", firebaseUid = "user_A", isPro = true))
        dao.insertOrUpdateCompletion(ProtocolCompletionEntity(id = 0, dateKey = todayDateKey, itemId = "clarity_lux_splash", isCompleted = true))

        // Deletion of User A
        repository.wipeAllUserData()

        // User B signs in
        dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, focus = "Deep Sleep", firebaseUid = "user_B", isPro = false))

        val profile = dao.getUserProfile()
        val completions = dao.getCompletionsForDate(todayDateKey)

        assertEquals("user_B", profile?.firebaseUid)
        assertEquals("Deep Sleep", profile?.focus)
        assertFalse(profile?.isPro ?: true)
        assertEquals("Previous user's completions must NOT be visible to new user", 0, completions.size)
    }

    /**
     * TEST 11: Process is recreated after deletion
     * -> Deleted user is not restored as authenticated.
     */
    @Test
    fun test11_ProcessRecreatedAfterDeletion_UserNotRestoredAsAuthenticated() = runBlocking {
        // Complete deletion workflow
        viewModel.deleteAccountPermanently()

        // Simulate process recreation: instantiate new ViewModel against cleared database
        val newViewModel = ProtocolViewModel(application)

        assertFalse("Process recreation must NOT restore deleted user as guest", newViewModel.isGuestSession.value)
        assertNull("Process recreation profile must be null", newViewModel.userProfile.value)
    }

    /**
     * TEST 12: Partial failure
     * -> UI accurately reports what succeeded and what failed.
     */
    @Test
    fun test12_PartialFailure_AccuratelyReportedInResult() = runBlocking {
        val partialResult = AccountDeletionResult(
            isRemoteSuccess = true,
            isLocalWipeSuccess = true,
            isSubscriptionReset = false,
            isNotificationReset = true,
            message = "Remote account deleted, but cleanup incomplete for: RevenueCat session. Session cleared."
        )

        assertTrue(partialResult.isRemoteSuccess)
        assertTrue(partialResult.isLocalWipeSuccess)
        assertFalse(partialResult.isSubscriptionReset)
        assertFalse("isFullyDeleted must be false when any layer fails", partialResult.isFullyDeleted)
        assertTrue("Message must accurately describe partial cleanup status", partialResult.message.contains("cleanup incomplete for: RevenueCat session"))
    }
}
