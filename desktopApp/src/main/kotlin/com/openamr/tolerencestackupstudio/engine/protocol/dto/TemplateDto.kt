package com.openamr.tolerencestackupstudio.engine.protocol.dto

import kotlinx.serialization.Serializable

@Serializable
data class TemplateRoleDto(
    val roleId: String,
    val labelHint: String,
    val kind: ComponentKind,
    val description: String,
)

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val description: String,
    val roles: List<TemplateRoleDto>,
)

@Serializable
data class ListTemplatesResponseDto(val templates: List<TemplateDto>)

@Serializable
data class InstantiateTemplateRequestDto(
    val templateId: String,
    /** roleId -> the actual component label the user assigned to that role */
    val roleBindings: Map<String, String>,
)

@Serializable
data class InstantiateTemplateResponseDto(
    val templateId: String,
    val expression: String,
)
