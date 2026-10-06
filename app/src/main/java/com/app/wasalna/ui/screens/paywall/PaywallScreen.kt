package com.app.wasalna.ui.screens.paywall

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var selectedPlan by remember { mutableStateOf("yearly") }

    LaunchedEffect(context) {
        (context as? Activity)?.let { viewModel.initBilling(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("First Class") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
            )
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Diamond,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wasalna First Class",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isPremium) "You're riding First Class ✦" else "Never miss your stop — in style",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        BenefitRow("Zero Ads — clean ride, no banners")
        BenefitRow("Unlimited saved places")
        BenefitRow("Silent Alarm + priority alerts")

        Spacer(modifier = Modifier.height(16.dp))

        PlanCard(
            title = "Monthly",
            price = "$0.99 / month",
            selected = selectedPlan == "monthly",
            onSelect = { selectedPlan = "monthly" },
        )
        Spacer(modifier = Modifier.height(8.dp))
        PlanCard(
            title = "Yearly — Best value",
            price = "$5.99 / year",
            selected = selectedPlan == "yearly",
            onSelect = { selectedPlan = "yearly" },
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.simulatePurchase(selectedPlan)
                Toast.makeText(
                    context,
                    "Welcome to First Class! (3-day free trial started)",
                    Toast.LENGTH_LONG,
                ).show()
            },
            enabled = !isPremium,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isPremium) "First Class Active ✦" else "Start 3-Day Free Trial")
        }
        if (!isPremium) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mock checkout — no real charge. Real Play Billing on release.",
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
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
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
                Text(if (selected) "Selected" else "Select")
            }
        }
    }
}
