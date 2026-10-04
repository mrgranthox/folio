package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.NumericKeypad
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.PrimaryGreen

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

    val displayAmount = if (amountString.isBlank()) "0.00" else amountString

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .testTag("add_expense_sheet")
        ) {
            // Sheet Header: Close button, Title, OCR Scan button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    text = if (isIncome) "Add Income" else "Add Expense",
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

            Spacer(modifier = Modifier.height(8.dp))

            // Income vs Expense Tab
            TabRow(
                selectedTabIndex = if (isIncome) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Tab(
                    selected = !isIncome,
                    onClick = {
                        isIncome = false
                        val cat = categories.firstOrNull { !it.name.contains("Income", ignoreCase = true) }
                        if (cat != null) selectedCategoryId = cat.id
                    },
                    text = { Text("Expense", fontWeight = if (!isIncome) FontWeight.Bold else FontWeight.Normal, color = if (!isIncome) DebitRed else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Tab(
                    selected = isIncome,
                    onClick = {
                        isIncome = true
                        val cat = categories.firstOrNull { it.name.contains("Income", ignoreCase = true) }
                        if (cat != null) selectedCategoryId = cat.id
                    },
                    text = { Text("Income", fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Normal, color = if (isIncome) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big Amount Display Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Currency selector pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("GHS", "USD", "EUR", "GBP").forEach { curr ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedCurrency == curr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedCurrency = curr }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = curr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCurrency == curr) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$selectedCurrency $displayAmount",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = if (isIncome) CreditGreen else DebitRed,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Merchant / Counterparty input
            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = { Text(if (isIncome) "Received From (e.g. Acme Client Retainer)" else "Paid To / Merchant (e.g. KFC, TotalEnergies)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Account Selector
            Text(
                text = "Select Account / Rail",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts) { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text(acc.name, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selector
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        leadingIcon = {
                            CategoryIconBadge(
                                categoryName = cat.name,
                                iconKey = cat.icon,
                                colorHex = cat.colorHex,
                                size = 20.dp,
                                iconSize = 12.dp
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notes input
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes & Details (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Numeric Keypad
            NumericKeypad(
                onDigitPress = { digit ->
                    if (digit == "." && amountString.contains(".")) return@NumericKeypad
                    if (amountString.length < 9) {
                        amountString += digit
                    }
                },
                onBackspace = {
                    if (amountString.isNotEmpty()) {
                        amountString = amountString.dropLast(1)
                    }
                },
                onQuickAdd = { addVal ->
                    val current = amountString.toDoubleOrNull() ?: 0.0
                    amountString = String.format(java.util.Locale.US, "%.2f", current + addVal)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            val numAmount = amountString.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (numAmount > 0) {
                        onSave(
                            counterparty.ifBlank { if (isIncome) "Payment Inflow" else "General Expense" },
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
                    .height(54.dp)
                    .testTag("submit_expense_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isIncome) CreditGreen else PrimaryGreen)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save ${if (isIncome) "Income" else "Expense"}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
