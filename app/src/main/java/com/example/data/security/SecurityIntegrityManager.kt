package com.example.data.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * SecurityIntegrityManager provides multi-layer anti-modding, anti-tampering,
 * and environment verification defenses:
 *
 * 1. ZERO CLIENT-SIDE ADMIN DEFENSE:
 *    There are no client-side admin routes or elevated permissions available.
 *    Any attempted elevation in smali or storage is neutralized.
 *
 * 2. RE-PACKAGING & SIGNATURE VERIFICATION:
 *    Detects unauthorized APK re-signing or modified certificates typical of
 *    modded APKs (e.g. Lucky Patcher, APK Editor, Smali injectors).
 *
 * 3. HOOKING & DYNAMIC INSTRUMENTATION DETECTION:
 *    Scans for active Frida runtime agents, Xposed Bridge hooks, and attached debuggers.
 *
 * 4. ROOT & COMPROMISED ENVIRONMENT DETECTION:
 *    Inspects standard superuser binaries, build tags, and unsafe system paths.
 */
data class SecurityReport(
    val isClean: Boolean,
    val isTampered: Boolean,
    val isHooked: Boolean,
    val isRooted: Boolean,
    val detectionReasons: List<String>
)

object SecurityIntegrityManager {

    private const val TAG = "SecurityIntegrity"
    private const val EXPECTED_PACKAGE_NAME = "com.aistudio.protocol.wellness"

    /**
     * Requirement 20: Trusted certificate SHA-256 digests (uppercase hex without colons).
     * Establishes cryptographic trust by comparing actual signing certificates against expected digests.
     */
    val EXPECTED_CERTIFICATE_DIGESTS: Set<String> = setOf(
        // Protocol Official Production Release Key
        "B412F84973C25971A16689E2844521CD88935A6194021180FF23AA894CE19243",
        // Local Android SDK Debug Keystore Key (Used in development & test runners)
        "24EA989E836653F7E2245C7D5218DF1360CB338B68DC5790C7456673C9DC14E3"
    )

    private val KNOWN_ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    /**
     * Conducts a complete security audit of the application environment.
     * Note: This provides diagnostic environment telemetry. Subscription security
     * is strictly anchored in Google Play Billing / RevenueCat signed receipts,
     * NOT client-side anti-tamper flags.
     */
    suspend fun auditAppEnvironment(context: Context): SecurityReport = withContext(Dispatchers.IO) {
        val reasons = mutableListOf<String>()

        // 1. Package Name & Origin Integrity
        val currentPackage = context.packageName
        if (currentPackage != EXPECTED_PACKAGE_NAME) {
            reasons.add("Package spoofing detected: $currentPackage")
        }

        // 2. Signature & Re-Packaging Verification against EXPECTED_CERTIFICATE_DIGESTS
        val isSignatureTampered = checkSignatureTamper(context)
        if (isSignatureTampered) {
            reasons.add("Application binary signature was altered or signed by an untrusted certificate")
        }

        // 3. Dynamic Instrumentation (Frida / Xposed / Debugger)
        val isHooked = checkDynamicInstrumentation()
        if (isHooked) {
            reasons.add("Dynamic instrumentation or runtime hook detected")
        }

        // 4. Root & System Integrity
        val isRooted = checkRootStatus()
        if (isRooted) {
            reasons.add("Elevated root environment detected")
        }

        val isTampered = isSignatureTampered || (currentPackage != EXPECTED_PACKAGE_NAME)
        val isClean = reasons.isEmpty()

        if (!isClean) {
            Log.w(TAG, "Security integrity alerts: ${reasons.joinToString("; ")}")
        }

        SecurityReport(
            isClean = isClean,
            isTampered = isTampered,
            isHooked = isHooked,
            isRooted = isRooted,
            detectionReasons = reasons
        )
    }

    /**
     * Computes the SHA-256 fingerprint of the certificate bytes.
     */
    fun computeCertificateSha256(certBytes: ByteArray): String {
        if (certBytes.isEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certBytes)
        return digest.joinToString("") { "%02X".format(it) }.uppercase()
    }

    /**
     * Requirement 20: Validates that the certificate SHA-256 matches an expected trusted digest.
     */
    fun verifyCertificateDigest(
        certBytes: ByteArray,
        trustedDigests: Set<String> = EXPECTED_CERTIFICATE_DIGESTS
    ): Boolean {
        if (certBytes.isEmpty()) return false
        val computedHex = computeCertificateSha256(certBytes)
        return trustedDigests.contains(computedHex)
    }

    /**
     * Checks if the APK certificate or signing chain matches expected trusted digests.
     */
    private fun checkSignatureTamper(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) {
                // Missing signatures indicate corrupted or stripped APK metadata
                return true
            }

            // Verify that at least one certificate matches expected trusted digests
            val hasValidTrustedSignature = signatures.any { sig ->
                verifyCertificateDigest(sig.toByteArray(), EXPECTED_CERTIFICATE_DIGESTS)
            }

            !hasValidTrustedSignature // true if untrusted or modified
        } catch (e: Exception) {
            Log.e(TAG, "Signature check error", e)
            false
        }
    }

    /**
     * Detects Frida hooks, Xposed bridge, or active debugger attachments.
     */
    private fun checkDynamicInstrumentation(): Boolean {
        // A. Debugger check
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        // B. Xposed framework check
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: ClassNotFoundException) {
            // Safe
        } catch (_: Throwable) {
            return true
        }

        // C. Check memory maps for Frida or injected libraries
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val lower = line.lowercase()
                        if (lower.contains("frida") || lower.contains("gadget") || lower.contains("xposed")) {
                            return true
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore read errors
        }

        // D. Check Frida standard server port (27042)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 27042), 50)
                return true
            }
        } catch (_: Exception) {
            // Normal behavior: connection refused means no Frida server on default port
        }

        return false
    }

    /**
     * Checks for known superuser binaries and test-keys build tags.
     */
    private fun checkRootStatus(): Boolean {
        // Check build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // Check common su binary paths
        for (path in KNOWN_ROOT_PATHS) {
            try {
                val file = File(path)
                if (file.exists()) {
                    return true
                }
            } catch (_: Exception) {
                // Ignore permission denial
            }
        }

        return false
    }

    /**
     * Validates that any user profile or session data adheres strictly to unprivileged
     * standard member boundaries. Enforces Zero Client-Side Admin.
     */
    fun sanitizeAccountRole(role: String?): String {
        // Under no circumstances can any account assume an Admin or elevated role on the client
        return "Member"
    }
}
