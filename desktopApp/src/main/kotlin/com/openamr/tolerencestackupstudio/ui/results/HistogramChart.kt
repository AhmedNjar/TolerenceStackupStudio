package com.openamr.tolerencestackupstudio.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val counts = histogram.counts
    val edges = histogram.binEdges
    if (counts.isEmpty()) return

    val maxCount = (counts.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val chartHeight = size.height - 24f // leave room for axis labels
        val barWidth = size.width / counts.size

        counts.forEachIndexed { i, count ->
            val barHeight = chartHeight * (count.toFloat() / maxCount.toFloat())
            drawRect(
                color = Color(0xFF1976D2),
                topLeft = Offset(i * barWidth, chartHeight - barHeight),
                size = Size(barWidth * 0.9f, barHeight),
            )
        }

        val minLabel = "%.3f".format(edges.first())
        val maxLabel = "%.3f".format(edges.last())
        drawText(textMeasurer, minLabel, topLeft = Offset(0f, chartHeight + 4f), style = TextStyle(fontSize = 10.sp, color = Color.Gray))
        val maxLabelWidth = textMeasurer.measure(maxLabel).size.width
        drawText(textMeasurer, maxLabel, topLeft = Offset(size.width - maxLabelWidth, chartHeight + 4f), style = TextStyle(fontSize = 10.sp, color = Color.Gray))
    }
}
