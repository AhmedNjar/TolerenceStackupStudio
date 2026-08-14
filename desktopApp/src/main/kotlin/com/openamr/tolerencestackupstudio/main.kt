package com.openamr.tolerencestackupstudio

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.openamr.tolerencestackupstudio.ui.AppScreen
import com.openamr.tolerencestackupstudio.ui.StackupViewModel
import com.openamr.tolerencestackupstudio.ui.StandardLookupDialog
import com.openamr.tolerencestackupstudio.ui.canvas.VectorChainCanvas
import com.openamr.tolerencestackupstudio.ui.datagrid.ClosingEquationDataGrid
import com.openamr.tolerencestackupstudio.ui.datagrid.ComponentDataGrid
import com.openamr.tolerencestackupstudio.ui.results.ResultsPanel
import com.openamr.tolerencestackupstudio.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame

fun main() = application {
    var engineClient by remember { mutableStateOf<EngineClient?>(null) }
    var viewModel by remember { mutableStateOf<StackupViewModel?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    val processManager = remember { EngineProcessManager() }
    val scope = rememberCoroutineScope()
    var darkTheme by remember { mutableStateOf(false) }

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

        AppTheme(darkTheme = darkTheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    startupError != null -> StartupError(startupError!!)
                    vm == null || client == null -> StartingEngine()
                    else -> MainScaffold(vm, client, scope, darkTheme, onThemeToggle = { darkTheme = !darkTheme })
                }
            }
        }
    }
}

@Composable
private fun StartupError(error: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Engine failed to start: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun StartingEngine() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Starting engine...", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    vm: StackupViewModel,
    client: EngineClient,
    scope: CoroutineScope,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tolerance Stack-up Studio") },
                actions = {
                    IconButton(onClick = onThemeToggle) {
                        Icon(if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Toggle Theme")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = vm.currentScreen,
                transitionSpec = {
                    if (targetState == AppScreen.RESULTS) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }.using(SizeTransform(clip = false))
                }
            ) { screen ->
                when (screen) {
                    AppScreen.EDITOR -> EditorScreen(vm, client, scope)
                    AppScreen.RESULTS -> ResultsScreen(vm, client, scope)
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(vm: StackupViewModel, client: EngineClient, scope: CoroutineScope) {
    var standardLookupComponentId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isWide = maxWidth >= 1000.dp
        
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AnalysisControls(vm, scope)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportControls(vm, client, scope)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isWide) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.weight(1f)) {
                        EditorSection(vm) { standardLookupComponentId = it }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        CanvasSection(vm)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    EditorSection(vm) { standardLookupComponentId = it }
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                        CanvasSection(vm)
                    }
                }
            }
        }
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
private fun ResultsScreen(vm: StackupViewModel, client: EngineClient, scope: CoroutineScope) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigateBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Analysis Results", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.weight(1f))
            ExportControls(vm, client, scope)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxSize(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            ResultsPanel(
                result = vm.lastResult,
                getComponentLabel = { id -> vm.components.find { it.id == id }?.label ?: id },
                getClosingLabel = { id -> vm.closingEquations.find { it.id == id }?.label ?: id },
            )
        }
    }
}

@Composable
private fun EditorSection(vm: StackupViewModel, onOpenStandardLookup: (String) -> Unit) {
    Column {
        Text("Components", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(300.dp)) {
            ComponentDataGrid(
                viewModel = vm,
                onOpenStandardLookup = onOpenStandardLookup,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Closing Equations", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(200.dp)) {
            ClosingEquationDataGrid(viewModel = vm)
        }
    }
}

@Composable
private fun CanvasSection(vm: StackupViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Visual Vector Chain", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            VectorChainCanvas(
                components = vm.components.toList(),
                selectedComponentId = vm.selectedComponentId,
                onSelect = { vm.selectComponent(it) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisControls(vm: StackupViewModel, scope: CoroutineScope) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalysisMethod.entries.forEach { method ->
            val checked = method in vm.selectedMethods
            FilterChip(
                selected = checked,
                onClick = {
                    vm.selectedMethods = if (!checked) vm.selectedMethods + method else vm.selectedMethods - method
                },
                label = { Text(method.name) },
                leadingIcon = if (checked) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            enabled = !vm.isBusy,
            onClick = { scope.launch { vm.runAnalysis() } },
            shape = MaterialTheme.shapes.medium
        ) {
            if (vm.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Analysis")
            }
        }

        vm.lastError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportControls(vm: StackupViewModel, client: EngineClient, scope: CoroutineScope) {
    var isExporting by remember { mutableStateOf(false) }
    var lastSavedPath by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    fun export(extension: String, call: suspend (GenerateReportRequestDto) -> GenerateReportResponseDto) {
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(enabled = !isExporting, onClick = { export("pdf", client::generatePdfReport) }) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PDF")
        }
        OutlinedButton(enabled = !isExporting, onClick = { export("xlsx", client::generateExcelReport) }) {
            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Excel")
        }
        if (isExporting) {
            Text("Exporting...", style = MaterialTheme.typography.labelSmall)
        }
        lastSavedPath?.let {
            Text("Saved!", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        exportError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun chooseSavePath(suggestedName: String, extension: String): String? {
    val dialog = FileDialog(null as Frame?, "Save $extension report", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    val fileName = if (file.endsWith(".$extension")) file else "$file.$extension"
    return directory + fileName
}
