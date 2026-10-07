package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.data.model.AccountType
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BudgetVarianceBadge
import com.example.ui.components.CashFlowBarChart
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.CurrencyUtils
import com.example.ui.components.SpendingDonutChart
import com.example.ui.components.parseColorHex
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.DatePeriod
import com.example.ui.viewmodel.ExpenseUiState
import java.util.Calendar
import kotlin.math.abs

enum class AnalyticsSubTab(val title: String) {
    BREAKDOWN("Breakdown"),
    TRENDS("Trend"),
    BUDGETS("Budgets & Targets")
}

@Composable
fun AnalyticsScreen(
    state: ExpenseUiState,
    onPeriodChange: (DatePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    // Precomputed analytical data
    val filteredOutflows = remember(state.filteredTransactions) {
        state.filteredTransactions.filter { !it.isIncome }
    }
    val totalPeriodOutflow = remember(filteredOutflows) {
        filteredOutflows.sumOf { abs(it.amount) }
    }
    val outflowCount = remember(filteredOutflows) {
        filteredOutflows.size
    }
    val avgTicketSize = if (outflowCount > 0) totalPeriodOutflow / outflowCount else 0.0

    // Top Merchants
    val merchantLeaderboard = remember(filteredOutflows) {
        filteredOutflows
            .groupBy { it.counterparty.trim().ifBlank { "Unknown Payee" } }
            .map { (merchant, txList) ->
                val total = txList.sumOf { abs(it.amount) }
                val count = txList.size
                val avg = if (count > 0) total / count else 0.0
                Triple(merchant, total, Pair(count, avg))
            }
            .sortedByDescending { it.second }
            .take(5)
    }

    // Payment Rail Share
    val railSpendList = remember(filteredOutflows, state.accounts, totalPeriodOutflow) {
        val denom = totalPeriodOutflow.coerceAtLeast(0.01)
        filteredOutflows
            .groupBy { tx ->
                val acc = state.accounts.firstOrNull { it.id == tx.accountId }
                acc?.name ?: tx.accountRail ?: "Mobile Money"
            }
            .map { (railName, txs) ->
                val total = txs.sumOf { abs(it.amount) }
                val pct = (total / denom).toFloat().coerceIn(0f, 1f)
                Triple(railName, total, pct)
            }
            .sortedByDescending { it.second }
    }

    // Weekday vs Weekend Rhythm
    val (weekdaySpend, weekendSpend) = remember(filteredOutflows) {
        val calendar = Calendar.getInstance()
        var weekday = 0.0
        var weekend = 0.0
        filteredOutflows.forEach { tx ->
            calendar.timeInMillis = tx.timestamp
            val dow = calendar.get(Calendar.DAY_OF_WEEK)
            val mag = abs(tx.amount)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                weekend += mag
            } else {
                weekday += mag
            }
        }
        Pair(weekday, weekend)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("analytics_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Time Horizon Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DatePeriod.entries.forEach { period ->
                        item {
                            FilterChip(
                                selected = state.selectedPeriod == period,
                                onClick = { onPeriodChange(period) },
                                label = { Text(period.label, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // 2. Analytical Sub-Tab Switcher
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnalyticsSubTab.entries.forEachIndexed { index, subTab ->
                            val isSelected = selectedSubTab == index
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSubTab = index }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = subTab.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Tab Contents
            when (selectedSubTab) {
                0 -> {
                    // ==========================================
                    // TAB 1: BREAKDOWN (Categories & Merchants)
                    // ==========================================

                    // Donut Chart Hero
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PieChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Category Share",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${state.categorySpendList.size} categories active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                SpendingDonutChart(items = state.categorySpendList, currency = state.preferredCurrency)
                            }
                        }
                    }

                    // Section Header: Category Distribution
                    item {
                        Text(
                            text = "CATEGORY DISTRIBUTION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Category List
                    item {
                        if (state.categorySpendList.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No category expense recorded for this time range.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column {
                                    state.categorySpendList.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CategoryIconBadge(
                                                categoryName = item.category.name,
                                                iconKey = item.category.icon,
                                                colorHex = item.category.colorHex,
                                                size = 36.dp,
                                                iconSize = 16.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.category.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val catTxCount = filteredOutflows.count { it.categoryId == item.category.id }
                                                Text(
                                                    text = "$catTxCount payment${if (catTxCount != 1) "s" else ""}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = CurrencyUtils.format(item.totalSpend, state.preferredCurrency),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (item.budgetLimit != null && item.budgetLimit > 0) {
                                                    BudgetVarianceBadge(
                                                        isOverBudget = item.isOverBudget,
                                                        budgetProgress = item.budgetProgress
                                                    )
                                                }
                                            }
                                        }
                                        if (index < state.categorySpendList.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section Header: Top Merchants Leaderboard
                    item {
                        Text(
                            text = "TOP PAYEES & MERCHANTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Top Merchants
                    item {
                        if (merchantLeaderboard.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No payee transaction history for this period.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column {
                                    merchantLeaderboard.forEachIndexed { rankIndex, (merchant, total, meta) ->
                                        val (txCount, avgTx) = meta
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Rank Badge
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (rankIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "#${rankIndex + 1}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (rankIndex == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = merchant,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$txCount payment${if (txCount > 1) "s" else ""} · Avg ${CurrencyUtils.format(avgTx, state.preferredCurrency)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = CurrencyUtils.format(total, state.preferredCurrency),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = DebitRed
                                            )
                                        }
                                        if (rankIndex < merchantLeaderboard.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section Header: Payment Rails
                    item {
                        Text(
                            text = "PAYMENT CHANNELS & RAILS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Payment Rails
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column {
                                railSpendList.forEachIndexed { index, (railName, total, pct) ->
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CreditCard,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = railName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = CurrencyUtils.format(total, state.preferredCurrency),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { pct.coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    }
                                    if (index < railSpendList.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // ==========================================
                    // TAB 2: TRENDS & RHYTHM
                    // ==========================================

                    // Header: Velocity & Run-rate
                    item {
                        Text(
                            text = "FINANCIAL VELOCITY & RUN-RATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 1. Month-over-Month & Daily Run-rate Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Card 1: Month-over-Month Growth (unambiguous, never "MoM" or "MON")
                            val momDiff = state.thisMonthOutflow - state.lastMonthOutflow
                            val isHigher = momDiff > 0
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isHigher) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = if (isHigher) DebitRed else CreditGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Month-over-Month",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${if (isHigher) "+" else if (momDiff < 0) "-" else ""}${CurrencyUtils.format(abs(momDiff), state.preferredCurrency)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isHigher) DebitRed else CreditGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val momPctText = if (state.lastMonthOutflow > 0) {
                                        val pct = abs(state.outflowVariancePct)
                                        if (isHigher) "+${String.format("%.1f", pct)}% vs last month"
                                        else "-${String.format("%.1f", pct)}% vs last month"
                                    } else {
                                        "vs previous month"
                                    }
                                    Text(
                                        text = momPctText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Card 2: Daily Burn & End-of-Month Projection
                            val cal = Calendar.getInstance()
                            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val projectedMonthSpend = state.avgDailyExpense * daysInMonth
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Daily Run-Rate",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = CurrencyUtils.format(state.avgDailyExpense, state.preferredCurrency),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Proj: ${CurrencyUtils.format(projectedMonthSpend, state.preferredCurrency)}/mo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Card 3: Dedicated Mobile Money (MoMo) Volume & Rails
                    item {
                        val momoTxs = filteredOutflows.filter { tx ->
                            tx.accountRail?.contains("momo", ignoreCase = true) == true ||
                            tx.accountRail?.contains("mtn", ignoreCase = true) == true ||
                            tx.accountRail?.contains("telecel", ignoreCase = true) == true ||
                            state.accounts.firstOrNull { it.id == tx.accountId }?.type == AccountType.MOMO
                        }
                        val momoSpend = momoTxs.sumOf { abs(it.amount) }
                        val momoShare = if (totalPeriodOutflow > 0) (momoSpend / totalPeriodOutflow).toFloat() else 0f
                        val otherSpend = (totalPeriodOutflow - momoSpend).coerceAtLeast(0.0)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CreditCard,
                                            contentDescription = null,
                                            tint = WarningOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Mobile Money (MoMo) Volume",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${String.format("%.0f", momoShare * 100)}% of total",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = CurrencyUtils.format(momoSpend, state.preferredCurrency),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${momoTxs.size} Mobile Money transaction${if (momoTxs.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Bank & Cash",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = CurrencyUtils.format(otherSpend, state.preferredCurrency),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(momoShare.coerceAtLeast(0.01f))
                                            .fillMaxHeight()
                                            .background(WarningOrange)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight((1f - momoShare).coerceAtLeast(0.01f))
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }

                    // Cash Flow Trajectory Chart
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ShowChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Cash Flow Trajectory",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${state.dailyCashFlow.size} active days",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Daily inflow vs outflow distribution",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                CashFlowBarChart(bars = state.dailyCashFlow, currency = state.preferredCurrency)
                            }
                        }
                    }

                    // Spending Rhythm: Weekday vs Weekend
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Spending Rhythm (Weekday vs. Weekend)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                val totalRhythm = (weekdaySpend + weekendSpend).coerceAtLeast(0.01)
                                val weekdayPct = (weekdaySpend / totalRhythm).toFloat().coerceIn(0f, 1f)
                                val weekendPct = (weekendSpend / totalRhythm).toFloat().coerceIn(0f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Weekdays (Mon - Fri)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            CurrencyUtils.format(weekdaySpend, state.preferredCurrency),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Weekends (Sat - Sun)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            CurrencyUtils.format(weekendSpend, state.preferredCurrency),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = WarningOrange
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(weekdayPct.coerceAtLeast(0.02f))
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(weekendPct.coerceAtLeast(0.02f))
                                            .fillMaxSize()
                                            .background(WarningOrange)
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // ==========================================
                    // TAB 3: BUDGETS & TARGETS
                    // ==========================================

                    // Executive Budget Health Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Total Monthly Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = CurrencyUtils.format(state.totalMonthlyBudget, state.preferredCurrency),
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    val isBudgetOver = state.monthlyBudgetUsedPct > 1.0
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isBudgetOver) DebitRed.copy(alpha = 0.15f) else CreditGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (isBudgetOver) "OVER BUDGET" else "ON TARGET",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isBudgetOver) DebitRed else CreditGreen
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val budgetProgress = state.monthlyBudgetUsedPct.toFloat().coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { budgetProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (state.monthlyBudgetUsedPct > 1.0) DebitRed else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Spent: ${CurrencyUtils.format(state.totalMonthlySpent, state.preferredCurrency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val remainingBudget = state.totalMonthlyBudget - state.totalMonthlySpent
                                    Text(
                                        text = if (remainingBudget >= 0) "${CurrencyUtils.format(remainingBudget, state.preferredCurrency)} left" else "${CurrencyUtils.format(abs(remainingBudget), state.preferredCurrency)} over",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (remainingBudget >= 0) CreditGreen else DebitRed
                                    )
                                }
                            }
                        }
                    }

                    // Section Header
                    item {
                        Text(
                            text = "CATEGORY TARGETS & VARIANCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Category Budgets - Unified Container Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            if (state.categorySpendList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No category budgets configured",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    state.categorySpendList.forEachIndexed { index, item ->
                                        val catColor = parseColorHex(item.category.colorHex)
                                        val limit = item.budgetLimit ?: 0.0
                                        val hasBudget = limit > 0

                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CategoryIconBadge(
                                                    categoryName = item.category.name,
                                                    iconKey = item.category.icon,
                                                    colorHex = item.category.colorHex,
                                                    size = 36.dp,
                                                    iconSize = 16.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.category.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (hasBudget) "Target: ${CurrencyUtils.format(limit, state.preferredCurrency)}" else "No target configured",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = CurrencyUtils.format(item.totalSpend, state.preferredCurrency),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (hasBudget) {
                                                        val remaining = limit - item.totalSpend
                                                        Text(
                                                            text = if (remaining >= 0) "${CurrencyUtils.format(remaining, state.preferredCurrency)} left" else "${CurrencyUtils.format(abs(remaining), state.preferredCurrency)} over",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = if (remaining >= 0) CreditGreen else DebitRed
                                                        )
                                                    }
                                                }
                                            }

                                            if (hasBudget) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { item.budgetProgress.toFloat().coerceIn(0f, 1f) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(5.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = if (item.isOverBudget) DebitRed else catColor,
                                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                )
                                            }
                                        }

                                        if (index < state.categorySpendList.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacer for clearance above the floating tab dock
            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
