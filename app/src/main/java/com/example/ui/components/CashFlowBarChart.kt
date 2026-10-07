package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.DailyCashFlowBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Responsive Cash Flow Bar Chart:
 * - Dynamically adapts to screen width and bar count so date labels NEVER collide or overlap.
 * - Supports interactive date selection: tapping a bar reveals exact inflow, outflow, and net balance for that day.
 * - Smooth horizontal scrolling when multiple dates are displayed, auto-scrolling to the most recent date.
 * - Material 3 visual standards with CreditGreen inflows and DebitRed outflows.
 */
@Composable
fun CashFlowBarChart(
    bars: List<DailyCashFlowBar>,
    currency: String = "GHS",
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No cash flow activity in this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedIndex by remember(bars) { mutableIntStateOf(bars.lastIndex.coerceAtLeast(0)) }
    val selectedBar = bars.getOrNull(selectedIndex) ?: bars.last()

    val maxVal = max(
        bars.maxOfOrNull { max(it.totalIncome, it.totalExpense) } ?: 1.0,
        10.0
    )

    val scrollState = rememberScrollState()

    // Auto-scroll to the latest date upon load
    LaunchedEffect(bars.size) {
        if (bars.size > 5) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val fullDateFormatter = remember { SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US) }
    val dayOfWeekFormatter = remember { SimpleDateFormat("EEE", Locale.US) }
    val dayOfMonthFormatter = remember { SimpleDateFormat("dd", Locale.US) }
    val monthShortFormatter = remember { SimpleDateFormat("MMM", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cash_flow_bar_chart")
    ) {
        // 1. Interactive Selected Date Inspection Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = fullDateFormatter.format(Date(selectedBar.dateTimestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val netAmount = selectedBar.totalIncome - selectedBar.totalExpense
                    val netColor = if (netAmount >= 0) CreditGreen else DebitRed
                    val netPrefix = if (netAmount >= 0) "+" else ""
                    Text(
                        text = "Net: $netPrefix${CurrencyUtils.format(netAmount, currency)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = netColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CreditGreen)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "In: +${CurrencyUtils.format(selectedBar.totalIncome, currency)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CreditGreen
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DebitRed)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Out: -${CurrencyUtils.format(selectedBar.totalExpense, currency)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = DebitRed
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Responsive Bar Chart Container
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val minSlotWidth = 48.dp
            val isScrollable = (bars.size * minSlotWidth.value) > availableWidth.value
            val slotWidth = if (isScrollable) minSlotWidth else (availableWidth / bars.size)

            val chartHeight = 150.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isScrollable) Modifier.horizontalScroll(scrollState) else Modifier
                    )
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (isScrollable) Arrangement.Start else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEachIndexed { index, bar ->
                    val isSelected = index == selectedIndex
                    val dateObj = Date(bar.dateTimestamp)
                    val dayOfWeek = dayOfWeekFormatter.format(dateObj)
                    val dayOfMonth = dayOfMonthFormatter.format(dateObj)
                    val monthShort = monthShortFormatter.format(dateObj)

                    Column(
                        modifier = Modifier
                            .width(slotWidth)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Bars Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val barWidth = (slotWidth.value * 0.28f).coerceIn(6f, 14f).dp

                                // Inflow Bar (Green)
                                val inRatio = (bar.totalIncome / maxVal).toFloat().coerceIn(0f, 1f)
                                val animatedInRatio by animateFloatAsState(targetValue = inRatio, label = "inRatio")
                                val inBarHeight = (chartHeight.value * 0.85f * animatedInRatio).coerceAtLeast(if (bar.totalIncome > 0) 4f else 0f).dp

                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .height(inBarHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (bar.totalIncome > 0) CreditGreen else Color.Transparent)
                                )

                                Spacer(modifier = Modifier.width(3.dp))

                                // Outflow Bar (Red)
                                val outRatio = (bar.totalExpense / maxVal).toFloat().coerceIn(0f, 1f)
                                val animatedOutRatio by animateFloatAsState(targetValue = outRatio, label = "outRatio")
                                val outBarHeight = (chartHeight.value * 0.85f * animatedOutRatio).coerceAtLeast(if (bar.totalExpense > 0) 4f else 0f).dp

                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .height(outBarHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (bar.totalExpense > 0) DebitRed else Color.Transparent)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Responsive, Non-Overlapping Date Labels
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    } else Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                        ) {
                            Text(
                                text = dayOfWeek,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                text = dayOfMonth,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Legend and hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap any day to inspect details",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CreditGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Inflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DebitRed)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Outflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
