package com.openamr.tolerencestackupstudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentDto

/**
 * Shows a closing equation's expression as a row of clickable tokens.
 */
@Composable
fun EquationDisplay(
    expression: String,
    components: List<ComponentDto>,
    selectedComponentId: String?,
    onSelectComponent: (String?) -> Unit,
) {
    val tokens = remember(expression, components) { tokenize(expression, components) }

    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        tokens.forEach { token ->
            if (token.componentId == null) {
                Text(
                    text = token.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val isSelected = token.componentId == selectedComponentId
                Text(
                    text = token.text,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        onSelectComponent(if (isSelected) null else token.componentId)
                    },
                )
            }
        }
    }
}

private data class Token(val text: String, val componentId: String?)

private fun tokenize(expression: String, components: List<ComponentDto>): List<Token> {
    if (components.isEmpty() || expression.isBlank()) {
        return listOf(Token(expression, null))
    }

    val byLabel = components.associateBy { it.label }
    val labelsByLengthDesc = byLabel.keys.sortedByDescending { it.length }
    val pattern = Regex("\\b(${labelsByLengthDesc.joinToString("|") { Regex.escape(it) }})\\b")

    val tokens = mutableListOf<Token>()
    var cursor = 0
    for (match in pattern.findAll(expression)) {
        if (match.range.first > cursor) {
            tokens.add(Token(expression.substring(cursor, match.range.first), null))
        }
        tokens.add(Token(match.value, byLabel[match.value]?.id))
        cursor = match.range.last + 1
    }
    if (cursor < expression.length) {
        tokens.add(Token(expression.substring(cursor), null))
    }
    return tokens
}
