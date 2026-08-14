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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentKind
import com.openamr.tolerencestackupstudio.engine.protocol.dto.DistributionType
import com.openamr.tolerencestackupstudio.ui.StackupViewModel

private val COLUMN_WIDTHS = listOf(50, 110, 90, 90, 80, 80, 110, 90, 40).map { it.dp }

@Composable
fun ComponentDataGrid(viewModel: StackupViewModel, onOpenStandardLookup: (componentId: String) -> Unit) {
    Box(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))) {
        Column {
            HeaderRow()
            LazyColumn {
                items(viewModel.components, key = { it.id }) { component ->
                    ComponentRow(
                        component = component,
                        selected = component.id == viewModel.selectedComponentId,
                        onSelect = { viewModel.selectComponent(component.id) },
                        onChange = { updated -> viewModel.updateComponent(component.id) { updated } },
                        onDelete = { viewModel.removeComponent(component.id) },
                        onOpenStandardLookup = { onOpenStandardLookup(component.id) },
                    )
                }
                item {
                    Button(onClick = viewModel::addComponent, modifier = Modifier.padding(8.dp), shape = MaterialTheme.shapes.small) {
                        Text("+ Add Component")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    val headers = listOf("Label", "Name", "Kind", "Nominal", "Upper Dev", "Lower Dev", "Distribution", "Std Fit", "")
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(4.dp)) {
        headers.forEachIndexed { i, h ->
            Text(h, modifier = Modifier.width(COLUMN_WIDTHS[i]).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ComponentRow(
    component: ComponentDto,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (ComponentDto) -> Unit,
    onDelete: () -> Unit,
    onOpenStandardLookup: () -> Unit,
) {
    val rowBackground = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledTextField(
            value = component.label,
            width = COLUMN_WIDTHS[0],
            onCommit = { onChange(component.copy(label = it)) },
        )
        LabeledTextField(
            value = component.name,
            width = COLUMN_WIDTHS[1],
            onCommit = { onChange(component.copy(name = it)) },
        )
        EnumDropdown(
            value = component.kind,
            options = ComponentKind.entries,
            width = COLUMN_WIDTHS[2],
            onSelect = { onChange(component.copy(kind = it)) },
        )
        NumericField(
            // Angular components store their value (degrees) in `nominal` too —
            // see Component.kind docstring on the Python side for why there's
            // no separate angleDeg field.
            value = component.nominal,
            width = COLUMN_WIDTHS[3],
            onCommit = { onChange(component.copy(nominal = it)) },
        )
        // Tolerance fields are keyed on standardFit as well as the component id
        // so their local edit-buffer resets when a standard-lookup applies a
        // NEW value externally, but not on every recomposition caused by the
        // user's own typing in this same field.
        NumericField(
            value = component.upperTol,
            width = COLUMN_WIDTHS[4],
            resetKey = component.standardFit,
            onCommit = { onChange(component.copy(upperTol = it)) },
        )
        NumericField(
            value = component.lowerTol,
            width = COLUMN_WIDTHS[5],
            resetKey = component.standardFit,
            onCommit = { onChange(component.copy(lowerTol = it)) },
        )
        EnumDropdown(
            value = component.distribution,
            options = DistributionType.entries,
            width = COLUMN_WIDTHS[6],
            onSelect = { onChange(component.copy(distribution = it)) },
        )
        Box(modifier = Modifier.width(COLUMN_WIDTHS[7]), contentAlignment = Alignment.Center) {
            IconButton(onClick = onOpenStandardLookup, modifier = Modifier.size(28.dp)) {
                Text("\u2699", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) // gear symbol; avoids depending on material-icons-extended
            }
        }
        Box(modifier = Modifier.width(COLUMN_WIDTHS[8]), contentAlignment = Alignment.Center) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Text("\u2715", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) // multiplication-x symbol
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
private fun NumericField(
    value: Double,
    width: Dp,
    resetKey: Any? = null,
    onCommit: (Double) -> Unit,
) {
    var text by remember(resetKey) { mutableStateOf(formatNumber(value)) }
    var isInvalid by remember(resetKey) { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val parsed = newText.toDoubleOrNull()
            if (parsed != null) {
                isInvalid = false
                onCommit(parsed)
            } else {
                // Allow transient invalid states while typing (e.g. "1.", "-")
                // without losing what the user typed or committing garbage.
                isInvalid = newText.isNotEmpty() && newText != "-" && newText != "."
            }
        },
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        singleLine = true,
        isError = isInvalid,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun <T : Enum<T>> EnumDropdown(value: T, options: List<T>, width: Dp, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(width)) {
        Text(
            text = value.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { expanded = true }.padding(4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
