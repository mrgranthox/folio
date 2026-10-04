package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningAmber
import kotlin.math.abs

@Composable
fun VarianceBadge(
    percentage: Double,
    isExpense: Boolean = false,
    modifier: Modifier = Modifier
) {
    // For expenses: decrease is good (green), increase is bad (red)
    // For income: increase is good (green), decrease is bad (red)
    val isGood = if (isExpense) percentage <= 0 else percentage >= 0
    val color = if (isGood) CreditGreen else DebitRed
    val bgColor = color.copy(alpha = 0.14f)
    val sign = if (percentage > 0) "+" else if (percentage < 0) "-" else ""

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (percentage >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "$sign${String.format("%.1f", abs(percentage))}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
fun BudgetVarianceBadge(
    isOverBudget: Boolean,
    budgetProgress: Double,
    modifier: Modifier = Modifier
) {
    val color = if (isOverBudget) DebitRed else if (budgetProgress > 0.85) WarningAmber else CreditGreen
    val bgColor = color.copy(alpha = 0.14f)
    val text = if (isOverBudget) {
        "Over budget (${String.format("%.0f", budgetProgress * 100)}%)"
    } else {
        "${String.format("%.0f", budgetProgress * 100)}% used"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isOverBudget) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = color
        )
    }
}
