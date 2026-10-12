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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.app.wasalna.ui.components.WasalnaTopBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.utils.trialRemainingText
import kotlinx.coroutines.delay

/**
 * Phase 10.13: First Class paywall — marketing upgrade.
 *
 * - Hero message (Arabic-first) + 3 real value props from the tree:
 *   watch-only alert, headphone-only alert, First Class perks
 *   (unlimited places · zero ads · silent alarm · custom ringtone ·
 *   offline maps). No invented perks.
 * - Plan MODELS ([PaywallPlan]) mirror the BillingClient SUBS catalogue
 *   (wasalna_first_class_monthly / _yearly) as data + UI only, clearly
 *   marked experimental — no real charge code (purchase stays the
 *   3-day mock trial via [PaywallViewModel.simulatePurchase]).
 * - Elderly-friendly: headline-size hero, 56dp primary CTA (single),
 *   48dp+ plan rows, scrollable column for small phones.
 * - Premium gates (isPremium checks) untouched in behavior.
 */
data class PaywallPlan(
    val id: String,
    val productId: String,
    val titleRes: Int,
    val priceRes: Int,
    val descRes: Int,
    val badgeRes: Int? = null,
    val experimental: Boolean = true,
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
    val accountName by viewModel.accountDisplayName.collectAsState()
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
        // Hero: big type for elderly readers, centered, Arabic-first copy.
        Text(
            text = stringResource(R.string.paywall_hero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.paywall_hero_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isPremium) stringResource(R.string.paywall_active_sub) else stringResource(R.string.paywall_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
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
        // Phase 10.13-exp: who is browsing (local account or guest).
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (accountName.isBlank()) {
                    stringResource(R.string.paywall_account_guest)
                } else {
                    stringResource(R.string.paywall_account_signed_as, accountName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Real value props from the tree — watch-only, headphone-only,
        // then the First Class bundle. No invented perks.
        ValuePropCard(
            icon = Icons.Filled.Watch,
            title = stringResource(R.string.paywall_value_watch_title),
            desc = stringResource(R.string.paywall_value_watch_desc),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ValuePropCard(
            icon = Icons.Filled.Headset,
            title = stringResource(R.string.paywall_value_headphone_title),
            desc = stringResource(R.string.paywall_value_headphone_desc),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ValuePropCard(
            icon = Icons.Filled.Diamond,
            title = stringResource(R.string.paywall_value_perks_title),
            desc = stringResource(R.string.paywall_value_perks_desc),
        )

        Spacer(modifier = Modifier.height(8.dp))
        BenefitRow(stringResource(R.string.paywall_benefit_ads))
        BenefitRow(stringResource(R.string.paywall_benefit_places))
        BenefitRow(stringResource(R.string.paywall_benefit_silent))

        Spacer(modifier = Modifier.height(16.dp))

        PAYWALL_PLANS.forEach { plan ->
            PlanCard(
                title = stringResource(plan.titleRes),
                price = stringResource(plan.priceRes),
                desc = stringResource(plan.descRes),
                badge = plan.badgeRes?.let { stringResource(it) },
                trialBadge = stringResource(R.string.paywall_trial_badge),
                selected = selectedPlan == plan.id,
                onSelect = { selectedPlan = plan.id },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Single primary CTA — 56dp tall, headline-size label.
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
                } else {
                    stringResource(R.string.paywall_cta_start)
                },
                style = MaterialTheme.typography.titleLarge,
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
private fun ValuePropCard(
    icon: ImageVector,
    title: String,
    desc: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge,
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
    desc: String,
    badge: String?,
    trialBadge: String,
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
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(badge) },
                        )
                    }
                }
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = trialBadge,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onSelect) {
                Text(if (selected) stringResource(R.string.common_selected) else stringResource(R.string.common_select))
            }
        }
    }
}
