package com.openamr.tolerencestackupstudio.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ContributionDto

/** Bars = per-component variance contribution %, line = cumulative %,
 * matching the same shape as the PDF report's matplotlib Pareto chart. */
@Composable
fun ParetoChart(
    contributions: List<ContributionDto>,
    getComponentLabel: (String) -> String,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    if (contributions.isEmpty()) return

    val sorted = contributions.sortedByDescending { it.contributionPct }
    var running = 0.0
    val cumulative = sorted.map { running += it.contributionPct; running }

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val chartHeight = size.height - 32f
        val barWidth = size.width / sorted.size
        val maxPct = 100.0

        // Draw baseline
        drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1f)

        sorted.forEachIndexed { i, c ->
            val label = getComponentLabel(c.componentId)
            val barHeight = (chartHeight * (c.contributionPct / maxPct)).toFloat()
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * barWidth + (barWidth * 0.1f), chartHeight - barHeight),
                size = Size(barWidth * 0.8f, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
            
            val labelLayout = textMeasurer.measure(label, TextStyle(fontSize = 10.sp))
            drawText(
                textMeasurer, label,
                topLeft = Offset(i * barWidth + barWidth * 0.5f - labelLayout.size.width / 2f, chartHeight + 6f),
                style = TextStyle(fontSize = 10.sp, color = labelColor),
            )
        }

        val points = cumulative.mapIndexed { i, pct ->
            Offset(i * barWidth + barWidth * 0.5f, chartHeight - (chartHeight * (pct / maxPct)).toFloat())
        }
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEach { p -> drawCircle(lineColor, radius = 4.dp.toPx(), center = p) }
    }
}
