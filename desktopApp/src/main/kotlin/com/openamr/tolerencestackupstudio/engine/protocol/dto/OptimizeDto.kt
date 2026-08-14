package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

@Serializable
data class CostToleranceParamsDto(
    val componentId: String,
    val costCoefficientK: Double,
    val costExponentP: Double,
    val minTol: Double,
    val maxTol: Double,
)

@Serializable
data class OptimizeTolerancesRequestDto(
    val chainId: String,
    /** Components not referenced in `optimizable` are held fixed at their given tolerances. */
    val components: List<ComponentDto>,
    /** specLimits is REQUIRED here — it defines the yield constraint. */
    val closingEquation: ClosingEquationDto,
    val targetYieldPct: Double,
    /** Each optimizable component gets a SYMMETRIC tolerance in the result
     * (upperTol == lowerTol) — asymmetric allocation isn't explored. */
    val optimizable: List<CostToleranceParamsDto>,
    val monteCarloVerificationRuns: Int = 20_000,
    val randomSeed: Long? = null,
)

@Serializable
data class OptimizedToleranceDto(val componentId: String, val upperTol: Double, val lowerTol: Double)

@Serializable
data class MonteCarloVerificationDto(
    val yieldPct: Double,
    val cp: Double? = null,
    val cpk: Double? = null,
    val skewness: Double,
    val kurtosis: Double,
    val runs: Int,
)

@Serializable
data class OptimizeTolerancesResponseDto(
    val success: Boolean,
    val message: String,
    val iterations: Int,
    val optimizedTolerances: List<OptimizedToleranceDto>,
    val totalCost: Double,
    /** Yield predicted by the fast RSS/normal-approximation constraint model
     * used inside the optimizer loop — compare against monteCarloVerification
     * below, which is a real simulation on the optimized result. A large gap
     * usually means the stack-up is meaningfully non-normal. */
    val predictedYieldPct: Double,
    val monteCarloVerification: MonteCarloVerificationDto,
)
