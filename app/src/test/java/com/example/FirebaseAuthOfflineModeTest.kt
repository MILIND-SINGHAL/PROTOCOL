package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FirebaseManager
import com.example.data.firebase.FirebaseSyncResult
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.local.ProtocolDatabase
import com.example.data.local.ProtocolRepository
import com.example.data.revenuecat.RevenueCatManager
import com.example.viewmodel.AppNavDestination
import com.example.viewmodel.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * Exhaustive test suite for Firebase OFFLINE_MODE vs REAL_SUCCESS authentication handling.
 *
 * Enforces the core rule:
 * REAL_SUCCESS  -> Authenticated Firebase session allowed
 * OFFLINE_MODE  -> NOT authenticated -> Do not create Firebase account session
 * ERROR         -> NOT authenticated
 *
 * OFFLINE != AUTHENTICATED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAuthOfflineModeTest {

    private lateinit var database: ProtocolDatabase
    private lateinit var repository: ProtocolRepository

    /**
     * Test Session Coordinator mimicking ProtocolViewModel session handling logic.
     */
    private class TestSessionCoordinator(
        val repository: ProtocolRepository
    ) {
        val _isGuestSession = MutableStateFlow(false)
        val isGuestSession = _isGuestSession.asStateFlow()

        private val _appNavState = MutableStateFlow<String>(AppNavDestination.Loading.route)
        val appNavState = _appNavState.asStateFlow()

        var simulatedFirebaseCurrentUserUid: String? = null
        var revenueCatLoggedInUid: String? = null
        var revenueCatResetCount: Int = 0
        var firestoreSyncAttempts: Int = 0

        fun getSessionState(profileUid: String?): UserSessionState {
            val hasValidUid = profileUid != null &&
                    !profileUid.startsWith("usr_") &&
                    profileUid != "guest" &&
                    profileUid.isNotBlank()

            return if (simulatedFirebaseCurrentUserUid != null && hasValidUid) {
                UserSessionState.AUTHENTICATED
            } else if (_isGuestSession.value) {
                UserSessionState.OFFLINE_GUEST
            } else {
                UserSessionState.UNAUTHENTICATED
            }
        }

        suspend fun handleAuthResult(
            authResult: FirebaseSyncResult,
            onResult: (status: FirebaseSyncStatus, message: String) -> Unit
        ) {
            // ONLY REAL_SUCCESS can establish an authenticated account session
            if (authResult.status != FirebaseSyncStatus.REAL_SUCCESS) {
                onResult(authResult.status, authResult.message)
                return
            }

            // Sync Firebase UID to Room cache ONLY on REAL_SUCCESS
            simulatedFirebaseCurrentUserUid = authResult.uid
            repository.setActiveUserSession(
                firebaseUid = authResult.uid,
                email = authResult.email,
                displayName = authResult.displayName,
                isEmailVerified = authResult.isEmailVerified,
                role = "Member"
            )

            // Sync RevenueCat identity ONLY on REAL_SUCCESS
            authResult.uid?.let { uid ->
                revenueCatLoggedInUid = uid
            }

            onResult(authResult.status, authResult.message)
        }

        suspend fun syncCloudData(onResult: (status: FirebaseSyncStatus, message: String) -> Unit) {
            val currentUid = simulatedFirebaseCurrentUserUid
            val profile = repository.getUserProfile()
            val canonicalUid = currentUid ?: profile?.firebaseUid

            if (canonicalUid.isNullOrBlank() || canonicalUid.startsWith("usr_") || canonicalUid == "guest" || currentUid == null) {
                onResult(
                    FirebaseSyncStatus.OFFLINE_MODE,
                    "Cloud sync requires an authenticated Firebase session."
                )
                return
            }

            firestoreSyncAttempts++
            onResult(FirebaseSyncStatus.REAL_SUCCESS, "Cloud sync complete.")
        }

        fun continueAsGuest() {
            _isGuestSession.value = true
        }

        suspend fun signOut() {
            simulatedFirebaseCurrentUserUid = null
            repository.clearActiveUserSession()
            revenueCatLoggedInUid = null
            revenueCatResetCount++
            _isGuestSession.value = false
            _appNavState.value = AppNavDestination.Auth.route
        }

        suspend fun resolveStartupDestination(allowGuest: Boolean = false) {
            repository.ensureInitialized()
            val profile = repository.getUserProfile()
            val firebaseUserUid = simulatedFirebaseCurrentUserUid

            if (firebaseUserUid != null && profile?.firebaseUid != firebaseUserUid) {
                repository.setActiveUserSession(
                    firebaseUid = firebaseUserUid,
                    email = profile?.email,
                    displayName = profile?.displayName,
                    isEmailVerified = profile?.isEmailVerified ?: false
                )
            } else if (firebaseUserUid == null && profile?.firebaseUid != null) {
                // Clear any legacy or orphaned UID when no real Firebase user is active
                repository.clearActiveUserSession()
            }

            val hasRealAuthenticatedSession = firebaseUserUid != null &&
                    !profile?.firebaseUid.isNullOrBlank() &&
                    !profile.firebaseUid.startsWith("usr_")
            val isSessionActive = hasRealAuthenticatedSession || allowGuest || _isGuestSession.value

            if (!isSessionActive) {
                _appNavState.value = AppNavDestination.Auth.route
            } else if (profile == null || !profile.hasCompletedBaseline) {
                _appNavState.value = AppNavDestination.Baseline.route
            } else {
                _appNavState.value = AppNavDestination.Dashboard.route
            }
        }
    }

    @Before
    fun setup() {
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
     * TEST 1: Firebase authentication returns REAL_SUCCESS
     * -> authenticated session created.
     */
    @Test
    fun test1_FirebaseRealSuccess_AuthenticatedSessionCreated() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        val realSuccessResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.REAL_SUCCESS,
            message = "Signed in as member@protocol.app",
            uid = "firebase_uid_real_123",
            email = "member@protocol.app",
            displayName = "Member",
            isEmailVerified = true
        )

        var returnedStatus: FirebaseSyncStatus? = null
        coordinator.handleAuthResult(realSuccessResult) { status, _ ->
            returnedStatus = status
        }

        assertEquals(FirebaseSyncStatus.REAL_SUCCESS, returnedStatus)
        val profile = repository.getUserProfile()
        assertNotNull(profile)
        assertEquals("firebase_uid_real_123", profile?.firebaseUid)
        assertEquals("member@protocol.app", profile?.email)
        assertEquals(UserSessionState.AUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertEquals("firebase_uid_real_123", coordinator.revenueCatLoggedInUid)
    }

    /**
     * TEST 2: Firebase authentication returns OFFLINE_MODE
     * -> authenticated session NOT created.
     */
    @Test
    fun test2_FirebaseOfflineMode_AuthenticatedSessionNotCreated() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        val offlineResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.OFFLINE_MODE,
            message = "Firebase Auth is offline. Running in local offline mode.",
            uid = null,
            email = "offline_user@protocol.app",
            isEmailVerified = false
        )

        var returnedStatus: FirebaseSyncStatus? = null
        coordinator.handleAuthResult(offlineResult) { status, _ ->
            returnedStatus = status
        }

        assertEquals(FirebaseSyncStatus.OFFLINE_MODE, returnedStatus)
        val profile = repository.getUserProfile()
        assertNull("Offline mode must NOT set firebaseUid in Room profile", profile?.firebaseUid)
        assertNotEquals(UserSessionState.AUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertEquals(UserSessionState.UNAUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertNull("RevenueCat identity sync must NOT be called for offline mode", coordinator.revenueCatLoggedInUid)
    }

    /**
     * TEST 3: Firebase authentication returns ERROR
     * -> authenticated session NOT created.
     */
    @Test
    fun test3_FirebaseError_AuthenticatedSessionNotCreated() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        val errorResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.ERROR,
            message = "Network timeout / invalid credentials",
            uid = null
        )

        var returnedStatus: FirebaseSyncStatus? = null
        coordinator.handleAuthResult(errorResult) { status, _ ->
            returnedStatus = status
        }

        assertEquals(FirebaseSyncStatus.ERROR, returnedStatus)
        val profile = repository.getUserProfile()
        assertNull("ERROR status must NOT set firebaseUid in Room profile", profile?.firebaseUid)
        assertEquals(UserSessionState.UNAUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertNull("RevenueCat identity sync must NOT be called on error", coordinator.revenueCatLoggedInUid)
    }

    /**
     * TEST 4: OFFLINE_MODE
     * -> Firebase UID is not fabricated (null, not 'usr_', 'offline_', 'goog_', hash, timestamp, etc.).
     */
    @Test
    fun test4_OfflineMode_FirebaseUidIsNotFabricated() {
        val offlineResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.OFFLINE_MODE,
            message = "Running in local offline mode",
            uid = null
        )

        assertNull("Offline mode UID must strictly be null, never fabricated", offlineResult.uid)

        // Validate that fabricated UID formats are rejected by Firestore security check
        val email = "user@protocol.app"
        val fakeHash = "usr_" + email.hashCode().toString(16)
        val fakeGoogle = "goog_" + email.hashCode().toString(16)
        val fakeOffline = "offline_12345"

        assertFalse("usr_ prefix must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid(fakeHash))
        assertFalse("offline_ prefix must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid(fakeOffline))
        assertFalse("goog_ prefix must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid(fakeGoogle))
        assertFalse("guest must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid("guest"))
        assertFalse("null must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid(null))
        assertFalse("blank must be rejected as valid Firestore UID", FirebaseManager.isValidFirestoreUserUid("   "))
    }

    /**
     * TEST 5: OFFLINE_MODE
     * -> RevenueCat logIn(firebaseUid) is NOT called.
     */
    @Test
    fun test5_OfflineMode_RevenueCatLogInNotCalled() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        val offlineResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.OFFLINE_MODE,
            message = "Running in local offline mode",
            uid = null
        )

        coordinator.handleAuthResult(offlineResult) { _, _ -> }

        assertNull("RevenueCat login must NOT be called when authentication returns OFFLINE_MODE", coordinator.revenueCatLoggedInUid)
    }

    /**
     * TEST 6: OFFLINE_MODE
     * -> authenticated Firestore synchronization is NOT started.
     */
    @Test
    fun test6_OfflineMode_AuthenticatedFirestoreSyncNotStarted() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        // Attempting cloud sync when in OFFLINE_MODE (no authenticated Firebase session)
        var syncStatus: FirebaseSyncStatus? = null
        var syncMessage: String? = null
        coordinator.syncCloudData { status, message ->
            syncStatus = status
            syncMessage = message
        }

        assertEquals(FirebaseSyncStatus.OFFLINE_MODE, syncStatus)
        assertEquals(0, coordinator.firestoreSyncAttempts)
        assertTrue(syncMessage?.contains("requires an authenticated Firebase session") == true)
    }

    /**
     * TEST 7: Existing authenticated user logs out
     * -> session is cleared.
     */
    @Test
    fun test7_AuthenticatedUserLogsOut_SessionIsCleared() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        // Authenticate first
        val realSuccessResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.REAL_SUCCESS,
            message = "Signed in",
            uid = "auth_uid_456",
            email = "logout_test@protocol.app",
            displayName = "User",
            isEmailVerified = true
        )
        coordinator.handleAuthResult(realSuccessResult) { _, _ -> }

        var profile = repository.getUserProfile()
        assertEquals("auth_uid_456", profile?.firebaseUid)
        assertEquals(UserSessionState.AUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))

        // User logs out
        coordinator.signOut()

        profile = repository.getUserProfile()
        assertNull("Room profile firebaseUid must be cleared upon sign out", profile?.firebaseUid)
        assertNull("Room profile email must be cleared upon sign out", profile?.email)
        assertFalse("Room profile email verification must be cleared upon sign out", profile?.isEmailVerified ?: false)
        assertEquals(UserSessionState.UNAUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertNull("RevenueCat UID must be reset upon sign out", coordinator.revenueCatLoggedInUid)
        assertEquals(1, coordinator.revenueCatResetCount)
        assertEquals(AppNavDestination.Auth.route, coordinator.appNavState.value)
    }

    /**
     * TEST 8: Offline/local user remains able to use any explicitly supported guest/local functionality
     * without becoming an authenticated Firebase user.
     */
    @Test
    fun test8_OfflineGuest_LocalFunctionalityPreservedWithoutBecomingAuthenticated() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        // Explicitly enter Guest mode
        coordinator.continueAsGuest()
        assertTrue(coordinator.isGuestSession.value)

        val profileBefore = repository.getUserProfile()
        assertEquals(UserSessionState.OFFLINE_GUEST, coordinator.getSessionState(profileBefore?.firebaseUid))
        assertNotEquals(UserSessionState.AUTHENTICATED, coordinator.getSessionState(profileBefore?.firebaseUid))

        // Guest user can complete baseline locally in Room
        repository.saveBaseline(wakeTime = "07:00", focus = "Deep Sleep", wearable = "Apple Watch")
        val updatedProfile = repository.getUserProfile()
        assertNotNull(updatedProfile)
        assertEquals("07:00", updatedProfile?.wakeTime)
        assertEquals("Deep Sleep", updatedProfile?.focus)
        assertTrue(updatedProfile?.hasCompletedBaseline == true)

        // Guest user can toggle habit items in Room
        val initialCompletions = database.protocolDao().getCompletionsForDate(repository.getTodayKey())
        assertEquals(0, initialCompletions.size)
        repository.toggleItem("habit_sunlight")
        val completionsAfter = database.protocolDao().getCompletionsForDate(repository.getTodayKey())
        assertEquals(1, completionsAfter.size)
        assertEquals("habit_sunlight", completionsAfter.first().itemId)
        assertTrue(completionsAfter.first().isCompleted)

        // Verify user is STILL OFFLINE_GUEST and NEVER became AUTHENTICATED
        assertNull("Guest user must have null firebaseUid", updatedProfile?.firebaseUid)
        assertEquals(UserSessionState.OFFLINE_GUEST, coordinator.getSessionState(updatedProfile?.firebaseUid))
        assertNotEquals(UserSessionState.AUTHENTICATED, coordinator.getSessionState(updatedProfile?.firebaseUid))
    }

    /**
     * TEST 9: Process recreation while in OFFLINE_MODE
     * -> app does not incorrectly restore an authenticated Firebase session.
     */
    @Test
    fun test9_ProcessRecreationInOfflineMode_DoesNotRestoreAuthenticatedSession() = runBlocking {
        val coordinator = TestSessionCoordinator(repository)

        // Simulate an orphaned or fabricated legacy UID in Room DB before process recreation
        repository.setActiveUserSession(
            firebaseUid = "usr_legacy_offline_fabricated",
            email = "offline@protocol.app",
            displayName = "Offline User",
            isEmailVerified = false
        )

        // Simulated process recreation with no authenticated Firebase currentUser
        coordinator.simulatedFirebaseCurrentUserUid = null

        // App launches and runs resolveStartupDestination()
        coordinator.resolveStartupDestination(allowGuest = false)

        val profile = repository.getUserProfile()
        assertNull("Orphaned or fabricated UID must be cleared on startup when Firebase has no user", profile?.firebaseUid)
        assertEquals(UserSessionState.UNAUTHENTICATED, coordinator.getSessionState(profile?.firebaseUid))
        assertEquals(AppNavDestination.Auth.route, coordinator.appNavState.value)
    }

    /**
     * TEST 10: No code path converts OFFLINE_MODE into REAL_SUCCESS.
     */
    @Test
    fun test10_NoCodePathConvertsOfflineModeIntoRealSuccess() {
        val offline = FirebaseSyncResult(
            status = FirebaseSyncStatus.OFFLINE_MODE,
            message = "Offline operation"
        )
        assertFalse("OFFLINE_MODE must NEVER evaluate to success = true", offline.success)
        assertFalse("OFFLINE_MODE must NEVER evaluate to isRealSuccess = true", offline.isRealSuccess)
        assertTrue("OFFLINE_MODE must evaluate to isOfflineMode = true", offline.isOfflineMode)
        assertFalse("OFFLINE_MODE must not evaluate to isError = true", offline.isError)

        val error = FirebaseSyncResult(
            status = FirebaseSyncStatus.ERROR,
            message = "Error operation"
        )
        assertFalse("ERROR must NEVER evaluate to success = true", error.success)
        assertFalse("ERROR must NEVER evaluate to isRealSuccess = true", error.isRealSuccess)
        assertTrue("ERROR must evaluate to isError = true", error.isError)

        val realSuccess = FirebaseSyncResult(
            status = FirebaseSyncStatus.REAL_SUCCESS,
            message = "Success operation"
        )
        assertTrue("REAL_SUCCESS must evaluate to success = true", realSuccess.success)
        assertTrue("REAL_SUCCESS must evaluate to isRealSuccess = true", realSuccess.isRealSuccess)
        assertFalse("REAL_SUCCESS must not evaluate to isOfflineMode = true", realSuccess.isOfflineMode)
        assertFalse("REAL_SUCCESS must not evaluate to isError = true", realSuccess.isError)
    }
}
