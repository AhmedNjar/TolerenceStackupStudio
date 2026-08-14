package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

/** discriminator lives in the `standard` field itself, same as the engine's payload dict keys. */
@Serializable
data class Iso286FitRequestDto(
    val standard: String = "ISO286",
    val basicSize: Double,
    /** e.g. "H7", "h6", "js6". Only H, h, JS/js are supported by the engine —
     * other letters return an ERROR rather than a guessed value. See
     * engine/standards/iso286.py's module docstring for why. */
    val designation: String,
)

@Serializable
data class Iso2768ToleranceRequestDto(
    val standard: String = "ISO2768",
    val nominalSize: Double,
    val toleranceClass: String, // "f" | "m" | "c" | "v"
)

/** ANSI B4.1 is NOT implemented on the engine side — this DTO exists for
 * contract completeness, but calling with it always returns an ERROR. */
@Serializable
data class AnsiB4FitRequestDto(
    val standard: String = "ANSI_B4_1",
    val basicSize: Double,
    val fitClass: String,
)

@Serializable
data class StandardTableResultDto(
    val standard: String,
    val upperTol: Double,
    val lowerTol: Double,
    val exact: Boolean,
)
