package com.app.wasalna.ui.screens.paywall

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.app.wasalna.ui.components.WasalnaTopBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.utils.trialRemainingText
import kotlinx.coroutines.delay

/**
 * First Class paywall — simplified (user order Oct 2026).
 *
 * One hero + one compact perks card + 3 tappable plan rows (radio-style,
 * no inner buttons) + a single CTA. Same ViewModel behavior
 * (trial countdown, expiry enforcement, billing init, mock purchase).
 */
data class PaywallPlan(
    val id: String,
    val productId: String,
    val titleRes: Int,
    val priceRes: Int,
    val descRes: Int,
    val badgeRes: Int? = null,
    // User order Oct 2026: lifetime has no trial — one payment, forever.
    val showTrialBadge: Boolean = true,
)

private val PAYWALL_PLANS = listOf(
    PaywallPlan(
        id = "monthly",
        productId = "wasalna_first_class_monthly",
        titleRes = R.string.paywall_monthly,
        priceRes = R.string.paywall_monthly_price,
        descRes = R.string.paywall_plan_monthly_desc,
    ),
    PaywallPlan(
        id = "yearly",
        productId = "wasalna_first_class_yearly",
        titleRes = R.string.paywall_yearly,
        priceRes = R.string.paywall_yearly_price,
        descRes = R.string.paywall_plan_yearly_desc,
        badgeRes = R.string.paywall_yearly_badge,
    ),
    PaywallPlan(
        id = "lifetime",
        productId = "wasalna_first_class_lifetime",
        titleRes = R.string.paywall_lifetime,
        priceRes = R.string.paywall_lifetime_price,
        descRes = R.string.paywall_plan_lifetime_desc,
        showTrialBadge = false,
    ),
)

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
            WasalnaTopBar(
                title = stringResource(R.string.paywall_title),
                showMenu = true,
                onMenuClick = onMenuClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
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
                text = stringResource(R.string.paywall_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.paywall_hero_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPremium) {
                    stringResource(R.string.paywall_active_sub)
                } else {
                    stringResource(R.string.paywall_sub)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
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

            // Everything First Class unlocks — one compact card.
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.paywall_value_perks_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PerkLine(stringResource(R.string.paywall_benefit_ads))
                    PerkLine(stringResource(R.string.paywall_benefit_places))
                    PerkLine(stringResource(R.string.paywall_benefit_silent))
                    PerkLine(stringResource(R.string.paywall_value_perks_desc))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PAYWALL_PLANS.forEach { plan ->
                PlanRow(
                    title = stringResource(plan.titleRes),
                    price = stringResource(plan.priceRes),
                    desc = stringResource(plan.descRes),
                    badge = plan.badgeRes?.let { stringResource(it) },
                    trialBadge = if (plan.showTrialBadge && !isPremium) {
                        stringResource(R.string.paywall_trial_badge)
                    } else {
                        null
                    },
                    selected = selectedPlan == plan.id,
                    onSelect = { selectedPlan = plan.id },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    text = if (isPremium) {
                        stringResource(R.string.paywall_cta_active)
                    } else if (selectedPlan == "lifetime") {
                        stringResource(R.string.paywall_cta_lifetime)
                    } else {
                        stringResource(R.string.paywall_cta_start)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!isPremium) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.paywall_experimental_billing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PerkLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlanRow(
    title: String,
    price: String,
    desc: String,
    badge: String?,
    trialBadge: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) {
                    Icons.Filled.RadioButtonChecked
                } else {
                    Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (badge != null) "$title • $badge" else title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (trialBadge != null) "$desc • $trialBadge" else desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
