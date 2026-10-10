package com.app.wasalna.ui.screens.paywall

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.app.wasalna.utils.trialRemainingText
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Phase 8: First Class paywall — beautiful mock with 3-day free trial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onMenuClick: () -> Unit = {},
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isPremium by viewModel.isPremium.collectAsState()
    val trialEnd by viewModel.trialEnd.collectAsState()
    var selectedPlan by remember { mutableStateOf("yearly") }
    var trialNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isPremium) {
        // Phase 10.4.1: keep the trial countdown fresh + enforce expiry.
        viewModel.enforceExpiry()
        while (isPremium) {
            delay(60_000)
            trialNow = System.currentTimeMillis()
        }
    }

    LaunchedEffect(context) {
        (context as? Activity)?.let { viewModel.initBilling(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.common_open_menu))
                    }
                },
            )
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Diamond,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.paywall_header),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isPremium) stringResource(R.string.paywall_active_sub) else stringResource(R.string.paywall_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Phase 10.4.1: live trial countdown under the plan header.
        if (isPremium) {
            trialRemainingText(trialEnd, trialNow)?.let { remaining ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.paywall_trial_remaining, remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        BenefitRow(stringResource(R.string.paywall_benefit_ads))
        BenefitRow(stringResource(R.string.paywall_benefit_places))
        BenefitRow(stringResource(R.string.paywall_benefit_silent))

        Spacer(modifier = Modifier.height(16.dp))

        PlanCard(
            title = stringResource(R.string.paywall_monthly),
            price = stringResource(R.string.paywall_monthly_price),
            selected = selectedPlan == "monthly",
            onSelect = { selectedPlan = "monthly" },
        )
        Spacer(modifier = Modifier.height(8.dp))
        PlanCard(
            title = stringResource(R.string.paywall_yearly),
            price = stringResource(R.string.paywall_yearly_price),
            selected = selectedPlan == "yearly",
            onSelect = { selectedPlan = "yearly" },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.simulatePurchase(selectedPlan)
                Toast.makeText(
                    context,
                    context.getString(R.string.paywall_welcome_toast),
                    Toast.LENGTH_LONG,
                ).show()
            },
            enabled = !isPremium,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isPremium) stringResource(R.string.paywall_cta_active) else stringResource(R.string.paywall_cta_start))
        }
        if (!isPremium) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_mock_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(onClick = onSelect) {
                Text(if (selected) stringResource(R.string.common_selected) else stringResource(R.string.common_select))
            }
        }
    }
}
