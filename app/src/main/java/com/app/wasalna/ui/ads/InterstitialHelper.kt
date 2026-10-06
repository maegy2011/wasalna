package com.app.wasalna.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Phase 8: test interstitial helper.
 * Loads on demand then shows; always calls [onDone] (dismissed or failed)
 * so navigation never gets stuck when ads fail offline.
 */
object InterstitialHelper {

    private const val TAG = "InterstitialHelper"

    fun showInterstitial(
        context: Context,
        adUnitId: String = INTERSTITIAL_TEST_ID,
        onDone: () -> Unit,
    ) {
        val activity = context as? Activity
        if (activity == null) {
            Log.w(TAG, "showInterstitial: not an Activity, skipping")
            onDone()
            return
        }
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "interstitial loaded, showing")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.i(TAG, "interstitial dismissed")
                            onDone()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "interstitial show failed: ${error.message}")
                            onDone()
                        }
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "interstitial load failed: ${error.message}")
                    onDone()
                }
            },
        )
    }
}
