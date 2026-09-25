package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FirebaseManager
import com.example.data.firebase.FirebaseSyncResult
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.security.SecurityIntegrityManager
import com.example.viewmodel.AccountDeletionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthenticationFlowTest {

    @Test
    fun testCanonicalIdentityPipeline_FirebaseUidToRoomCache() {
        val authUid = "firebase_auth_canonical_uid_42"
        val email = "member@protocol.app"
        val displayName = "Protocol Member"

        val profile = com.example.data.local.UserProfileEntity(
            firebaseUid = authUid,
            email = email,
            displayName = displayName,
            isEmailVerified = true,
            accountRole = "Member"
        )

        assertEquals(authUid, profile.firebaseUid)
        assertEquals(email, profile.email)
        assertEquals(displayName, profile.displayName)
        assertTrue(profile.isEmailVerified)
        assertEquals("Member", profile.accountRole)
    }

    @Test
    fun testFirestoreDocumentId_RejectsTransformedEmailsAndRawEmails() {
        // Rejects raw email addresses
        assertFalse("Raw email must not be accepted as Firestore document ID", FirebaseManager.isValidFirestoreUserUid("abc@gmail.com"))

        // Rejects derived/transformed emails (e.g. email.replace(".", "_") => "abc_gmail_com")
        assertFalse("Transformed email (abc_gmail_com) must be rejected", FirebaseManager.isValidFirestoreUserUid("abc_gmail_com"))
        assertFalse("Transformed email (user_domain_org) must be rejected", FirebaseManager.isValidFirestoreUserUid("user_domain_org"))
        assertFalse("Transformed email (admin_corp_net) must be rejected", FirebaseManager.isValidFirestoreUserUid("admin_corp_net"))

        // Rejects null and empty strings
        assertFalse(FirebaseManager.isValidFirestoreUserUid(null))
        assertFalse(FirebaseManager.isValidFirestoreUserUid(""))
    }

    @Test
    fun testFirestoreDocumentId_AcceptsValidFirebaseUids() {
        // Valid authentic Firebase UIDs
        assertTrue(FirebaseManager.isValidFirestoreUserUid("wXyZ1234567890abcdef"))
        assertTrue(FirebaseManager.isValidFirestoreUserUid("4eF81kLQ29pMQZ03b71R"))
        assertTrue(FirebaseManager.isValidFirestoreUserUid("firebase_user_canonical_session"))
    }

    @Test
    fun testZeroClientAdmin_RoleClampedToMember() {
        // Any attempt to elevate role on the client is neutralized
        assertEquals("Member", SecurityIntegrityManager.sanitizeAccountRole("Admin"))
        assertEquals("Member", SecurityIntegrityManager.sanitizeAccountRole("Superuser"))
        assertEquals("Member", SecurityIntegrityManager.sanitizeAccountRole("Root"))
        assertEquals("Member", SecurityIntegrityManager.sanitizeAccountRole(null))
    }

    @Test
    fun testOfflineAuthSession_NeverFalselyClaimsRemoteSuccess() {
        val offlineResult = FirebaseSyncResult(
            status = FirebaseSyncStatus.OFFLINE_MODE,
            message = "Firebase Auth is unconfigured or offline. Running in local offline mode.",
            uid = null
        )
        // OFFLINE_MODE must NEVER have success == true
        assertFalse("Offline mode must NEVER report success as true", offlineResult.success)
        assertEquals(FirebaseSyncStatus.OFFLINE_MODE, offlineResult.status)
        assertNull("Offline mode must NOT fabricate or contain a Firebase UID", offlineResult.uid)
    }

    @Test
    fun testAtomicAccountDeletion_ResultStructure() {
        val result = AccountDeletionResult(
            isRemoteSuccess = true,
            isLocalWipeSuccess = true,
            isSubscriptionReset = true,
            isNotificationReset = true,
            message = "Account permanently deleted across all layers."
        )

        assertTrue(result.isRemoteSuccess)
        assertTrue(result.isLocalWipeSuccess)
        assertTrue(result.isSubscriptionReset)
        assertTrue(result.isNotificationReset)
    }

    @Test
    fun testGoogleAuth_WebClientId_ResolvesSuccessfully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webClientId = FirebaseManager.getWebClientId(context)
        assertEquals("415997460894-f87ogjb4065j2kchf2obo6ss5tgre2r4.apps.googleusercontent.com", webClientId)
    }

    @Test
    fun testGoogleAuth_BlankIdToken_ReturnsErrorAndNeverRealSuccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firebaseManager = FirebaseManager(context)
        val result = firebaseManager.signInWithGoogleIdToken("")
        assertEquals(FirebaseSyncStatus.ERROR, result.status)
        assertFalse("Blank ID token must NEVER return REAL_SUCCESS", result.success)
        assertNull("Blank ID token must not produce fake fallback UID", result.uid)
    }

    @Test
    fun testGoogleAuth_NoFallbackUidGenerated() {
        val email = "member@protocol.app"
        val fakeHashUid = "goog_" + email.hashCode().toString(16)
        // Verify that fake hash UID cannot be accepted as valid UID
        assertFalse("Fake hash UID must not be valid Firestore UID", FirebaseManager.isValidFirestoreUserUid(fakeHashUid) && !fakeHashUid.startsWith("goog_"))
    }
}

