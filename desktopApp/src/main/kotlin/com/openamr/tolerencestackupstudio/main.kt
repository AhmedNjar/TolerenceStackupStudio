package com.openamr.tolerencestackupstudio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.openamr.tolerencestackupstudio.engine.EngineClient
import com.openamr.tolerencestackupstudio.engine.EngineProcessManager
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalysisMethod
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalysisOptionsDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.GenerateReportRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.GenerateReportResponseDto
import com.openamr.tolerencestackupstudio.ui.StackupViewModel
import com.openamr.tolerencestackupstudio.ui.StandardLookupDialog
import com.openamr.tolerencestackupstudio.ui.canvas.VectorChainCanvas
import com.openamr.tolerencestackupstudio.ui.datagrid.ComponentDataGrid
import com.openamr.tolerencestackupstudio.ui.results.ResultsPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame

/**
 * Phase 3: the real UI — editable component grid, interactive 2D vector-chain
 * canvas (tap a segment or the matching grid row to link them), a standard
 * fit lookup dialog, method selection, and a results panel. Replaces the
 * Phase 1/2 ping-and-one-analysis smoke test this file used to be.
 */
fun main() = application {
    var engineClient by remember { mutableStateOf<EngineClient?>(null) }
    var viewModel by remember { mutableStateOf<StackupViewModel?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    val processManager = remember { EngineProcessManager() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val transport = processManager.start()
            val client = EngineClient(transport)
            engineClient = client
            viewModel = StackupViewModel(client)
        } catch (t: Throwable) {
            startupError = t.message ?: t.toString()
        }
    }

    Window(onCloseRequest = ::exitApplication, title = "Tolerance Stack-up Studio") {
        val vm = viewModel
        val client = engineClient

        MaterialTheme {
            when {
                startupError != null -> Text("Engine failed to start: $startupError", modifier = Modifier.padding(16.dp))
                vm == null || client == null -> Column(modifier = Modifier.padding(16.dp)) {
                    Text("Starting engine...")
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
                else -> MainContent(vm, client, scope)
            }
        }
    }
}

@Composable
private fun MainContent(
    vm: StackupViewModel,
    client: EngineClient,
    scope: CoroutineScope,
) {
    var standardLookupComponentId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        AnalysisControls(vm, scope)

        Spacer(modifier = Modifier.height(4.dp))
        ExportControls(vm, client, scope)

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Components", style = MaterialTheme.typography.h6)
                ComponentDataGrid(
                    viewModel = vm,
                    onOpenStandardLookup = { componentId -> standardLookupComponentId = componentId },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Vector Chain", style = MaterialTheme.typography.h6)
                VectorChainCanvas(
                    components = vm.components,
                    selectedComponentId = vm.selectedComponentId,
                    onSelect = { vm.selectComponent(it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Results", style = MaterialTheme.typography.h6)
        ResultsPanel(vm.lastResult)
    }

    val lookupId = standardLookupComponentId
    if (lookupId != null) {
        val component = vm.components.firstOrNull { it.id == lookupId }
        if (component != null) {
            StandardLookupDialog(
                component = component,
                engineClient = client,
                onApply = { upperTol, lowerTol, standard, designation ->
                    vm.applyStandardFit(lookupId, standard, designation, upperTol, lowerTol)
                    standardLookupComponentId = null
                },
                onDismiss = { standardLookupComponentId = null },
            )
        }
    }
}

@Composable
private fun AnalysisControls(vm: StackupViewModel, scope: CoroutineScope) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnalysisMethod.entries.forEach { method ->
            val checked = method in vm.selectedMethods
            Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                    vm.selectedMethods = if (isChecked) vm.selectedMethods + method else vm.selectedMethods - method
                },
            )
            Text(method.name, modifier = Modifier.padding(end = 12.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(enabled = !vm.isBusy, onClick = { scope.launch { vm.runAnalysis() } }) {
            Text(if (vm.isBusy) "Running..." else "Run Analysis")
        }

        vm.lastError?.let {
            Text(it, color = MaterialTheme.colors.error, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/**
 * The engine re-runs the full analysis itself for a report (see
 * GenerateReportRequestDto's docstring) — this doesn't reuse vm.lastResult,
 * it sends the current components/closingEquations/methods and lets the
 * engine compute fresh, so the export always matches what's currently in
 * the grid even if "Run Analysis" hasn't been clicked since the last edit.
 */
@Composable
private fun ExportControls(vm: StackupViewModel, client: EngineClient, scope: CoroutineScope) {
    var isExporting by remember { mutableStateOf(false) }
    var lastSavedPath by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    fun export(extension: String, call: suspend (GenerateReportRequestDto) -> GenerateReportResponseDto) {
        // Shown synchronously on the calling (UI) thread — a modal save
        // dialog blocking briefly while the user picks a location is normal,
        // expected desktop UX, not a bug to work around.
        val path = chooseSavePath(suggestedName = "${vm.closingEquations.firstOrNull()?.label ?: "report"}.$extension", extension = extension)
            ?: return

        isExporting = true
        exportError = null
        scope.launch {
            try {
                val request = GenerateReportRequestDto(
                    chainId = "main-chain",
                    components = vm.components.toList(),
                    closingEquations = vm.closingEquations.toList(),
                    options = AnalysisOptionsDto(methods = vm.selectedMethods.toList()),
                    outputPath = path,
                )
                val response = call(request)
                lastSavedPath = response.outputPath
            } catch (t: Throwable) {
                exportError = t.message ?: t.toString()
            } finally {
                isExporting = false
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = !isExporting, onClick = { export("pdf", client::generatePdfReport) }) {
            Text("Export PDF")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(enabled = !isExporting, onClick = { export("xlsx", client::generateExcelReport) }) {
            Text("Export Excel")
        }
        if (isExporting) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exporting...", style = MaterialTheme.typography.caption)
        }
        lastSavedPath?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text("Saved: $it", style = MaterialTheme.typography.caption)
        }
        exportError?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
        }
    }
}

/** Native OS save dialog via plain AWT — no extra dependency needed, and
 * FileDialog delegates to the native picker rather than a Swing look-and-feel
 * one. Returns null if the user cancels. */
private fun chooseSavePath(suggestedName: String, extension: String): String? {
    val dialog = FileDialog(null as Frame?, "Save $extension report", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true // blocks the calling thread until the dialog closes
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    val fileName = if (file.endsWith(".$extension")) file else "$file.$extension"
    return directory + fileName
}
