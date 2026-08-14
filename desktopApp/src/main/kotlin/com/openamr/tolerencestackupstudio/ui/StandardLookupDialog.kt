package com.openamr.tolerencestackupstudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openamr.tolerencestackupstudio.engine.EngineClient
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentDto
import kotlinx.coroutines.launch

private enum class StandardChoice { ISO286, ISO2768, ANSI_B4_1 }

/**
 * A standard lookup applies to ONE dimension at a time (a fit or a general
 * tolerance is inherently a per-dimension property), so this dialog is
 * opened from a single grid row's "⚙" button, not as a bulk/multi-select
 * action — see ComponentDataGrid's onOpenStandardLookup wiring.
 */
@Composable
fun StandardLookupDialog(
    component: ComponentDto,
    engineClient: EngineClient,
    onApply: (upperTol: Double, lowerTol: Double, standard: String, designationOrClass: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var standard by remember { mutableStateOf(StandardChoice.ISO286) }
    var standardMenuExpanded by remember { mutableStateOf(false) }

    var basicSizeText by remember { mutableStateOf(formatMm(component.nominal)) }
    var designation by remember { mutableStateOf("H7") }
    var toleranceClass by remember { mutableStateOf("m") }
    var classMenuExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standard fit lookup — ${component.label}") },
        text = {
            Column {
                Row {
                    Text("Standard: ")
                    Text(
                        text = standard.name,
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.body1,
                    )
                    TextButton(onClick = { standardMenuExpanded = true }) { Text("change") }
                    DropdownMenu(expanded = standardMenuExpanded, onDismissRequest = { standardMenuExpanded = false }) {
                        StandardChoice.entries.forEach { choice ->
                            DropdownMenuItem(onClick = { standard = choice; standardMenuExpanded = false; errorMessage = null }) {
                                Text(choice.name)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (standard) {
                    StandardChoice.ISO286 -> {
                        OutlinedTextField(
                            value = basicSizeText, onValueChange = { basicSizeText = it },
                            label = { Text("Basic size (mm)") },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = designation, onValueChange = { designation = it },
                            label = { Text("Designation (e.g. H7, h6, js6)") },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Only H, h, and JS/js are supported — other letters (f, g, k, n, p, s, ...) " +
                                "require fundamental-deviation data this app doesn't ship (see docs). They'll " +
                                "return an error here rather than a guessed number.",
                            style = MaterialTheme.typography.caption,
                        )
                    }
                    StandardChoice.ISO2768 -> {
                        OutlinedTextField(
                            value = basicSizeText, onValueChange = { basicSizeText = it },
                            label = { Text("Nominal size (mm)") },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text("Class: ")
                            Text(toleranceClass, modifier = Modifier.padding(start = 4.dp))
                            TextButton(onClick = { classMenuExpanded = true }) { Text("change") }
                            DropdownMenu(expanded = classMenuExpanded, onDismissRequest = { classMenuExpanded = false }) {
                                listOf("f", "m", "c", "v").forEach { c ->
                                    DropdownMenuItem(onClick = { toleranceClass = c; classMenuExpanded = false }) { Text(c) }
                                }
                            }
                        }
                    }
                    StandardChoice.ANSI_B4_1 -> {
                        Text(
                            "ANSI B4.1 is not implemented on the engine side — this standard's fit-class limit " +
                                "tables weren't reproduced with enough confidence to ship. Selecting Apply here " +
                                "will return an error.",
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = {
                    val basicSize = basicSizeText.toDoubleOrNull()
                    if (basicSize == null) {
                        errorMessage = "Enter a valid numeric size"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val result = when (standard) {
                                StandardChoice.ISO286 -> engineClient.getIso286Fit(basicSize, designation)
                                StandardChoice.ISO2768 -> engineClient.getIso2768Tolerance(basicSize, toleranceClass)
                                StandardChoice.ANSI_B4_1 -> throw IllegalStateException("ANSI B4.1 is not implemented")
                            }
                            val label = if (standard == StandardChoice.ISO286) designation else toleranceClass
                            onApply(result.upperTol, result.lowerTol, standard.name, label)
                        } catch (t: Throwable) {
                            errorMessage = t.message ?: t.toString()
                        } finally {
                            isLoading = false
                        }
                    }
                },
            ) { Text(if (isLoading) "Looking up..." else "Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatMm(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
