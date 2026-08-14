package com.openamr.tolerencestackupstudio.engine

import com.openamr.tolerencestackupstudio.engine.protocol.Envelope
import com.openamr.tolerencestackupstudio.engine.protocol.EnvelopeType
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalyzeStackupRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.AnalyzeStackupResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.GenerateReportRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.GenerateReportResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.InstantiateTemplateRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.InstantiateTemplateResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.Iso2768ToleranceRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.Iso286FitRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ListTemplatesResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.OptimizeTolerancesRequestDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.OptimizeTolerancesResponseDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.StandardTableResultDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/**
 * Typed façade over [EngineTransport]. UI code (Phase 3) talks to this, not
 * to raw envelopes/sockets. Each public method sends one request DTO (or no
 * body) and decodes one response DTO; [callWithBody]/[callNoBody] hold the
 * shared envelope plumbing so adding a new engine call is a one-liner.
 */
class EngineClient(private val transport: EngineTransport) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun ping(): Boolean {
        val response = send(EnvelopeType.PING, buildJsonObject {})
        return response.success == true && response.type == EnvelopeType.PONG
    }

    suspend fun analyzeStackup(request: AnalyzeStackupRequestDto): AnalyzeStackupResponseDto =
        callWithBody(
            EnvelopeType.ANALYZE_STACKUP,
            request, AnalyzeStackupRequestDto.serializer(),
            AnalyzeStackupResponseDto.serializer(),
        )

    suspend fun optimizeTolerances(request: OptimizeTolerancesRequestDto): OptimizeTolerancesResponseDto =
        callWithBody(
            EnvelopeType.OPTIMIZE_TOLERANCES,
            request, OptimizeTolerancesRequestDto.serializer(),
            OptimizeTolerancesResponseDto.serializer(),
        )

    suspend fun listTemplates(): ListTemplatesResponseDto =
        callNoBody(EnvelopeType.LIST_TEMPLATES, ListTemplatesResponseDto.serializer())

    suspend fun instantiateTemplate(request: InstantiateTemplateRequestDto): InstantiateTemplateResponseDto =
        callWithBody(
            EnvelopeType.INSTANTIATE_TEMPLATE,
            request, InstantiateTemplateRequestDto.serializer(),
            InstantiateTemplateResponseDto.serializer(),
        )

    suspend fun getIso286Fit(basicSize: Double, designation: String): StandardTableResultDto =
        callWithBody(
            EnvelopeType.GET_STANDARD_TABLE,
            Iso286FitRequestDto(basicSize = basicSize, designation = designation), Iso286FitRequestDto.serializer(),
            StandardTableResultDto.serializer(),
        )

    suspend fun getIso2768Tolerance(nominalSize: Double, toleranceClass: String): StandardTableResultDto =
        callWithBody(
            EnvelopeType.GET_STANDARD_TABLE,
            Iso2768ToleranceRequestDto(nominalSize = nominalSize, toleranceClass = toleranceClass),
            Iso2768ToleranceRequestDto.serializer(),
            StandardTableResultDto.serializer(),
        )

    suspend fun generatePdfReport(request: GenerateReportRequestDto): GenerateReportResponseDto =
        callWithBody(
            EnvelopeType.GENERATE_PDF_REPORT,
            request, GenerateReportRequestDto.serializer(),
            GenerateReportResponseDto.serializer(),
        )

    suspend fun generateExcelReport(request: GenerateReportRequestDto): GenerateReportResponseDto =
        callWithBody(
            EnvelopeType.GENERATE_EXCEL_REPORT,
            request, GenerateReportRequestDto.serializer(),
            GenerateReportResponseDto.serializer(),
        )

    private suspend fun <Req, Res> callWithBody(
        type: String,
        requestBody: Req,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>,
    ): Res {
        val payload = json.encodeToJsonElement(requestSerializer, requestBody) as JsonObject
        val response = send(type, payload)
        return decode(type, response, responseSerializer)
    }

    private suspend fun <Res> callNoBody(type: String, responseSerializer: KSerializer<Res>): Res {
        val response = send(type, buildJsonObject {})
        return decode(type, response, responseSerializer)
    }

    private fun <Res> decode(type: String, response: Envelope, responseSerializer: KSerializer<Res>): Res {
        check(response.success == true) {
            "$type failed: ${response.error?.code} - ${response.error?.message}"
        }
        return json.decodeFromJsonElement(responseSerializer, response.payload)
    }

    private suspend fun send(type: String, payload: JsonObject): Envelope =
        transport.request(Envelope(requestId = UUID.randomUUID().toString(), type = type, payload = payload))
}
