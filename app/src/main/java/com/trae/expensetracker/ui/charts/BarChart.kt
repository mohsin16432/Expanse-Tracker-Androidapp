package com.trae.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.ui.theme.TextSecondary

/**
 * Minimal grouped bar chart drawn with Canvas.
 *
 * Each entry renders a pair of bars (spend / income) above a label row.
 */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    if (entries.isEmpty()) return

    val maxValue = entries.maxOf { maxOf(it.primary, it.secondary) }.coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val slotWidth = size.width / entries.size
            val barWidth = (slotWidth * 0.28f).coerceAtLeast(2f)
            val gap = barWidth * 0.35f
            val usableHeight = size.height

            entries.forEachIndexed { index, entry ->
                val slotStart = slotWidth * index
                val pairWidth = barWidth * 2 + gap
                val startX = slotStart + (slotWidth - pairWidth) / 2f

                val primaryHeight = (entry.primary / maxValue * usableHeight).toFloat()
                val secondaryHeight = (entry.secondary / maxValue * usableHeight).toFloat()

                drawRect(
                    color = primaryColor,
                    topLeft = Offset(startX, usableHeight - primaryHeight),
                    size = Size(barWidth, primaryHeight),
                )
                drawRect(
                    color = secondaryColor,
                    topLeft = Offset(startX + barWidth + gap, usableHeight - secondaryHeight),
                    size = Size(barWidth, secondaryHeight),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            entries.forEach { entry ->
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

data class BarEntry(
    val label: String,
    val primary: Double,
    val secondary: Double,
)
