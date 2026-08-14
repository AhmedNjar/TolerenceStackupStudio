package com.openamr.tolerencestackupstudio.engine.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Mirrors schemas/envelope.schema.json. Every frame sent or received on the
 * engine socket is one of these — see docs/ARCHITECTURE.md for the framing
 * (4-byte big-endian length prefix + UTF-8 JSON) and the handshake protocol.
 */
@Serializable
data class Envelope(
    val requestId: String,
    val type: String,
    val success: Boolean? = null,
    val error: EngineError? = null,
    val payload: JsonObject,
)

@Serializable
data class EngineError(
    val code: String,
    val message: String,
)

@Suppress("unused")
object EnvelopeType {
    const val PING = "PING"
    const val PONG = "PONG"
    const val ANALYZE_STACKUP = "ANALYZE_STACKUP"
    const val ANALYZE_STACKUP_RESULT = "ANALYZE_STACKUP_RESULT"
    const val OPTIMIZE_TOLERANCES = "OPTIMIZE_TOLERANCES"
    const val OPTIMIZE_TOLERANCES_RESULT = "OPTIMIZE_TOLERANCES_RESULT"
    const val GET_STANDARD_TABLE = "GET_STANDARD_TABLE"
    const val GET_STANDARD_TABLE_RESULT = "GET_STANDARD_TABLE_RESULT"
    const val LIST_TEMPLATES = "LIST_TEMPLATES"
    const val LIST_TEMPLATES_RESULT = "LIST_TEMPLATES_RESULT"
    const val INSTANTIATE_TEMPLATE = "INSTANTIATE_TEMPLATE"
    const val INSTANTIATE_TEMPLATE_RESULT = "INSTANTIATE_TEMPLATE_RESULT"
    const val GENERATE_PDF_REPORT = "GENERATE_PDF_REPORT"
    const val GENERATE_PDF_REPORT_RESULT = "GENERATE_PDF_REPORT_RESULT"
    const val GENERATE_EXCEL_REPORT = "GENERATE_EXCEL_REPORT"
    const val GENERATE_EXCEL_REPORT_RESULT = "GENERATE_EXCEL_REPORT_RESULT"
    const val PROGRESS = "PROGRESS"
    const val ERROR = "ERROR"
}
