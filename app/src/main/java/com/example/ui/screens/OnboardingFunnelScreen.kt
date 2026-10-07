package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed

/**
 * Module A: Onboarding & Trust Funnel (4 Screens) per Visual Specification:
 * 1. Welcome & Value Proposition (Carousel of illustrations, Headline Small, Body Medium, Primary filled button)
 * 2. Zero-Knowledge Privacy Agreement (Large shield icon, telemetry exclusion declaration, Checkbox required to enable button)
 * 3. Regional & Currency Setup (Searchable dropdown list Outlined TextField for fiat currencies)
 * 4. Initial Account Configuration (Toggle cards for Mobile Money, Bank, Cash, Card)
 */
@Composable
fun OnboardingFunnelScreen(
    onComplete: (selectedCurrency: String, selectedRails: List<String>) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var step by remember { mutableIntStateOf(1) }
    var agreedToPrivacy by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf("GHS") }
    var currencySearchQuery by remember { mutableStateOf("") }
    val selectedRails = remember { mutableStateListOf("MOMO", "BANK", "CASH") }

    val totalSteps = 4

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else if (onDismiss != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Text(
                        text = "Step $step of $totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                LinearProgressIndicator(
                    progress = { step.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            when (step) {
                1 -> WelcomeValuePropScreen(onNext = { step = 2 })
                2 -> ZeroKnowledgePrivacyScreen(
                    agreed = agreedToPrivacy,
                    onAgreedChange = { agreedToPrivacy = it },
                    onNext = { step = 3 }
                )
                3 -> RegionalCurrencyScreen(
                    selectedCurrency = selectedCurrency,
                    searchQuery = currencySearchQuery,
                    onSearchChange = { currencySearchQuery = it },
                    onSelectCurrency = { selectedCurrency = it },
                    onNext = { step = 4 }
                )
                4 -> InitialAccountConfigScreen(
                    selectedRails = selectedRails,
                    onToggleRail = { rail ->
                        if (selectedRails.contains(rail)) {
                            if (selectedRails.size > 1) selectedRails.remove(rail)
                        } else {
                            selectedRails.add(rail)
                        }
                    },
                    onFinish = {
                        onComplete(selectedCurrency, selectedRails.toList())
                    }
                )
            }
        }
    }
}

// 1. Welcome & Value Proposition Screen
@Composable
private fun WelcomeValuePropScreen(onNext: () -> Unit) {
    var carouselIndex by remember { mutableIntStateOf(0) }

    val slides = listOf(
        Triple(
            Icons.Default.SyncLock,
            "100% Offline-First Privacy",
            "Your financial data never touches an external server. Everything stays encrypted locally on your device with zero telemetry."
        ),
        Triple(
            Icons.Default.Speed,
            "Instant Automated Parsing",
            "Effortlessly track MoMo, Bank alerts, and Cash receipts in milliseconds without tedious manual bookkeeping."
        ),
        Triple(
            Icons.Default.Shield,
            "Zero Account Linking",
            "No passwords or banking credentials required. Safe, sovereign expense tracking built specifically for local wallets."
        )
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slides[carouselIndex].first,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = slides[carouselIndex].second,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = slides[carouselIndex].third,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Carousel dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == carouselIndex) 20.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == carouselIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                            .clickable { carouselIndex = index }
                    )
                }
            }
        }

        Button(
            onClick = {
                if (carouselIndex < slides.size - 1) {
                    carouselIndex++
                } else {
                    onNext()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_continue_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (carouselIndex < slides.size - 1) "Next" else "Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// 2. Zero-Knowledge Privacy Agreement
@Composable
private fun ZeroKnowledgePrivacyScreen(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Zero-Knowledge Guarantee",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "LEGAL DECLARATION & EXCLUSION OF TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Local-First Sovereignty: All transaction ledgers, accounts, and SMS alerts reside exclusively in your local sandboxed SQLite database on this device.\n\n" +
                                "2. Zero Hidden Telemetry: Folio contains no ad-trackers, background analytics daemons, or behavioral profiling services.\n\n" +
                                "3. Opt-In Intelligence: Automated MoMo/bank parsing executes 100% offline. Cloud AI (Gemini Assistant & OCR) operates solely when you explicitly supply an API key and trigger those actions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAgreedChange(!agreed) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = onAgreedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("privacy_agreement_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I have read and agree to the zero-knowledge privacy terms.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Button(
            onClick = onNext,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("privacy_agree_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            Text(
                text = "I Agree & Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// 3. Regional & Currency Setup Screen
@Composable
private fun RegionalCurrencyScreen(
    selectedCurrency: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onNext: () -> Unit
) {
    val currencies = listOf(
        Pair("GHS", "Ghana Cedi (GH₵)"),
        Pair("USD", "US Dollar ($)"),
        Pair("EUR", "Euro (€)"),
        Pair("GBP", "British Pound (£)"),
        Pair("NGN", "Nigerian Naira (₦)"),
        Pair("KES", "Kenyan Shilling (KSh)"),
        Pair("ZAR", "South African Rand (R)")
    )

    val filtered = currencies.filter {
        it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Regional & Currency Setup",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select your primary accounting fiat currency.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search currency...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { (code, label) ->
                    val isSelected = selectedCurrency == code
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCurrency(code) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("currency_continue_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "Continue with $selectedCurrency",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// 4. Initial Account Configuration Screen
@Composable
private fun InitialAccountConfigScreen(
    selectedRails: List<String>,
    onToggleRail: (String) -> Unit,
    onFinish: () -> Unit
) {
    val rails = listOf(
        Triple("MOMO", "Mobile Money", Icons.Default.PhoneAndroid),
        Triple("BANK", "Bank Account", Icons.Default.AccountBalance),
        Triple("CASH", "Cash Wallet", Icons.Default.Payments),
        Triple("CARD", "Debit / Credit Card", Icons.Default.CreditCard)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Initial Account Setup",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Toggle the payment rails you actively use. Selected cards will be provisioned in your ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rails.forEach { (key, title, icon) ->
                    val isSelected = selectedRails.contains(key)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleRail(key) }
                            .testTag("toggle_rail_$key"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("complete_onboarding_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
