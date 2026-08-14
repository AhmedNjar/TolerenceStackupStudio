package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

/** Mirrors schemas/component.schema.json and engine/src/tolerance_engine/models.py:Component */
@Serializable
data class ComponentDto(
    val id: String,
    val label: String,
    val name: String = "",
    val kind: ComponentKind,
    // Single source of truth for this component's value. Units follow `kind`:
    // length units for LINEAR, degrees for ANGULAR. (A separate `angleDeg`
    // field existed in the Phase 1 draft of this contract and was removed —
    // it duplicated `nominal` for angular components with no clear precedence
    // rule between the two.)
    val nominal: Double,
    val upperTol: Double,
    val lowerTol: Double,
    val distribution: DistributionType,
    val standardFit: StandardFitDto? = null,
    val thermal: ThermalDto? = null,
)

@Serializable
enum class ComponentKind { LINEAR, ANGULAR }

@Serializable
enum class DistributionType { NORMAL_3S, NORMAL_6S, UNIFORM, TRIANGULAR }

@Serializable
data class StandardFitDto(
    val standard: String, // "ISO286" | "ANSI_B4_1" | "ISO2768" | "ISO281"
    val designation: String, // e.g. "H7"
)

@Serializable
data class ThermalDto(
    val alpha: Double,
    val deltaT: Double,
)
