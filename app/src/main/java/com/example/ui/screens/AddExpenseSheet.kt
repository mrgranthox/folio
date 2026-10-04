package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.ui.components.CategoryIconBadge
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed

/**
 * Add / Edit Expense Sheet:
 * - Uses Android's native numeric keypad (KeyboardType.Decimal) for direct, responsive entry.
 * - Outlined Dropdowns for Account Rail and Category.
 * - Tab styling matching the navigation bar brand colors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (
        counterparty: String,
        amount: Double,
        currency: String,
        isIncome: Boolean,
        accountId: String,
        categoryId: String,
        notes: String?
    ) -> Unit,
    onScanReceiptClicked: () -> Unit
) {
    var isIncome by remember { mutableStateOf(false) }
    var amountString by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("GHS") }
    var counterparty by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var selectedAccountId by remember {
        mutableStateOf(accounts.firstOrNull()?.id ?: "")
    }

    var selectedCategoryId by remember {
        val initialCat = if (isIncome) {
            categories.firstOrNull { it.name.contains("Income", ignoreCase = true) }
        } else {
            categories.firstOrNull { !it.name.contains("Income", ignoreCase = true) }
        }
        mutableStateOf(initialCat?.id ?: categories.firstOrNull()?.id ?: "")
    }

    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .testTag("add_expense_sheet")
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    text = if (isIncome) "Record Income" else "Record Expense",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = {
                    onDismiss()
                    onScanReceiptClicked()
                }) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = "Scan Receipt", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Income vs Expense Tabs (styled to match navigation button colors)
            TabRow(
                selectedTabIndex = if (isIncome) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (isIncome) 1 else 0]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = !isIncome,
                    onClick = {
                        isIncome = false
                        val cat = categories.firstOrNull { !it.name.contains("Income", ignoreCase = true) }
                        if (cat != null) selectedCategoryId = cat.id
                    },
                    text = {
                        Text(
                            text = "Expense",
                            fontWeight = if (!isIncome) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = isIncome,
                    onClick = {
                        isIncome = true
                        val cat = categories.firstOrNull { it.name.contains("Income", ignoreCase = true) }
                        if (cat != null) selectedCategoryId = cat.id
                    },
                    text = {
                        Text(
                            text = "Income",
                            fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Currency Selector Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("GHS", "USD", "EUR", "GBP").forEach { curr ->
                    val isCurrSelected = selectedCurrency == curr
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedCurrency = curr }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = curr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Native Keypad Amount Field
            OutlinedTextField(
                value = amountString,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1 && filtered.length <= 10) {
                        amountString = filtered
                    }
                },
                label = { Text("Amount in $selectedCurrency") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) CreditGreen else DebitRed
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                trailingIcon = {
                    if (amountString.isNotEmpty()) {
                        IconButton(onClick = { amountString = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick add increment chips (for fast 1-tap addition)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10.0, 50.0, 100.0, 500.0).forEach { addVal ->
                    AssistChip(
                        onClick = {
                            val cur = amountString.toDoubleOrNull() ?: 0.0
                            val total = cur + addVal
                            amountString = if (total % 1.0 == 0.0) {
                                total.toInt().toString()
                            } else {
                                String.format(java.util.Locale.US, "%.2f", total)
                            }
                        },
                        label = { Text("+${addVal.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Outlined Dropdowns for Account Rail & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Account Rail Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { accountDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Payment Rail", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = selectedAccount?.name ?: "Select Rail",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = selectedCategory?.name ?: "Select Category",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIconBadge(
                                            categoryName = cat.name,
                                            iconKey = cat.icon,
                                            colorHex = cat.colorHex,
                                            size = 22.dp,
                                            iconSize = 12.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Counterparty / Merchant Field
            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = { Text(if (isIncome) "Received From (Client, Employer, etc.)" else "Payee / Merchant (Shell, KFC, Groceries, etc.)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Notes input
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Save Button
            val numAmount = amountString.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (numAmount > 0) {
                        onSave(
                            counterparty.ifBlank { if (isIncome) "Inflow Deposit" else (selectedCategory?.name ?: "General Expense") },
                            numAmount,
                            selectedCurrency,
                            isIncome,
                            selectedAccountId.ifBlank { accounts.firstOrNull()?.id ?: "acc-momo" },
                            selectedCategoryId.ifBlank { categories.firstOrNull()?.id ?: "cat-food" },
                            notes.ifBlank { null }
                        )
                        onDismiss()
                    }
                },
                enabled = numAmount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_expense_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save ${if (isIncome) "Income" else "Expense"} ($selectedCurrency ${if (numAmount > 0) String.format("%.2f", numAmount) else "0.00"})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
