package com.openamr.tolerencestackupstudio.ui.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalyzeStackupResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ClosingDimensionResultDto

@Composable
fun ResultsPanel(result: AnalyzeStackupResponseDto?) {
    if (result == null) {
        Text("Run an analysis to see results here.", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.caption)
        return
    }

    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(result.results) { closing ->
            Column {
                ClosingResultCard(closing)
                Divider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ClosingResultCard(closing: ClosingDimensionResultDto) {
    Column {
        Text(closing.closingId, style = MaterialTheme.typography.subtitle2)

        if (closing.error != null) {
            Text("Error: ${closing.error}", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
        } else {
            closing.worstCase?.let { wc ->
                Text(
                    "Worst Case — nominal ${fmt(wc.nominal)}, range [${fmt(wc.zMin)}, ${fmt(wc.zMax)}]",
                    style = MaterialTheme.typography.body2,
                )
            }
            closing.rss?.let { rss ->
                val shiftNote = rss.shiftFactorApplied?.let { " (shift factor ${fmt(it)})" } ?: ""
                Text(
                    "RSS$shiftNote — sigma ${fmt(rss.sigma)}, predicted [${fmt(rss.zMinPredicted)}, ${fmt(rss.zMaxPredicted)}]",
                    style = MaterialTheme.typography.body2,
                )
            }
            closing.monteCarlo?.let { mc ->
                Text(
                    "Monte Carlo (${mc.runs} runs) — mean ${fmt(mc.mean)}, std ${fmt(mc.stdDev)}, " +
                        "yield ${fmt(mc.yieldPct)}%, DPPM ${fmt(mc.dppm)}" +
                        (mc.dppmEstimationMethod?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.body2,
                )
                if (mc.cp != null && mc.cpk != null) {
                    Text("Cp ${fmt(mc.cp)}, Cpk ${fmt(mc.cpk)}", style = MaterialTheme.typography.body2)
                }
                Text("Skewness ${fmt(mc.skewness)}, Kurtosis ${fmt(mc.kurtosis)}", style = MaterialTheme.typography.body2)
                HistogramChart(mc.histogram, modifier = Modifier.padding(top = 4.dp))
            }
            if (closing.contributions.isNotEmpty()) {
                Text(
                    "Top contributor: ${closing.contributions.first().componentId} " +
                        "(${fmt(closing.contributions.first().contributionPct)}%)",
                    style = MaterialTheme.typography.body2,
                )
                ParetoChart(closing.contributions, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun fmt(v: Double): String = "%.4f".format(v)
