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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.HistogramDto

/** Simple bar chart of Monte Carlo output bins — no interactivity, this is
 * a read-only summary view (contrast with VectorChainCanvas, which is
 * interactive because it links back to the data grid). */
@Composable
fun HistogramChart(histogram: HistogramDto, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    
    val counts = histogram.counts
    val edges = histogram.binEdges
    if (counts.isEmpty()) return

    val maxCount = (counts.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val chartHeight = size.height - 24f // leave room for axis labels
        val barWidth = size.width / counts.size

        // Draw baseline
        drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1f)

        counts.forEachIndexed { i, count ->
            val barHeight = chartHeight * (count.toFloat() / maxCount.toFloat())
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * barWidth + (barWidth * 0.05f), chartHeight - barHeight),
                size = Size(barWidth * 0.9f, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        val minLabel = "%.3f".format(edges.first())
        val maxLabel = "%.3f".format(edges.last())
        
        drawText(
            textMeasurer, minLabel, 
            topLeft = Offset(0f, chartHeight + 4f), 
            style = TextStyle(fontSize = 10.sp, color = labelColor)
        )
        
        val maxLabelLayout = textMeasurer.measure(maxLabel)
        drawText(
            textMeasurer, maxLabel, 
            topLeft = Offset(size.width - maxLabelLayout.size.width, chartHeight + 4f), 
            style = TextStyle(fontSize = 10.sp, color = labelColor)
        )
    }
}
