package com.openamr.tolerencestackupstudio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.openamr.tolerencestackupstudio.engine.EngineClient
import com.openamr.tolerencestackupstudio.engine.protocol.dto.*
import com.openamr.tolerencestackupstudio.project.ProjectFileDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

enum class AppScreen { EDITOR, RESULTS }

/**
 * Holds all editable state for one stack-up chain and talks to the engine.
 * Deliberately a plain state holder (mutableStateListOf/mutableStateOf),
 * consistent with Phase 1's Main.kt — not an MVI setup. If this app grows
 * more chains/screens later, that's a reasonable point to reconsider.
 */
@OptIn(FlowPreview::class)
class StackupViewModel(
    private val engineClient: EngineClient,
    private val viewModelScope: CoroutineScope
) {

    var currentScreen by mutableStateOf(AppScreen.EDITOR)
        private set

    val components = mutableStateListOf<ComponentDto>()
    val closingEquations = mutableStateListOf<ClosingEquationDto>()

    var selectedComponentId by mutableStateOf<String?>(null)
        private set

    var selectedMethods by mutableStateOf(setOf(AnalysisMethod.WORST_CASE, AnalysisMethod.MONTE_CARLO))

    var lastResult by mutableStateOf<AnalyzeStackupResponseDto?>(null)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var isBusy by mutableStateOf(false)
        private set

    init {
        // Starter chain so the grid/canvas aren't empty on first launch —
        // the same shaft-housing gap used as the Phase 1/2 smoke test.
        components.addAll(
            listOf(
                ComponentDto(
                    id = newId(), label = "B", name = "Housing bore", kind = ComponentKind.LINEAR,
                    nominal = 25.0, upperTol = 0.021, lowerTol = 0.0, distribution = DistributionType.NORMAL_3S,
                ),
                ComponentDto(
                    id = newId(), label = "D", name = "Shaft diameter", kind = ComponentKind.LINEAR,
                    nominal = 25.0, upperTol = 0.0, lowerTol = -0.020, distribution = DistributionType.NORMAL_3S,
                ),
            )
        )
        closingEquations.add(ClosingEquationDto(id = newId(), label = "Z1", name = "Shaft/Bore Gap", expression = "B - D"))

        // Watch for changes and run live analysis for nominals
        snapshotFlow { components.toList() to closingEquations.toList() }
            .debounce(300)
            .onEach { runLiveAnalysis() }
            .launchIn(viewModelScope)
    }

    private suspend fun runLiveAnalysis() {
        if (components.isEmpty() || closingEquations.isEmpty()) return
        try {
            val response = engineClient.analyzeStackup(
                AnalyzeStackupRequestDto(
                    chainId = "main-chain-live",
                    components = components.toList(),
                    closingEquations = closingEquations.toList(),
                    options = AnalysisOptionsDto(methods = listOf(AnalysisMethod.WORST_CASE)),
                )
            )
            // Update lastResult with fresh nominals, but keep existing detailed results if any
            lastResult = response
        } catch (t: Throwable) {
            // Ignore live analysis errors (e.g. partial formula while typing)
        }
    }

    fun selectComponent(id: String?) {
        selectedComponentId = id
    }

    fun addComponent() {
        val nextLabel = nextAvailableLabel()
        components.add(
            ComponentDto(
                id = newId(), label = nextLabel, name = "", kind = ComponentKind.LINEAR,
                nominal = 10.0, upperTol = 0.1, lowerTol = 0.1, distribution = DistributionType.NORMAL_3S,
            )
        )
    }

    fun removeComponent(id: String) {
        components.removeAll { it.id == id }
        if (selectedComponentId == id) selectedComponentId = null
    }

    fun updateComponent(id: String, transform: (ComponentDto) -> ComponentDto) {
        val index = components.indexOfFirst { it.id == id }
        if (index >= 0) components[index] = transform(components[index])
    }

    fun addClosingEquation() {
        val nextLabel = nextAvailableClosingLabel()
        closingEquations.add(
            ClosingEquationDto(
                id = newId(),
                label = nextLabel,
                name = "",
                expression = "",
            )
        )
    }

    fun removeClosingEquation(id: String) {
        closingEquations.removeAll { it.id == id }
    }

    fun updateClosingEquation(id: String, transform: (ClosingEquationDto) -> ClosingEquationDto) {
        val index = closingEquations.indexOfFirst { it.id == id }
        if (index >= 0) closingEquations[index] = transform(closingEquations[index])
    }

    /** Returns the calculated nominal value for a closing equation from the last result. */
    fun getCalculatedNominal(closingId: String): Double? {
        val result = lastResult?.results?.find { it.closingId == closingId } ?: return null
        return result.worstCase?.nominal ?: result.rss?.nominal
    }

    /** Snapshot of everything Save Project needs to write to disk. */
    fun toProjectFile(chainId: String = "main-chain"): ProjectFileDto = ProjectFileDto(
        chainId = chainId,
        components = components.toList(),
        closingEquations = closingEquations.toList(),
        selectedMethods = selectedMethods.toList(),
    )

    /** Replaces all current state with a loaded project — used by Open Project. */
    fun loadProject(project: ProjectFileDto) {
        components.clear()
        components.addAll(project.components)
        closingEquations.clear()
        closingEquations.addAll(project.closingEquations)
        selectedMethods = project.selectedMethods.toSet()
        selectedComponentId = null
        lastResult = null
        lastError = null
    }

    private fun nextAvailableClosingLabel(): String {
        var n = closingEquations.size + 1
        val used = closingEquations.map { it.label }.toSet()
        while ("Z$n" in used) n++
        return "Z$n"
    }

    /** Applies an ISO286/ISO2768 lookup result to a component's tolerances,
     * called after StandardLookupDialog gets a result back from the engine. */
    fun applyStandardFit(componentId: String, standard: String, designationOrClass: String, upperTol: Double, lowerTol: Double) {
        updateComponent(componentId) {
            it.copy(
                upperTol = upperTol,
                lowerTol = lowerTol,
                standardFit = StandardFitDto(standard = standard, designation = designationOrClass),
            )
        }
    }

    fun navigateToResults() {
        currentScreen = AppScreen.RESULTS
    }

    fun navigateBack() {
        currentScreen = AppScreen.EDITOR
    }

    suspend fun runAnalysis() {
        if (components.isEmpty() || closingEquations.isEmpty()) {
            lastError = "Add at least one component and one closing equation before running analysis."
            return
        }
        isBusy = true
        lastError = null
        try {
            val response = engineClient.analyzeStackup(
                AnalyzeStackupRequestDto(
                    chainId = "main-chain",
                    components = components.toList(),
                    closingEquations = closingEquations.toList(),
                    options = AnalysisOptionsDto(methods = selectedMethods.toList()),
                )
            )
            lastResult = response
            val firstError = response.results.firstOrNull { it.error != null }?.error
            if (firstError != null) {
                lastError = firstError
            } else {
                navigateToResults()
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.toString()
        } finally {
            isBusy = false
        }
    }

    private fun nextAvailableLabel(): String {
        val used = components.map { it.label }.toSet()
        for (c in 'A'..'Z') {
            if (c.toString() !in used) return c.toString()
        }
        return "C${components.size}" // fallback past 26 components
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
