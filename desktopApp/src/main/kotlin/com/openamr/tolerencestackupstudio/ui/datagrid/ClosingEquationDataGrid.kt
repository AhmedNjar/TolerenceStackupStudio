package com.openamr.tolerencestackupstudio.ui.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ClosingEquationDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.SpecLimitsDto
import com.openamr.tolerencestackupstudio.ui.EquationDisplay
import com.openamr.tolerencestackupstudio.ui.StackupViewModel

private val COLUMN_WIDTHS = listOf(50, 140, 180, 90, 80, 80, 40).map { it.dp }

@Composable
fun ClosingEquationDataGrid(viewModel: StackupViewModel) {
    Box(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))) {
        Column {
            HeaderRow()
            LazyColumn {
                items(viewModel.closingEquations, key = { it.id }) { equation ->
                    val nominal = viewModel.getCalculatedNominal(equation.id)
                    EquationRow(
                        equation = equation,
                        calculatedNominal = nominal,
                        viewModel = viewModel,
                        onChange = { updated -> viewModel.updateClosingEquation(equation.id) { updated } },
                        onDelete = { viewModel.removeClosingEquation(equation.id) },
                    )
                }
                item {
                    Button(onClick = viewModel::addClosingEquation, modifier = Modifier.padding(8.dp), shape = MaterialTheme.shapes.small) {
                        Text("+ Add Equation (Z)")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    val headers = listOf("Label", "Name", "Equation", "Nominal", "Lower", "Upper", "")
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(4.dp)) {
        headers.forEachIndexed { i, h ->
            Text(h, modifier = Modifier.width(COLUMN_WIDTHS[i]).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun EquationRow(
    equation: ClosingEquationDto,
    calculatedNominal: Double?,
    viewModel: StackupViewModel,
    onChange: (ClosingEquationDto) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledTextField(
            value = equation.label,
            width = COLUMN_WIDTHS[0],
            onCommit = { onChange(equation.copy(label = it)) },
        )
        LabeledTextField(
            value = equation.name,
            width = COLUMN_WIDTHS[1],
            onCommit = { onChange(equation.copy(name = it)) },
        )
        Column(modifier = Modifier.width(COLUMN_WIDTHS[2])) {
            LabeledTextField(
                value = equation.expression,
                width = COLUMN_WIDTHS[2],
                onCommit = { onChange(equation.copy(expression = it)) },
            )
            if (equation.expression.isNotBlank()) {
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    EquationDisplay(
                        expression = equation.expression,
                        components = viewModel.components,
                        selectedComponentId = viewModel.selectedComponentId,
                        onSelectComponent = { viewModel.selectComponent(it) },
                    )
                }
            }
        }
        
        // Calculated Nominal (Read-only)
        OutlinedTextField(
            value = calculatedNominal?.let { "%.4f".format(it) } ?: "---",
            onValueChange = {},
            modifier = Modifier.width(COLUMN_WIDTHS[3]).padding(horizontal = 2.dp),
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = if (calculatedNominal != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (calculatedNominal != null) FontWeight.Bold else FontWeight.Normal
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        OptionalNumericField(
            value = equation.specLimits?.lsl,
            width = COLUMN_WIDTHS[4],
            onCommit = { lsl ->
                val current = equation.specLimits ?: SpecLimitsDto(usl = 0.0, lsl = 0.0)
                onChange(equation.copy(specLimits = if (lsl == null) null else current.copy(lsl = lsl)))
            }
        )
        OptionalNumericField(
            value = equation.specLimits?.usl,
            width = COLUMN_WIDTHS[5],
            onCommit = { usl ->
                val current = equation.specLimits ?: SpecLimitsDto(usl = 0.0, lsl = 0.0)
                onChange(equation.copy(specLimits = if (usl == null) null else current.copy(usl = usl)))
            }
        )
        Box(modifier = Modifier.width(COLUMN_WIDTHS[6]), contentAlignment = Alignment.Center) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LabeledTextField(value: String, width: Dp, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onCommit(it) },
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun OptionalNumericField(
    value: Double?,
    width: Dp,
    onCommit: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.let { formatValue(it) } ?: "") }
    var isInvalid by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            if (newText.isEmpty()) {
                isInvalid = false
                onCommit(null)
            } else {
                val parsed = newText.toDoubleOrNull()
                if (parsed != null) {
                    isInvalid = false
                    onCommit(parsed)
                } else {
                    isInvalid = newText != "-" && newText != "."
                }
            }
        },
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        singleLine = true,
        isError = isInvalid,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

private fun formatValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
