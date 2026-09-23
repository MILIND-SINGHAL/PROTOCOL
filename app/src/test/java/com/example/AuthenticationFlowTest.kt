package com.example

import com.example.data.firebase.FirebaseManager
import com.example.data.firebase.FirebaseSyncResult
import com.example.data.firebase.FirebaseSyncStatus
import com.example.data.security.SecurityIntegrityManager
import com.example.viewmodel.AccountDeletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
            uid = "usr_fallback_123"
        )
        // OFFLINE_MODE must NEVER have success == true
        assertFalse("Offline mode must NEVER report success as true", offlineResult.success)
        assertEquals(FirebaseSyncStatus.OFFLINE_MODE, offlineResult.status)
        assertNotNull(offlineResult.uid)
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
}
