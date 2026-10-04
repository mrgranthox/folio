package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.DailyCashFlowBar
import kotlin.math.max

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

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val maxVal = max(
        bars.maxOfOrNull { max(it.totalIncome, it.totalExpense) } ?: 1.0,
        100.0
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CreditGreen)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Inflow", style = MaterialTheme.typography.labelSmall, color = labelColor)

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(DebitRed)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Outflow", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 8.dp)
        ) {
            val chartWidth = size.width
            val bottomLabelHeight = 24.dp.toPx()
            val chartHeight = size.height - bottomLabelHeight

            // Draw horizontal reference grid lines (0%, 50%, 100%)
            val lineCount = 3
            for (i in 0..lineCount) {
                val y = chartHeight - (i.toFloat() / lineCount) * chartHeight
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val itemCount = bars.size
            val slotWidth = chartWidth / itemCount
            val barGroupWidth = slotWidth * 0.65f
            val singleBarWidth = (barGroupWidth / 2f) * 0.85f

            bars.forEachIndexed { index, bar ->
                val centerX = index * slotWidth + (slotWidth / 2f)

                // Inflow bar (Green)
                val inHeight = (bar.totalIncome / maxVal).toFloat().coerceIn(0f, 1f) * (chartHeight - 8f)
                val inTop = chartHeight - inHeight
                val inLeft = centerX - barGroupWidth / 2f

                if (inHeight > 0) {
                    drawRoundRect(
                        color = CreditGreen,
                        topLeft = Offset(inLeft, inTop),
                        size = Size(singleBarWidth, inHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Outflow bar (Red)
                val outHeight = (bar.totalExpense / maxVal).toFloat().coerceIn(0f, 1f) * (chartHeight - 8f)
                val outTop = chartHeight - outHeight
                val outLeft = inLeft + singleBarWidth + 2.dp.toPx()

                if (outHeight > 0) {
                    drawRoundRect(
                        color = DebitRed,
                        topLeft = Offset(outLeft, outTop),
                        size = Size(singleBarWidth, outHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Date label under the bar
                val textLayoutResult = textMeasurer.measure(
                    text = bar.label,
                    style = TextStyle(fontSize = 10.sp, color = labelColor)
                )
                val textX = centerX - (textLayoutResult.size.width / 2f)
                val textY = chartHeight + 6.dp.toPx()

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(textX, textY)
                )
            }
        }
    }
}
