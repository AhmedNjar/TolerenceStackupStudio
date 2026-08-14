package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

/**
 * Shared request shape for both GENERATE_PDF_REPORT and
 * GENERATE_EXCEL_REPORT. The engine re-runs the full analysis pipeline from
 * these inputs — it does NOT accept a previously-computed
 * AnalyzeStackupResponseDto — so the report always reflects current state.
 */
@Serializable
data class GenerateReportRequestDto(
    val chainId: String,
    val components: List<ComponentDto>,
    val closingEquations: List<ClosingEquationDto>,
    val options: AnalysisOptionsDto,
    /** Absolute filesystem path. The engine writes the file directly —
     * it is NOT returned as base64, since both processes run on the same
     * machine and writing straight to disk avoids inflating the JSON
     * payload with a binary blob. */
    val outputPath: String,
)

@Serializable
data class GenerateReportResponseDto(
    val outputPath: String,
    val fileSizeBytes: Long,
)
