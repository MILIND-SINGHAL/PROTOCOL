package com.example.data.revenuecat

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class RevenueCatPackage(
    val identifier: String,
    val title: String,
    val subtitle: String,
    val period: String,
    val hasTrial: Boolean = false,
    val trialDays: Int = 0
)

data class RevenueCatOffering(
    val identifier: String,
    val availablePackages: List<RevenueCatPackage>
)

sealed class PurchaseResult {
    data class Success(val entitlement: String, val transactionId: String) : PurchaseResult()
    data class Error(val message: String, val isCancelled: Boolean = false) : PurchaseResult()
}

class RevenueCatManager {

    companion object {
        const val TAG = "RevenueCatProtocol"
        const val PROJECT_ID = "projf4beb188"
        const val APP_ID = "app8c346dcd71"
        const val ENTITLEMENT_ID = "pro"
        // Configured Google Play API key (supports live goog_ keys configured by developer)
        const val API_KEY = ""
    }

    private val _isLiveConnected = MutableStateFlow(false)
    val isLiveConnected: StateFlow<Boolean> = _isLiveConnected.asStateFlow()

    private val _isSdkConfigured = MutableStateFlow(false)
    val isSdkConfigured: StateFlow<Boolean> = _isSdkConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _activeOfferingId = MutableStateFlow("default")
    val activeOfferingId: StateFlow<String> = _activeOfferingId.asStateFlow()

    private val _remoteOfferings = MutableStateFlow<Offerings?>(null)
    val remoteOfferings: StateFlow<Offerings?> = _remoteOfferings.asStateFlow()

    private val _latestCustomerInfo = MutableStateFlow<CustomerInfo?>(null)
    val latestCustomerInfo: StateFlow<CustomerInfo?> = _latestCustomerInfo.asStateFlow()

    /**
     * Callback invoked whenever RevenueCat yields updated CustomerInfo.
     * The single source of truth for "pro" entitlement is RevenueCat.
     */
    var onCustomerInfoUpdated: ((CustomerInfo) -> Unit)? = null

    private val standardOffering = RevenueCatOffering(
        identifier = "default",
        availablePackages = listOf(
            RevenueCatPackage(
                identifier = "\$rc_annual",
                title = "Annual Protocol",
                subtitle = "Includes 4-day free trial",
                period = "per year",
                hasTrial = true,
                trialDays = 4
            ),
            RevenueCatPackage(
                identifier = "\$rc_weekly",
                title = "Weekly Protocol",
                subtitle = "Flexible, cancel anytime",
                period = "per week",
                hasTrial = false
            )
        )
    )

    private val promoOffering = RevenueCatOffering(
        identifier = "shipaton_experiment_offer",
        availablePackages = listOf(
            RevenueCatPackage(
                identifier = "\$rc_annual_promo",
                title = "Founder Edition (25% Off)",
                subtitle = "Special founder rate with 4-day trial",
                period = "first year",
                hasTrial = true,
                trialDays = 4
            ),
            RevenueCatPackage(
                identifier = "\$rc_weekly",
                title = "Weekly Protocol",
                subtitle = "Standard weekly rate",
                period = "per week",
                hasTrial = false
            )
        )
    )

    val currentOffering: RevenueCatOffering
        get() = if (_activeOfferingId.value == "shipaton_experiment_offer") promoOffering else standardOffering

    fun isLiveKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.contains("placeholder", ignoreCase = true)) return false
        // Google Play RevenueCat keys start with 'goog_'
        return trimmed.startsWith("goog_") && trimmed.length >= 20
    }

    fun getCustomApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("revenuecat_prefs", Context.MODE_PRIVATE)
        return prefs.getString("custom_api_key", "") ?: ""
    }

    fun saveCustomApiKey(context: Context, key: String): Boolean {
        val trimmed = key.trim()
        val prefs = context.getSharedPreferences("revenuecat_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_api_key", trimmed).apply()

        return if (isLiveKey(trimmed)) {
            try {
                configureRevenueCat(context, trimmed)
                true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed live RevenueCat configuration with provided key: ${e.message}")
                _isLiveConnected.value = false
                false
            }
        } else {
            _isLiveConnected.value = false
            true
        }
    }

    private fun configureRevenueCat(context: Context, apiKey: String) {
        Purchases.logLevel = if (com.example.BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context.applicationContext, apiKey).build()
        )
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
            _latestCustomerInfo.value = customerInfo
            onCustomerInfoUpdated?.invoke(customerInfo)
        }
        _isSdkConfigured.value = true
        _isLiveConnected.value = true

        // Initial sync of customer info from RevenueCat
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                _latestCustomerInfo.value = customerInfo
                onCustomerInfoUpdated?.invoke(customerInfo)
            }
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "Error fetching initial CustomerInfo: ${error.message}")
            }
        })

        // Fetch remote offerings from RevenueCat dashboard
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                _remoteOfferings.value = offerings
                Log.d(TAG, "Loaded RevenueCat remote offerings: ${offerings.current?.identifier}")
            }
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "Error fetching RevenueCat remote offerings: ${error.message}")
            }
        })
    }

    /**
     * Initializes the RevenueCat integration with Google Play.
     * Never simulates purchases or returns fake entitlement states.
     */
    fun initialize(context: Context) {
        if (_isSdkConfigured.value && Purchases.isConfigured) return

        val savedCustomKey = getCustomApiKey(context)
        val keyToTest = if (savedCustomKey.isNotBlank()) savedCustomKey else API_KEY

        if (isLiveKey(keyToTest)) {
            try {
                configureRevenueCat(context, keyToTest)
                Log.d(TAG, "RevenueCat configured with Google Play key.")
            } catch (e: Throwable) {
                Log.w(TAG, "RevenueCat initialization failed: ${e.message}")
                _isLiveConnected.value = false
                _isSdkConfigured.value = false
            }
        } else {
            Log.d(TAG, "No Google Play RevenueCat key configured ('goog_...'). Real in-app purchases require a valid key.")
            _isLiveConnected.value = false
            _isSdkConfigured.value = false
        }
    }

    fun switchOffering(offeringId: String) {
        _activeOfferingId.value = offeringId
    }

    // Tracks the active RevenueCat customer identity
    var currentAppUserId: String? = null
        internal set

    // Optional delegate hook allowing unit tests to simulate identity sync results
    var identitySyncResolverForTesting: (suspend (String) -> CustomerInfo?)? = null

    // Optional delegate hook allowing unit tests to simulate logout behavior
    var logoutResolverForTesting: (suspend () -> Unit)? = null

    /**
     * Synchronizes RevenueCat customer identity with the canonical Firebase UID.
     * Calls Purchases.sharedInstance.logIn(firebaseUid).
     *
     * Strict Identity Architecture:
     * - RevenueCat identity MUST be FirebaseUser.uid.
     * - Prohibited: email, display name, email hash, generated ID, timestamp, random UUID, "goog_" + email, device ID.
     *
     * Returns updated CustomerInfo if login succeeds, or null if login fails.
     * Note: Identity login success does NOT grant Pro; Pro is strictly determined by authoritative CustomerInfo entitlement.
     */
    suspend fun syncRevenueCatIdentity(firebaseUid: String): CustomerInfo? {
        val trimmedUid = firebaseUid.trim()
        if (trimmedUid.isBlank()) {
            Log.w(TAG, "Cannot sync empty or blank Firebase UID to RevenueCat.")
            return null
        }

        // Validate that identity is not an email, email hash, or prohibited format
        if (trimmedUid.contains("@") || trimmedUid.contains(".")) {
            Log.w(TAG, "Invalid RevenueCat identity: email format rejected. Must be Firebase UID.")
            return null
        }
        if (trimmedUid.startsWith("goog_")) {
            Log.w(TAG, "Invalid RevenueCat identity: 'goog_' prefix rejected. Must be Firebase UID.")
            return null
        }

        currentAppUserId = trimmedUid

        // Test hook for unit testing
        identitySyncResolverForTesting?.let { testResolver ->
            val info = testResolver.invoke(trimmedUid)
            if (info != null) {
                _latestCustomerInfo.value = info
                onCustomerInfoUpdated?.invoke(info)
            } else {
                _latestCustomerInfo.value = null
            }
            return info
        }

        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            Log.d(TAG, "RevenueCat unconfigured or disconnected. Recorded customer identity: $trimmedUid")
            return null
        }

        return suspendCoroutine { cont ->
            Purchases.sharedInstance.logIn(trimmedUid, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    Log.d(TAG, "RevenueCat logIn success for UID: $trimmedUid, created: $created")
                    _latestCustomerInfo.value = customerInfo
                    onCustomerInfoUpdated?.invoke(customerInfo)
                    cont.resume(customerInfo)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "RevenueCat logIn failed for UID: $trimmedUid: ${error.message}")
                    cont.resume(null)
                }
            })
        }
    }

    // Delegate hook allowing unit tests to simulate authoritative entitlement results (active, inactive, error)
    var entitlementResolverForTesting: (suspend () -> Boolean?)? = null

    /**
     * Authoritative subscription verification.
     * Obtains current RevenueCat CustomerInfo and determines:
     * customerInfo.entitlements["pro"]?.isActive == true
     *
     * Returns true if "pro" entitlement is verified active.
     * Returns false if inactive, expired, network failure, or unconfigured.
     * NEVER returns true on error or fallback.
     */
    suspend fun getAuthoritativeProEntitlement(): Boolean {
        // Test delegate hook
        entitlementResolverForTesting?.let { testResolver ->
            val result = testResolver.invoke()
            if (result != null) return result
        }

        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            val cachedInfo = _latestCustomerInfo.value
            return cachedInfo?.entitlements?.get(ENTITLEMENT_ID)?.isActive == true
        }

        return suspendCoroutine { cont ->
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    _latestCustomerInfo.value = customerInfo
                    onCustomerInfoUpdated?.invoke(customerInfo)
                    val isEntitled = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    cont.resume(isEntitled)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "Error fetching authoritative CustomerInfo: ${error.message}")
                    // Conservative safety: verification/network errors must NEVER grant Pro access
                    cont.resume(false)
                }
            })
        }
    }

    fun refreshCustomerInfo(onComplete: ((CustomerInfo?) -> Unit)? = null) {
        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            onComplete?.invoke(null)
            return
        }

        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                _latestCustomerInfo.value = customerInfo
                onCustomerInfoUpdated?.invoke(customerInfo)
                onComplete?.invoke(customerInfo)
            }
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "Error refreshing customer info: ${error.message}")
                onComplete?.invoke(null)
            }
        })
    }

    suspend fun applyRetentionDiscount(activity: Activity?): PurchaseResult {
        return purchasePackage(activity, "\$rc_annual_retention_discount")
    }

    // Optional delegate hook allowing unit tests to simulate purchase execution with exact package matching
    var purchaseResolverForTesting: (suspend (String) -> PurchaseResult?)? = null

    // Optional delegate hook allowing unit tests to simulate restore execution
    var restoreResolverForTesting: (suspend () -> PurchaseResult?)? = null

    // Allows unit tests to provide mock remote offerings
    fun setRemoteOfferingsForTesting(offerings: Offerings?) {
        _remoteOfferings.value = offerings
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("$tag: $msg")
        }
    }

    private fun logW(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        } catch (_: Throwable) {
            System.err.println("$tag: $msg")
        }
    }

    /**
     * Resolves the exact package matching packageId or productId from remote offerings.
     * Strictly avoids fallbacks (such as availablePackages.firstOrNull()) to prevent
     * silently charging the customer for a different package.
     */
    fun findExactPackage(packageId: String): com.revenuecat.purchases.Package? {
        val offerings = _remoteOfferings.value ?: return null
        val activeOffering = offerings.getOffering(_activeOfferingId.value)
        return activeOffering?.availablePackages?.find {
            it.identifier == packageId || it.product.id == packageId
        } ?: offerings.current?.availablePackages?.find {
            it.identifier == packageId || it.product.id == packageId
        } ?: offerings.all.values.flatMap { it.availablePackages }.find {
            it.identifier == packageId || it.product.id == packageId
        }
    }

    /**
     * Resolves the localized formatted price for a given packageId from remote offerings.
     * Source of truth:
     * RevenueCat Offering -> RevenueCat Package -> StoreProduct -> Store-provided formatted price.
     *
     * - If remote offerings have not loaded yet (null): returns "Loading price…"
     * - If the requested package is unavailable: returns "Unavailable"
     * - Otherwise returns the exact store-provided formatted price (e.g. "$39.99", "€39,99", "£34.99").
     */
    fun getFormattedPrice(packageId: String): String {
        val offerings = _remoteOfferings.value ?: return "Loading price…"
        val pkg = findExactPackage(packageId) ?: return "Unavailable"
        return pkg.product.price.formatted
    }

    /**
     * Calculates the localized formatted monthly price breakdown for an annual subscription package.
     * Derived from StoreProduct.price without hardcoding currency or amount.
     */
    fun getFormattedPricePerMonth(packageId: String, locale: Locale = Locale.getDefault()): String? {
        val pkg = findExactPackage(packageId) ?: return null
        val price = pkg.product.price
        return try {
            val currency = Currency.getInstance(price.currencyCode)
            val format = NumberFormat.getCurrencyInstance(locale).apply {
                this.currency = currency
                maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
                minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
            }
            val monthlyAmount = (price.amountMicros / 12.0) / 1_000_000.0
            format.format(monthlyAmount)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Real Google Play & RevenueCat purchase execution.
     * Google Play -> RevenueCat -> Entitlement Active -> Room Cache -> UI.
     * Strictly avoids simulated delays and mock successes.
     * Enforces exact package matching with zero fallback to other packages.
     */
    suspend fun purchasePackage(activity: Activity?, packageId: String): PurchaseResult {
        _isPurchasing.value = true
        _lastError.value = null

        // Locate the exact package matching the requested package/product identifier
        val pkgToBuy = findExactPackage(packageId)

        // Test hook allowing unit tests to simulate purchase execution with the verified exact package
        purchaseResolverForTesting?.let { testResolver ->
            if (pkgToBuy == null) {
                val errorMsg = "Selected subscription is currently unavailable."
                logW(TAG, "Exact package '$packageId' not found in RevenueCat offerings. Unsafe fallback rejected: will NOT substitute another package.")
                _lastError.value = errorMsg
                _isPurchasing.value = false
                return PurchaseResult.Error(errorMsg)
            }
            val result = testResolver.invoke(packageId)
            if (result != null) {
                if (result is PurchaseResult.Error) {
                    _lastError.value = result.message
                }
                _isPurchasing.value = false
                return result
            }
        }

        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            val errorMsg = "Google Play Billing / RevenueCat is not configured. Real purchases require a valid Google Play RevenueCat API key (goog_...)."
            _lastError.value = errorMsg
            _isPurchasing.value = false
            return PurchaseResult.Error(errorMsg)
        }

        if (pkgToBuy == null) {
            val errorMsg = "Selected subscription is currently unavailable."
            logW(TAG, "Exact package '$packageId' not found in RevenueCat offerings. Unsafe fallback rejected: will NOT substitute another package.")
            _lastError.value = errorMsg
            _isPurchasing.value = false
            return PurchaseResult.Error(errorMsg)
        }

        if (activity == null) {
            val errorMsg = "An active Activity context is required to launch Google Play billing flow."
            _lastError.value = errorMsg
            _isPurchasing.value = false
            return PurchaseResult.Error(errorMsg)
        }

        return suspendCoroutine { cont ->
            val params = PurchaseParams.Builder(activity, pkgToBuy).build()
            Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    _isPurchasing.value = false
                    _latestCustomerInfo.value = customerInfo
                    onCustomerInfoUpdated?.invoke(customerInfo)

                    val isEntitled = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    if (isEntitled) {
                        val txId = storeTransaction.orderId ?: storeTransaction.purchaseToken
                        cont.resume(PurchaseResult.Success(ENTITLEMENT_ID, txId))
                    } else {
                        val msg = "Transaction completed with Google Play, but entitlement '$ENTITLEMENT_ID' was not activated in RevenueCat."
                        _lastError.value = msg
                        cont.resume(PurchaseResult.Error(msg))
                    }
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _isPurchasing.value = false
                    val msg = if (userCancelled) "Purchase was cancelled." else error.message
                    _lastError.value = msg
                    cont.resume(PurchaseResult.Error(error.message, isCancelled = userCancelled))
                }
            })
        }
    }

    /**
     * Real Google Play & RevenueCat purchase restoration.
     * Google Play -> RevenueCat -> Entitlement Active -> Room Cache -> UI.
     * Strictly avoids simulated delays and mock successes.
     */
    suspend fun restorePurchases(): PurchaseResult {
        _isPurchasing.value = true
        _lastError.value = null

        restoreResolverForTesting?.let { testResolver ->
            val result = testResolver.invoke()
            if (result != null) {
                if (result is PurchaseResult.Error) {
                    _lastError.value = result.message
                }
                _isPurchasing.value = false
                return result
            }
        }

        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            val errorMsg = "Google Play Billing / RevenueCat is not configured. Cannot restore purchases."
            _lastError.value = errorMsg
            _isPurchasing.value = false
            return PurchaseResult.Error(errorMsg)
        }

        return suspendCoroutine { cont ->
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    _isPurchasing.value = false
                    _latestCustomerInfo.value = customerInfo
                    onCustomerInfoUpdated?.invoke(customerInfo)

                    val isEntitled = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    if (isEntitled) {
                        cont.resume(PurchaseResult.Success(ENTITLEMENT_ID, "restored"))
                    } else {
                        val msg = "No active '$ENTITLEMENT_ID' entitlement found on Google Play for this account."
                        _lastError.value = msg
                        cont.resume(PurchaseResult.Error(msg))
                    }
                }

                override fun onError(error: PurchasesError) {
                    _isPurchasing.value = false
                    _lastError.value = error.message
                    cont.resume(PurchaseResult.Error(error.message))
                }
            })
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    /**
     * Requirement 24: Reset customer identity and clear cached subscription entitlements.
     * Invoked during atomic account deletion.
     */
    suspend fun resetUserIdentity() {
        currentAppUserId = null
        _latestCustomerInfo.value = null
        _lastError.value = null

        logoutResolverForTesting?.let { testResolver ->
            testResolver.invoke()
            return
        }

        if (Purchases.isConfigured && _isLiveConnected.value) {
            try {
                suspendCoroutine<Unit> { cont ->
                    Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            _latestCustomerInfo.value = null
                            cont.resume(Unit)
                        }
                        override fun onError(error: PurchasesError) {
                            Log.w(TAG, "RevenueCat logOut warning: ${error.message}")
                            _latestCustomerInfo.value = null
                            cont.resume(Unit)
                        }
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "RevenueCat identity reset error", e)
            }
        }
    }
}
