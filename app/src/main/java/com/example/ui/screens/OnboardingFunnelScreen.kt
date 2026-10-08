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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import java.util.UUID

data class CategoryDraft(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var icon: String = "briefcase",
    var colorHex: String = "#3B82F6"
)

data class AccountDraft(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: String, // "MOMO", "BANK", "CASH", "CARD"
    var initialBalance: String = "0.0",
    var isSelected: Boolean = true
)

@Composable
fun OnboardingFunnelScreen(
    onComplete: (
        selectedCurrency: String,
        selectedCategories: List<CategoryEntity>,
        selectedAccounts: List<AccountEntity>
    ) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var step by remember { mutableIntStateOf(1) }
    var agreedToPrivacy by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf("GHS") }
    var currencySearchQuery by remember { mutableStateOf("") }

    // Initial 3 category drafts
    val categoryDrafts = remember {
        mutableStateListOf(
            CategoryDraft(name = "Food & Dining", icon = "utensils", colorHex = "#F59E0B"),
            CategoryDraft(name = "Transport & Fuel", icon = "car", colorHex = "#10B981"),
            CategoryDraft(name = "Bills & Utilities", icon = "zap", colorHex = "#3B82F6")
        )
    }

    // Initial account drafts
    val accountDrafts = remember {
        mutableStateListOf(
            AccountDraft(name = "Mobile Money", type = "MOMO", initialBalance = "0.0", isSelected = true),
            AccountDraft(name = "Bank Account", type = "BANK", initialBalance = "0.0", isSelected = true),
            AccountDraft(name = "Cash Wallet", type = "CASH", initialBalance = "0.0", isSelected = true),
            AccountDraft(name = "Debit / Credit Card", type = "CARD", initialBalance = "0.0", isSelected = false)
        )
    }

    val totalSteps = 5

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
                .padding(horizontal = 24.dp, vertical = 12.dp)
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
                4 -> InitialCategoryConfigScreen(
                    categoryDrafts = categoryDrafts,
                    onNext = { step = 5 }
                )
                5 -> InitialAccountConfigScreen(
                    accountDrafts = accountDrafts,
                    selectedCurrency = selectedCurrency,
                    onFinish = {
                        val finalCategories = categoryDrafts
                            .filter { it.name.isNotBlank() }
                            .map { draft ->
                                CategoryEntity(
                                    id = draft.id,
                                    name = draft.name.trim(),
                                    icon = draft.icon,
                                    colorHex = draft.colorHex
                                )
                            }
                        val finalAccounts = accountDrafts
                            .filter { it.isSelected && it.name.isNotBlank() }
                            .map { draft ->
                                val bal = draft.initialBalance.toDoubleOrNull() ?: 0.0
                                AccountEntity(
                                    id = draft.id,
                                    name = draft.name.trim(),
                                    accountType = draft.type,
                                    currentBalance = bal,
                                    currency = selectedCurrency
                                )
                            }
                        onComplete(selectedCurrency, finalCategories, finalAccounts)
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
                textAlign = TextAlign.Center
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
                    imageVector = Icons.Default.Shield,
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Folio operates with total data autonomy. Review our privacy principles below:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Local Database Engine",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Transactions, balances, and notes reside solely in encrypted Room SQLite storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.SyncLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Zero Cloud Tracking",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "No third-party analytics, remote tracking servers, or telemetry SDKs are attached.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAgreedChange(!agreed) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = onAgreedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I accept the zero-knowledge privacy policy and local storage design.",
                    style = MaterialTheme.typography.bodySmall,
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
                .testTag("agree_privacy_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "I Agree & Continue",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// 3. Regional & Currency Setup
@Composable
private fun RegionalCurrencyScreen(
    selectedCurrency: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onNext: () -> Unit
) {
    val currencies = listOf(
        Pair("GHS", "Ghanaian Cedi (GH¢)"),
        Pair("USD", "US Dollar ($)"),
        Pair("EUR", "Euro (€)"),
        Pair("GBP", "British Pound (£)"),
        Pair("NGN", "Nigerian Naira (₦)"),
        Pair("KES", "Kenyan Shilling (KSh)"),
        Pair("ZAR", "South African Rand (R)")
    )

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) currencies
        else currencies.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
                    it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Primary Currency",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select the main currency for your ledger calculations and wallet balances.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search currency...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(filtered) { _, (code, label) ->
                    val isSelected = selectedCurrency == code
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCurrency(code) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
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

// 4. Initial Category Setup Screen
@Composable
private fun InitialCategoryConfigScreen(
    categoryDrafts: MutableList<CategoryDraft>,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Categories Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set up 2 or 3 initial categories to group your payments. You can edit names or add custom ones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(categoryDrafts) { index, draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { newName ->
                                    categoryDrafts[index] = draft.copy(name = newName)
                                },
                                label = { Text("Category Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (categoryDrafts.size > 1) {
                                IconButton(
                                    onClick = { categoryDrafts.removeAt(index) },
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Category",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            categoryDrafts.add(
                                CategoryDraft(
                                    name = "Category ${categoryDrafts.size + 1}",
                                    icon = "briefcase",
                                    colorHex = "#3B82F6"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Another Category")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = categoryDrafts.any { it.name.isNotBlank() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("categories_continue_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "Continue to Accounts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// 5. Initial Accounts Setup Screen
@Composable
private fun InitialAccountConfigScreen(
    accountDrafts: MutableList<AccountDraft>,
    selectedCurrency: String,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Initial Accounts Setup",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select active payment methods, edit wallet names, and enter your starting balance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(accountDrafts) { index, draft ->
                    val icon = when (draft.type) {
                        "MOMO" -> Icons.Default.PhoneAndroid
                        "BANK" -> Icons.Default.AccountBalance
                        "CASH" -> Icons.Default.Payments
                        else -> Icons.Default.CreditCard
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (draft.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = if (draft.isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        accountDrafts[index] = draft.copy(isSelected = !draft.isSelected)
                                    }
                            ) {
                                Checkbox(
                                    checked = draft.isSelected,
                                    onCheckedChange = { checked ->
                                        accountDrafts[index] = draft.copy(isSelected = checked)
                                    }
                                )
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (draft.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = draft.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (draft.isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            AnimatedVisibility(visible = draft.isSelected) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    OutlinedTextField(
                                        value = draft.name,
                                        onValueChange = { newName ->
                                            accountDrafts[index] = draft.copy(name = newName)
                                        },
                                        label = { Text("Account Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = draft.initialBalance,
                                        onValueChange = { newBal ->
                                            accountDrafts[index] = draft.copy(initialBalance = newBal)
                                        },
                                        label = { Text("Starting Balance ($selectedCurrency)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFinish,
            enabled = accountDrafts.any { it.isSelected && it.name.isNotBlank() },
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
                text = "Complete Setup & Get Started",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
