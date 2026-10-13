package com.app.wasalna.ui.screens.paywall

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.app.wasalna.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 8: Paywall billing (mock-first).
 * Real BillingClient init + product query scaffold; purchase is simulated
 * via [simulatePurchase] which flips premium in DataStore.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    // Phase 10.4.1: trial expiry for the paywall countdown header.
    val trialEnd: StateFlow<Long> =
        settingsRepository.trialEndTimeMillis.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L,
        )

    // Phase 10.13-exp: Google account row on the paywall (local-only).
    val accountDisplayName: StateFlow<String> =
        settingsRepository.accountDisplayName.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    val accountEmail: StateFlow<String> =
        settingsRepository.accountEmail.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun enforceExpiry() {
        viewModelScope.launch {
            runCatching { settingsRepository.enforceTrialExpiry() }
                .onFailure { Log.e(TAG, "enforceTrialExpiry failed: ${it.message}") }
        }
    }

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady

    private var billingClient: BillingClient? = null

    fun initBilling(activity: Activity) {
        if (billingClient != null) return
        try {
            val client = BillingClient.newBuilder(activity)
                .setListener { _, _ -> /* purchases updated: mock mode */ }
                .enablePendingPurchases()
                .build()
            billingClient = client
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        val ok = result.responseCode ==
                            BillingClient.BillingResponseCode.OK
                        _billingReady.value = ok
                        Log.i(TAG, "billing setup: ${result.responseCode} ok=$ok")
                        if (ok) queryProducts()
                    }

                    override fun onBillingServiceDisconnected() {
                        _billingReady.value = false
                        Log.w(TAG, "billing disconnected")
                    }
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "initBilling failed: ${e.message}")
        }
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("wasalna_first_class_monthly")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("wasalna_first_class_yearly")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("wasalna_first_class_lifetime")
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            Log.i(TAG, "query products: ${result.responseCode} count=${details.size}")
        }
    }

    /**
     * Mock purchase — user order Oct 2026: 7-day trial on monthly/yearly
     * (premium ON + expiry), lifetime = premium with NO expiry.
     * Replace with real launchBillingFlow.
     */
    fun simulatePurchase(plan: String) {
        viewModelScope.launch {
            if (plan == "lifetime") {
                runCatching { settingsRepository.setPremium(true) }
                    .onSuccess { Log.i(TAG, "lifetime granted via $plan (mock)") }
                    .onFailure { Log.e(TAG, "lifetime grant failed: ${it.message}") }
            } else {
                runCatching { settingsRepository.startTrial(TRIAL_DAYS) }
                    .onSuccess { Log.i(TAG, "trial started via $plan (mock, $TRIAL_DAYS days)") }
                    .onFailure { Log.e(TAG, "startTrial failed: ${it.message}") }
            }
        }
    }

    override fun onCleared() {
        runCatching { billingClient?.endConnection() }
        billingClient = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "PaywallViewModel"
        const val TRIAL_DAYS = 7
    }
}
