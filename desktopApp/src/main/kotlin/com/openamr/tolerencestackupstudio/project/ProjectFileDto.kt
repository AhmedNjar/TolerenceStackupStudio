package com.openamr.tolerencestackupstudio.project

import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalysisMethod
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ClosingEquationDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentDto
import kotlinx.serialization.Serializable

/**
 * The on-disk project file format (.tsproj, plain JSON).
 */
@Serializable
data class ProjectFileDto(
    val formatVersion: Int = 1,
    val chainId: String,
    val components: List<ComponentDto>,
    val closingEquations: List<ClosingEquationDto>,
    val selectedMethods: List<AnalysisMethod>,
)
