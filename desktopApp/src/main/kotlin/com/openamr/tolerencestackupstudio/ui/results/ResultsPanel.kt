package com.openamr.tolerencestackupstudio.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalyzeStackupResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ClosingDimensionResultDto

@Composable
fun ResultsPanel(
    result: AnalyzeStackupResponseDto?,
    getComponentLabel: (String) -> String,
    getClosingLabel: (String) -> String,
) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text("No analysis data yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(result.results) { closing ->
            ClosingResultCard(closing, getComponentLabel, getClosingLabel)
        }
    }
}

@Composable
private fun ClosingResultCard(
    closing: ClosingDimensionResultDto,
    getComponentLabel: (String) -> String,
    getClosingLabel: (String) -> String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Dimension: ${getClosingLabel(closing.closingId)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            if (closing.error != null) {
                Text(
                    text = "Analysis Error: ${closing.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                ResultMetricSection(closing)
                
                if (closing.contributions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Variance Contribution (Pareto)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    ParetoChart(closing.contributions, getComponentLabel, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultMetricSection(closing: ClosingDimensionResultDto) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Worst Case Section
        closing.worstCase?.let { wc ->
            MetricRow("Worst Case", "Nominal: ${formatValue(wc.nominal)}", "Range: [${formatValue(wc.zMin)}, ${formatValue(wc.zMax)}]")
        }

        // RSS Section
        closing.rss?.let { rss ->
            val title = if (rss.shiftFactorApplied != null) "RSS (Shift ${formatValue(rss.shiftFactorApplied)})" else "RSS"
            MetricRow(title, "Sigma: ${formatValue(rss.sigma)}", "Predicted: [${formatValue(rss.zMinPredicted)}, ${formatValue(rss.zMaxPredicted)}]")
        }

        // Monte Carlo Section
        closing.monteCarlo?.let { mc ->
            Column {
                MetricRow(
                    "Monte Carlo (${mc.runs} runs)",
                    "Mean: ${formatValue(mc.mean)}",
                    "Yield: ${formatValue(mc.yieldPct)}%",
                    primaryColor = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                HistogramChart(mc.histogram, modifier = Modifier.padding(top = 8.dp))
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DPPM: ${formatValue(mc.dppm)}", style = MaterialTheme.typography.labelMedium)
                    if (mc.cpk != null) {
                        Text("Cpk: ${formatValue(mc.cpk)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(title: String, val1: String, val2: String, primaryColor: Boolean = false) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (primaryColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(val1, style = MaterialTheme.typography.bodyMedium)
            Text(val2, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatValue(v: Double): String = "%.4f".format(v)
