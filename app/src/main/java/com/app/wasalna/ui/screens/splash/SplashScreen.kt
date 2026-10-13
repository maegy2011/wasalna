package com.app.wasalna.ui.screens.splash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.app.wasalna.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.BuildConfig
import kotlinx.coroutines.delay

/**
 * Phase 3: branded splash — waits 2000ms minimum, then routes
 * on onboarding state (null = still loading → wait).
 * Oct 2026 (user order): every launch re-checks the ESSENTIAL
 * permissions (foreground location, background location on Q+,
 * notifications on Tiramisu+, battery exemption). Any essential missing
 * → Permissions screen, even when onboarding was completed before.
 * Optional permissions (overlay) stay just-in-time via PermissionGate.
 */
@Composable
fun SplashScreen(
    onNavigateHome: () -> Unit,
    onNavigatePermissions: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val context = LocalContext.current

    // Essential-permission gate, evaluated at navigation time.
    fun essentialsMissing(): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) return true
        return false
    }

    LaunchedEffect(isOnboardingCompleted) {
        // Minimum brand display time; only navigate once DataStore has loaded.
        delay(2000)
        when {
            isOnboardingCompleted == null -> { /* still loading — re-runs on change */ }
            isOnboardingCompleted == true && !essentialsMissing() -> onNavigateHome()
            else -> onNavigatePermissions()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = stringResource(R.string.splash_logo_desc),
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // App name under the logo: localized via app_name (en: Wasalna? / ar: وصلنا؟
            // — the ?/؟ is part of the name) so the splash speaks one language.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator()
        }
        // Phase 10.5.0: copyright + version footer pinned to the bottom —
        // localized app name (with ?) + version via splash_footer.
        Text(
            text = stringResource(
                R.string.splash_footer,
                stringResource(R.string.app_name),
                BuildConfig.VERSION_NAME,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
