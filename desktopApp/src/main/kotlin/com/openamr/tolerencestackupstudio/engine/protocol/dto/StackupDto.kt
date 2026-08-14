package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

// ---- Request ----------------------------------------------------------

@Serializable
data class AnalyzeStackupRequestDto(
    val chainId: String,
    val components: List<ComponentDto>,
    val closingEquations: List<ClosingEquationDto>,
    val options: AnalysisOptionsDto,
)

@Serializable
data class ClosingEquationDto(
    val id: String,
    val label: String,
    val expression: String,
    val specLimits: SpecLimitsDto? = null,
)

@Serializable
data class SpecLimitsDto(val usl: Double, val lsl: Double)

@Serializable
enum class AnalysisMethod { WORST_CASE, RSS, MODIFIED_RSS, MONTE_CARLO }

@Serializable
data class AnalysisOptionsDto(
    val methods: List<AnalysisMethod>,
    val monteCarloRuns: Int = 10_000,
    val modifiedRssShiftFactor: Double? = null,
    val randomSeed: Long? = null,
)

// ---- Response -----------------------------------------------------------

@Serializable
data class AnalyzeStackupResponseDto(
    val chainId: String,
    val results: List<ClosingDimensionResultDto>,
)

@Serializable
data class ClosingDimensionResultDto(
    val closingId: String,
    val error: String? = null,
    val worstCase: WorstCaseResultDto? = null,
    val rss: RssResultDto? = null,
    val monteCarlo: MonteCarloResultDto? = null,
    val contributions: List<ContributionDto> = emptyList(),
)

@Serializable
data class WorstCaseResultDto(val nominal: Double, val zMin: Double, val zMax: Double)

@Serializable
data class RssResultDto(
    val nominal: Double,
    val sigma: Double,
    val zMinPredicted: Double,
    val zMaxPredicted: Double,
    val shiftFactorApplied: Double? = null,
)

@Serializable
data class MonteCarloResultDto(
    val runs: Int,
    val mean: Double,
    val stdDev: Double,
    val skewness: Double,
    val kurtosis: Double,
    val cp: Double? = null,
    val cpk: Double? = null,
    val yieldPct: Double,
    val dppm: Double,
    /** "EMPIRICAL" (counted directly) or "NORMAL_FIT_TAIL" (zero defects observed
     * at this sample size, so a normal-fit tail estimate was used instead —
     * see engine/statistics/monte_carlo.py). Null when there were no spec limits. */
    val dppmEstimationMethod: String? = null,
    val histogram: HistogramDto,
)

@Serializable
data class HistogramDto(val binEdges: List<Double>, val counts: List<Int>)

@Serializable
data class ContributionDto(val componentId: String, val contributionPct: Double)
