package com.trae.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight donut chart drawn with Canvas.
 *
 * Uses no charting library so there is no third-party API surface to keep in sync.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    thickness: Dp = 26.dp,
    emptyColor: Color = Color(0xFFE0E3EB),
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(0.0)
    val strokeWidthPx = thickness.value

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidthPx)
            val inset = strokeWidthPx / 2f
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val topLeft = Offset(inset, inset)

            if (total <= 0.0) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                return@Canvas
            }

            var startAngle = -90f
            slices.forEach { slice ->
                if (slice.value <= 0.0) return@forEach
                val sweep = (slice.value / total * 360.0).toFloat()
                // A 1-degree gap keeps adjacent slices visually distinct.
                val drawnSweep = if (sweep > 2f) sweep - 1f else sweep
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = drawnSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        center()
    }
}

data class DonutSlice(
    val label: String,
    val value: Double,
    val color: Color,
)
