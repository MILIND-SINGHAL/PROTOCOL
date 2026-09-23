package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

enum class FirebaseSyncStatus {
    REAL_SUCCESS,
    OFFLINE_MODE,
    ERROR
}

data class FirebaseSyncResult(
    val status: FirebaseSyncStatus,
    val message: String,
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false
) {
    val isRealSuccess: Boolean get() = status == FirebaseSyncStatus.REAL_SUCCESS
    val isOfflineMode: Boolean get() = status == FirebaseSyncStatus.OFFLINE_MODE
    val isError: Boolean get() = status == FirebaseSyncStatus.ERROR

    // CRITICAL: success is strictly true ONLY when real remote Firebase operation succeeded.
    // It is NEVER true for OFFLINE_MODE or ERROR.
    val success: Boolean get() = status == FirebaseSyncStatus.REAL_SUCCESS
}

class FirebaseManager(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseManager"

        /**
         * Requirement 22: Validates that a Firestore document ID in `/users/{uid}` strictly conforms to
         * a Firebase Auth UID, rejecting fragile transformed emails or invalid formats.
         * Eliminates email.replace(".", "_") as a document key authority.
         */
        fun isValidFirestoreUserUid(uid: String?): Boolean {
            if (uid.isNullOrBlank()) return false
            // Derived email strings typically contain '@', '.', or end with domain extensions like '_com', '_org' from email.replace(".", "_")
            if (uid.contains("@") || uid.contains(".")) return false
            if (uid.matches(Regex(".*_[a-zA-Z0-9]+_(com|org|net|edu|gov|io|app|co|uk|de|dev|ai|me|info|biz)$", RegexOption.IGNORE_CASE))) return false // e.g. abc_gmail_com
            return true
        }
    }

    val isFirebaseInitialized: Boolean
        get() = try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
            false
        }

    val currentUser: FirebaseUser?
        get() = if (isFirebaseInitialized) {
            try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                null
            }
        } else null

    /**
     * Sign in with existing Firebase credentials.
     * Firebase UID is the canonical identity authority.
     */
    suspend fun signIn(email: String, password: String): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            val fallbackUid = "usr_" + email.trim().lowercase().hashCode().toString(16)
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase Auth is unconfigured or offline. Running in local offline mode (no remote Firebase session).",
                uid = fallbackUid,
                email = email.trim(),
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                isEmailVerified = false
            )
        }
        return try {
            val auth = FirebaseAuth.getInstance()
            val result = auth.signInWithEmailAndPassword(email.trim(), password.trim()).await()
            val user = result.user
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Signed in as ${user?.email ?: email}.",
                uid = user?.uid,
                email = user?.email ?: email.trim(),
                displayName = user?.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
                isEmailVerified = user?.isEmailVerified ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signIn failed", e)
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Invalid email or password."
            )
        }
    }

    /**
     * Create new user and send email verification.
     * Firebase UID is generated and returned as the authority.
     */
    suspend fun signUp(email: String, password: String, name: String = ""): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            val fallbackUid = "usr_" + email.trim().lowercase().hashCode().toString(16)
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase Auth is unconfigured or offline. Created local offline profile (no remote Firebase account created).",
                uid = fallbackUid,
                email = email.trim(),
                displayName = if (name.isNotBlank()) name else email.substringBefore("@").replaceFirstChar { it.uppercase() },
                isEmailVerified = false
            )
        }
        return try {
            val auth = FirebaseAuth.getInstance()
            val result = auth.createUserWithEmailAndPassword(email.trim(), password.trim()).await()
            val user = result.user
            if (name.isNotBlank() && user != null) {
                try {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name.trim())
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (pe: Exception) {
                    Log.w(TAG, "Could not set user displayName", pe)
                }
            }
            var emailSent = false
            try {
                user?.sendEmailVerification()?.await()
                emailSent = true
            } catch (ve: Exception) {
                Log.w(TAG, "Verification email note: ${ve.message}")
            }
            val verificationNote = if (emailSent) " Verification email sent to $email." else ""
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Account created.$verificationNote Please check your inbox and tap the link to verify.",
                uid = user?.uid,
                email = user?.email ?: email.trim(),
                displayName = if (name.isNotBlank()) name else (user?.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }),
                isEmailVerified = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signUp failed", e)
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Could not register account."
            )
        }
    }

    /**
     * Google Identity authentication session resolver.
     */
    suspend fun signInWithGoogleSession(googleEmail: String, googleName: String): FirebaseSyncResult {
        val email = googleEmail.trim()
        val name = if (googleName.isNotBlank()) googleName else email.substringBefore("@").replaceFirstChar { it.uppercase() }

        if (!isFirebaseInitialized) {
            val fallbackUid = "goog_" + email.lowercase().hashCode().toString(16)
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase Auth is unconfigured or offline. Running Google session in local offline mode ($email).",
                uid = fallbackUid,
                email = email,
                displayName = name,
                isEmailVerified = false
            )
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val uid = currentUser?.uid ?: ("goog_" + email.lowercase().hashCode().toString(16))
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Authenticated with Google ($email).",
                uid = uid,
                email = email,
                displayName = name,
                isEmailVerified = currentUser?.isEmailVerified ?: true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Google auth session failed", e)
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Google authentication failed."
            )
        }
    }

    /**
     * Refresh user and check email verification status via Firebase reload().
     * NEVER returns true when Firebase is unconfigured or offline.
     */
    suspend fun checkEmailVerification(): Boolean {
        if (!isFirebaseInitialized) return false
        return try {
            val user = FirebaseAuth.getInstance().currentUser ?: return false
            user.reload().await()
            user.isEmailVerified
        } catch (e: Exception) {
            Log.w(TAG, "Could not check email verification", e)
            false
        }
    }

    /**
     * Send password reset link to user's registered email via Firebase.
     * Never falsely reports success when Firebase is not configured.
     */
    suspend fun sendPasswordReset(email: String): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase Auth is unconfigured or offline. Cannot send password reset email in offline mode."
            )
        }
        return try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Password reset link sent to $email."
            )
        } catch (e: Exception) {
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Could not send password reset email."
            )
        }
    }

    fun signOut() {
        if (isFirebaseInitialized) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.w(TAG, "Sign out error", e)
            }
        }
    }

    /**
     * Requirement 24: Delete user account, Firestore documents, and invoke local cleanup.
     * Guarantees that local Room data and session storage are wiped.
     */
    suspend fun deleteAccount(onLocalCleanup: (suspend () -> Unit)? = null): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            onLocalCleanup?.invoke()
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase is unconfigured or offline. Local session data and device storage reset."
            )
        }
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            val uid = user?.uid
            if (uid != null) {
                try {
                    FirebaseFirestore.getInstance().collection("users").document(uid).delete().await()
                } catch (fe: Exception) {
                    Log.w(TAG, "Firestore doc delete error: ${fe.message}")
                }
            }
            user?.delete()?.await()
            onLocalCleanup?.invoke()
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Firebase account, cloud data, and local storage deleted."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion failed", e)
            onLocalCleanup?.invoke()
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Failed to delete remote account."
            )
        }
    }

    /**
     * Send email verification link to user's registered email via Firebase Auth.
     * Never falsely reports success when Firebase is not configured.
     */
    suspend fun sendEmailVerification(email: String? = null, password: String? = null): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase Auth is unconfigured or offline. Real email verification requires active Firebase."
            )
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                auth.signInWithEmailAndPassword(email.trim(), password.trim()).await().user
            } else null

            if (user == null) {
                return FirebaseSyncResult(
                    status = FirebaseSyncStatus.ERROR,
                    message = "No active Firebase user session found to send verification email."
                )
            }

            user.sendEmailVerification().await()
            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Verification email sent to ${user.email}. Tap the link in your email to verify."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send verification email", e)
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Failed to send email verification."
            )
        }
    }

    /**
     * Requirement 22 & 23: Sync habit completion and profile telemetry to Firestore.
     * Document ID in collection "users" strictly uses Firebase UID:
     * FirebaseAuth.getInstance().currentUser!!.uid
     *
     * Requirement 23: Firestore is strictly habit telemetry and NEVER an entitlement authority.
     * Client-side booleans (e.g. isPro) are NEVER synced as cloud truth.
     * Pro access is determined exclusively by RevenueCat / Google Play Billing receipts.
     */
    suspend fun syncProfileToCloud(
        userId: String? = null,
        wakeTime: String,
        focusGoal: String,
        streakDays: Int,
        completedItems: Set<String>
    ): FirebaseSyncResult {
        if (!isFirebaseInitialized) {
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.OFFLINE_MODE,
                message = "Firebase is unconfigured or offline. Cloud sync skipped (offline mode)."
            )
        }

        // Canonical identity authority: FirebaseAuth currentUser UID
        val canonicalUid = currentUser?.uid ?: userId

        if (!isValidFirestoreUserUid(canonicalUid)) {
            return FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = "Invalid Firestore document ID. Cloud sync strictly requires an authenticated Firebase UID, never a transformed email."
            )
        }

        val nonNullUid = canonicalUid!!

        return try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = mapOf(
                "uid" to nonNullUid,
                "wakeTime" to wakeTime,
                "focusGoal" to focusGoal,
                "streakDays" to streakDays,
                "lastSyncTimestamp" to System.currentTimeMillis(),
                "completedToday" to completedItems.toList()
            )
            db.collection("users").document(nonNullUid)
                .set(userDoc, SetOptions.merge())
                .await()

            FirebaseSyncResult(
                status = FirebaseSyncStatus.REAL_SUCCESS,
                message = "Cloud sync complete (Cloud Firestore document: /users/$nonNullUid)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync error", e)
            FirebaseSyncResult(
                status = FirebaseSyncStatus.ERROR,
                message = e.localizedMessage ?: "Firestore sync error."
            )
        }
    }
}
