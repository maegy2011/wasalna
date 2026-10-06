package com.app.wasalna.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Phase 8: AdMob banner (test unit by default).
 * Lifecycle-aware: destroys the AdView on dispose to avoid leaks.
 */
@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = BANNER_TEST_ID,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { adView ->
            adView.destroy()
        },
    )
}

const val BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111"
const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"
