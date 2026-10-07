package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.DatePeriod
import com.example.ui.viewmodel.ExpenseUiState
import com.example.ui.viewmodel.SortOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: ExpenseUiState,
    onSearchChange: (String) -> Unit,
    onPeriodChange: (DatePeriod) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onAccountFilterChange: (String?) -> Unit,
    onDirectionFilterChange: (TransactionDirection?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val headerDateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US)

    val grouped = remember(state.filteredTransactions) {
        state.filteredTransactions.groupBy { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("transactions_screen")
        ) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = "Search transactions...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Filters row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort dropdown
                item {
                    Box {
                        FilterChip(
                            selected = state.selectedSort != SortOrder.DATE_DESC,
                            onClick = { sortMenuExpanded = true },
                            label = { Text(state.selectedSort.label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = {
                                        onSortChange(sort)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Account filter
                item {
                    var accountMenuExpanded by remember { mutableStateOf(false) }
                    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountFilter }
                    Box {
                        FilterChip(
                            selected = state.selectedAccountFilter != null,
                            onClick = { accountMenuExpanded = true },
                            label = { Text(selectedAccount?.name ?: "All Accounts", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Accounts") },
                                onClick = {
                                    onAccountFilterChange(null)
                                    accountMenuExpanded = false
                                }
                            )
                            state.accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        onAccountFilterChange(acc.id)
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Category filter
                item {
                    var categoryMenuExpanded by remember { mutableStateOf(false) }
                    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryFilter }
                    Box {
                        FilterChip(
                            selected = state.selectedCategoryFilter != null,
                            onClick = { categoryMenuExpanded = true },
                            label = { Text(selectedCategory?.name ?: "All Categories", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = {
                                    onCategoryFilterChange(null)
                                    categoryMenuExpanded = false
                                }
                            )
                            state.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        onCategoryFilterChange(cat.id)
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Direction filters
                item {
                    FilterChip(
                        selected = state.selectedDirectionFilter == null,
                        onClick = { onDirectionFilterChange(null) },
                        label = { Text("All", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedDirectionFilter == TransactionDirection.DEBIT,
                        onClick = {
                            onDirectionFilterChange(if (state.selectedDirectionFilter == TransactionDirection.DEBIT) null else TransactionDirection.DEBIT)
                        },
                        label = { Text("Expenses", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedDirectionFilter == TransactionDirection.CREDIT,
                        onClick = {
                            onDirectionFilterChange(if (state.selectedDirectionFilter == TransactionDirection.CREDIT) null else TransactionDirection.CREDIT)
                        },
                        label = { Text("Inflows", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Period filters
                DatePeriod.entries.forEach { period ->
                    item {
                        FilterChip(
                            selected = state.selectedPeriod == period,
                            onClick = { onPeriodChange(period) },
                            label = { Text(period.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transactions list grouped by day
            if (state.filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your search or active filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isAmountSorted = state.selectedSort == SortOrder.AMOUNT_DESC || state.selectedSort == SortOrder.AMOUNT_ASC

                    if (isAmountSorted) {
                        items(state.filteredTransactions, key = { it.id }) { tx ->
                            TransactionCardItem(
                                tx = tx,
                                timeFormat = timeFormat,
                                onClick = { onTransactionClick(tx) }
                            )
                        }
                    } else {
                        grouped.forEach { (dateMillis, txList) ->
                            item(key = "header-$dateMillis") {
                                val totalDayNet = txList.sumOf { it.amount }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = headerDateFormat.format(Date(dateMillis)),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Day Net: ${if (totalDayNet >= 0) "+" else "-"} ${CurrencyUtils.format(abs(totalDayNet), state.preferredCurrency)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (totalDayNet >= 0) CreditGreen else DebitRed
                                    )
                                }
                            }

                            items(txList, key = { it.id }) { tx ->
                                TransactionCardItem(
                                    tx = tx,
                                    timeFormat = timeFormat,
                                    onClick = { onTransactionClick(tx) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCardItem(
    tx: TransactionEntity,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val isIncome = tx.isIncome
    val amountColor = if (isIncome) CreditGreen else DebitRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                categoryName = tx.categoryName ?: "General",
                colorHex = tx.categoryColor,
                size = 38.dp,
                iconSize = 18.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.counterparty,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${tx.accountRail ?: "Payment"} · ${timeFormat.format(Date(tx.timestamp))}${if (!tx.externalRef.isNullOrBlank()) " · Ref: ${tx.externalRef}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"} ${CurrencyUtils.format(abs(tx.amount), tx.currency)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                if (tx.sourceMethod == "ocr") {
                    Text(
                        text = "OCR Scanned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!tx.notes.isNullOrBlank()) {
                    Text(
                        text = tx.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
