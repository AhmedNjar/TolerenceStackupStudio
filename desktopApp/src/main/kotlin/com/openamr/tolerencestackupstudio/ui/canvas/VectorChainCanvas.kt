package com.openamr.tolerencestackupstudio.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentDto
import com.openamr.tolerencestackupstudio.engine.protocol.dto.ComponentKind
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renders the component chain as a 2D vector loop: LINEAR components are
 * drawn as arrows advancing the running cursor along the current heading;
 * ANGULAR components don't draw a length — they rotate the heading for
 * whatever comes after them (the standard convention for a 2D vector-loop
 * stack-up diagram, e.g. the law-of-cosines triangular-loop template).
 * Tapping a segment or angle joint selects the matching data-grid row.
 */
@Composable
fun VectorChainCanvas(
    components: List<ComponentDto>,
    selectedComponentId: String?,
    onSelect: (String?) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val layout = remember(components, widthPx, heightPx) {
            computeLayout(components, widthPx, heightPx)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { tapOffset ->
                        val hit = layout.elements.firstOrNull { it.hitTest(tapOffset) }
                        onSelect(hit?.componentId)
                    }
                }
        ) {
            // Faint baseline through the chain's start point, purely a visual
            // anchor so the loop doesn't float with no reference.
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, heightPx / 2f),
                end = Offset(widthPx, heightPx / 2f),
                strokeWidth = 1f,
            )

            layout.elements.forEach { element ->
                val selected = element.componentId == selectedComponentId
                val color = if (selected) Color(0xFF1976D2) else Color(0xFF546E7A)
                val strokeWidth = if (selected) 5f else 3f

                when (element) {
                    is CanvasElement.Segment -> {
                        drawLine(color, element.start, element.end, strokeWidth, cap = StrokeCap.Round)
                        drawArrowHead(element.start, element.end, color)
                        
                        val textStyle = TextStyle(fontSize = 11.sp, color = color)
                        val textLayout = textMeasurer.measure(element.label, textStyle)
                        drawText(
                            textMeasurer, element.label,
                            topLeft = Offset(
                                element.labelPosition.x - textLayout.size.width / 2f,
                                element.labelPosition.y - textLayout.size.height / 2f
                            ),
                            style = textStyle,
                        )
                    }
                    is CanvasElement.AngleJoint -> {
                        drawCircle(color, radius = if (selected) 8f else 6f, center = element.center)
                        
                        val textStyle = TextStyle(fontSize = 11.sp, color = color)
                        val textLayout = textMeasurer.measure(element.label, textStyle)
                        drawText(
                            textMeasurer, element.label,
                            topLeft = Offset(
                                element.labelPosition.x, // keep joint labels slightly offset to the right
                                element.labelPosition.y - textLayout.size.height / 2f
                            ),
                            style = textStyle,
                        )
                    }
                }
            }
        }
    }
}

// ---- Layout computation ---------------------------------------------------

private sealed class CanvasElement {
    abstract val componentId: String
    abstract fun hitTest(point: Offset): Boolean

    data class Segment(
        override val componentId: String,
        val start: Offset,
        val end: Offset,
        val label: String,
        val labelPosition: Offset,
    ) : CanvasElement() {
        override fun hitTest(point: Offset): Boolean = distanceToSegment(point, start, end) <= HIT_TOLERANCE_PX
    }

    data class AngleJoint(
        override val componentId: String,
        val center: Offset,
        val label: String,
        val labelPosition: Offset,
    ) : CanvasElement() {
        override fun hitTest(point: Offset): Boolean = (point - center).getDistance() <= JOINT_HIT_RADIUS_PX
    }
}

private data class ChainLayout(val elements: List<CanvasElement>)

private const val HIT_TOLERANCE_PX = 12f
private const val JOINT_HIT_RADIUS_PX = 14f

private fun computeLayout(components: List<ComponentDto>, widthPx: Float, heightPx: Float): ChainLayout {
    if (components.isEmpty() || widthPx <= 0f || heightPx <= 0f) return ChainLayout(emptyList())

    // Pass 1: walk the chain at scale=1 (raw component units) to find the
    // bounding box, so pass 2 can pick a scale that fits the canvas.
    val rawPoints = walkChain(components, scale = 1f, origin = Offset.Zero)
    val minX = rawPoints.minOf { it.x }
    val maxX = rawPoints.maxOf { it.x }
    val minY = rawPoints.minOf { it.y }
    val maxY = rawPoints.maxOf { it.y }
    val rawWidth = max(maxX - minX, 0.001f)
    val rawHeight = max(maxY - minY, 0.001f)

    val marginPx = 80f
    val scale = minOf(
        (widthPx - 2 * marginPx) / rawWidth,
        (heightPx - 2 * marginPx) / rawHeight,
    ).coerceAtLeast(0.01f)

    // Pass 2: re-walk at the chosen scale, then translate so the chain is
    // centered in the canvas.
    val rawCenterX = (minX + maxX) / 2f
    val rawCenterY = (minY + maxY) / 2f
    val origin = Offset(widthPx / 2f - rawCenterX * scale, heightPx / 2f - rawCenterY * scale)

    return ChainLayout(buildElements(components, scale, origin))
}

/** Returns every vertex the chain visits, at the given scale — used both to
 * measure the bounding box (pass 1) and, via buildElements, to place labels
 * (pass 2). Kept separate from buildElements so pass 1 doesn't pay for
 * string formatting it doesn't need. */
private fun walkChain(components: List<ComponentDto>, scale: Float, origin: Offset): List<Offset> {
    val points = mutableListOf(origin)
    var cursor = origin
    var headingRad = 0.0

    for (c in components) {
        if (c.kind == ComponentKind.ANGULAR) {
            headingRad += Math.toRadians(c.nominal)
            points.add(cursor) // joint doesn't move the cursor
        } else {
            val length = c.nominal.toFloat() * scale
            val next = Offset(
                cursor.x + (length * cos(headingRad)).toFloat(),
                cursor.y - (length * sin(headingRad)).toFloat(), // screen y grows downward
            )
            points.add(next)
            cursor = next
        }
    }
    return points
}

private fun buildElements(components: List<ComponentDto>, scale: Float, origin: Offset): List<CanvasElement> {
    val elements = mutableListOf<CanvasElement>()
    var cursor = origin
    var headingRad = 0.0

    for (c in components) {
        if (c.kind == ComponentKind.ANGULAR) {
            val label = "${c.label}: ${formatDeg(c.nominal)}"
            elements.add(
                CanvasElement.AngleJoint(
                    componentId = c.id,
                    center = cursor,
                    label = label,
                    labelPosition = Offset(cursor.x + 12f, cursor.y + 12f),
                )
            )
            headingRad += Math.toRadians(c.nominal)
        } else {
            val length = c.nominal.toFloat() * scale
            val next = Offset(
                cursor.x + (length * cos(headingRad)).toFloat(),
                cursor.y - (length * sin(headingRad)).toFloat(),
            )
            val mid = Offset((cursor.x + next.x) / 2f, (cursor.y + next.y) / 2f)
            // Offset the label perpendicular to the segment so it doesn't sit
            // on top of the line itself.
            val perp = Offset(-sin(headingRad).toFloat(), -cos(headingRad).toFloat())
            val labelPos = Offset(mid.x + perp.x * 24f, mid.y + perp.y * 24f)

            elements.add(
                CanvasElement.Segment(
                    componentId = c.id,
                    start = cursor,
                    end = next,
                    label = "${c.label}: ${formatMm(c.nominal)} +${formatMm(c.upperTol)}/-${formatMm(c.lowerTol)}",
                    labelPosition = labelPos,
                )
            )
            cursor = next
        }
    }
    return elements
}

private fun formatMm(v: Double): String = "%.3f".format(v)
private fun formatDeg(v: Double): String = "%.2f\u00B0".format(v)

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lengthSq = ab.x * ab.x + ab.y * ab.y
    if (lengthSq == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lengthSq).coerceIn(0f, 1f)
    val projection = Offset(a.x + ab.x * t, a.y + ab.y * t)
    return (p - projection).getDistance()
}

private fun DrawScope.drawArrowHead(start: Offset, end: Offset, color: Color) {
    val direction = end - start
    val length = direction.getDistance()
    if (length < 1f) return
    val unit = Offset(direction.x / length, direction.y / length)
    val headSize = 10f
    val left = Offset(-unit.y, unit.x)

    val p1 = end
    val p2 = Offset(end.x - unit.x * headSize + left.x * headSize * 0.5f, end.y - unit.y * headSize + left.y * headSize * 0.5f)
    val p3 = Offset(end.x - unit.x * headSize - left.x * headSize * 0.5f, end.y - unit.y * headSize - left.y * headSize * 0.5f)

    drawLine(color, p1, p2, strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color, p1, p3, strokeWidth = 3f, cap = StrokeCap.Round)
}
