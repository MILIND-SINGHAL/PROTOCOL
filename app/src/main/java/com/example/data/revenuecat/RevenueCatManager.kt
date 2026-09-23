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
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class RevenueCatPackage(
    val identifier: String,
    val title: String,
    val priceString: String,
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
                priceString = "$39.99",
                subtitle = "Includes 4-day free trial",
                period = "per year ($3.33/mo)",
                hasTrial = true,
                trialDays = 4
            ),
            RevenueCatPackage(
                identifier = "\$rc_weekly",
                title = "Weekly Protocol",
                priceString = "$6.99",
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
                priceString = "$29.99",
                subtitle = "Special founder rate with 4-day trial",
                period = "first year ($2.49/mo)",
                hasTrial = true,
                trialDays = 4
            ),
            RevenueCatPackage(
                identifier = "\$rc_weekly",
                title = "Weekly Protocol",
                priceString = "$6.99",
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
        Purchases.logLevel = LogLevel.DEBUG
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

    /**
     * Real Google Play & RevenueCat purchase execution.
     * Google Play -> RevenueCat -> Entitlement Active -> Room Cache -> UI.
     * Strictly avoids simulated delays and mock successes.
     */
    suspend fun purchasePackage(activity: Activity?, packageId: String): PurchaseResult {
        _isPurchasing.value = true
        _lastError.value = null

        if (!Purchases.isConfigured || !_isLiveConnected.value) {
            val errorMsg = "Google Play Billing / RevenueCat is not configured. Real purchases require a valid Google Play RevenueCat API key (goog_...)."
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

        val offerings = _remoteOfferings.value
        val pkgToBuy = offerings?.current?.availablePackages?.find {
            it.identifier == packageId || it.product.id == packageId
        } ?: offerings?.all?.values?.flatMap { it.availablePackages }?.find {
            it.identifier == packageId || it.product.id == packageId
        } ?: offerings?.current?.availablePackages?.firstOrNull()

        if (pkgToBuy == null) {
            val errorMsg = "Offering or package '$packageId' not found in RevenueCat dashboard for Google Play."
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
        _latestCustomerInfo.value = null
        _lastError.value = null
        if (Purchases.isConfigured && _isLiveConnected.value) {
            try {
                suspendCoroutine<Unit> { cont ->
                    Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            _latestCustomerInfo.value = customerInfo
                            cont.resume(Unit)
                        }
                        override fun onError(error: PurchasesError) {
                            Log.w(TAG, "RevenueCat logOut warning: ${error.message}")
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
