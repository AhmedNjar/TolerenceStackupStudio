package com.openamr.tolerencestackupstudio.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    if (contributions.isEmpty()) return

    // contributions already come sorted descending from the engine, but sort
    // defensively here too since this view's correctness (a Pareto chart)
    // depends on that order, not just on trusting the caller.
    val sorted = contributions.sortedByDescending { it.contributionPct }
    var running = 0.0
    val cumulative = sorted.map { running += it.contributionPct; running }

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val chartHeight = size.height - 24f
        val barWidth = size.width / sorted.size
        val maxPct = 100.0

        sorted.forEachIndexed { i, c ->
            val label = getComponentLabel(c.componentId)
            val barHeight = (chartHeight * (c.contributionPct / maxPct)).toFloat()
            drawRect(
                color = Color(0xFF546E7A),
                topLeft = Offset(i * barWidth, chartHeight - barHeight),
                size = Size(barWidth * 0.7f, barHeight),
            )
            val labelWidth = textMeasurer.measure(label).size.width
            drawText(
                textMeasurer, label,
                topLeft = Offset(i * barWidth + barWidth * 0.35f - labelWidth / 2f, chartHeight + 4f),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray),
            )
        }

        val points = cumulative.mapIndexed { i, pct ->
            Offset(i * barWidth + barWidth * 0.35f, chartHeight - (chartHeight * (pct / maxPct)).toFloat())
        }
        for (i in 0 until points.size - 1) {
            drawLine(Color(0xFFD32F2F), points[i], points[i + 1], strokeWidth = 3f, cap = StrokeCap.Round)
        }
        points.forEach { p -> drawCircle(Color(0xFFD32F2F), radius = 4f, center = p) }
    }
}
