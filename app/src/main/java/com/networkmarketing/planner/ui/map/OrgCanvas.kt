package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.LosGraph
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OrgCanvas(
    snapshot: OrgSnapshot,
    kind: StructureKind,
    payouts: Map<String, PayoutBreakdown>,
    selectedIds: Set<String>,
    viewport: CanvasViewport,
    onViewportChange: (CanvasViewport) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onMoveEnd: (OrgNode, Float, Float) -> Unit,
    onApplyConnection: (LosGraph.ConnectionEdit) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onCreateDownline: (parent: OrgNode, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    onViewSize: (IntSize) -> Unit = {},
) {
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val nodes = snapshot.nodes(kind)
    val localPos = remember { mutableStateMapOf<String, Offset>() }
    var marqueeScreen by remember { mutableStateOf<Rect?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoverTargetId by remember { mutableStateOf<String?>(null) }
    var detachArmed by remember { mutableStateOf(false) }
    var emptyDwellStartedAt by remember { mutableLongStateOf(0L) }
    val grid = canvasGridColors()
    val marqueeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val marqueeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val edgeColor = Color(0xFF1E1E1E)

    fun posOf(node: OrgNode): Offset = localPos[node.id] ?: Offset(node.canvasX, node.canvasY)

    fun idsInScreenRect(rect: Rect, vp: CanvasViewport): Set<String> {
        val a = vp.screenToWorld(rect.left, rect.top, density.density)
        val b = vp.screenToWorld(rect.right, rect.bottom, density.density)
        val minX = min(a.x, b.x)
        val maxX = max(a.x, b.x)
        val minY = min(a.y, b.y)
        val maxY = max(a.y, b.y)
        val r = CanvasMetrics.NODE_RADIUS
        return nodes.filter { node ->
            val p = posOf(node)
            p.x + r > minX && p.x - r < maxX && p.y + r > minY && p.y - r < maxY
        }.map { it.id }.toSet()
    }

    fun hitNodeAt(world: Offset, excludeId: String?): OrgNode? {
        var best: OrgNode? = null
        var bestDist = CanvasMetrics.NODE_RADIUS
        nodes.forEach { node ->
            if (node.id == excludeId) return@forEach
            val p = posOf(node)
            val d = hypot((world.x - p.x).toDouble(), (world.y - p.y).toDouble()).toFloat()
            if (d <= bestDist) {
                bestDist = d
                best = node
            }
        }
        return best
    }

    val latestViewport = rememberUpdatedState(viewport)
    val latestOnViewportChange = rememberUpdatedState(onViewportChange)
    val latestOnSelectionChange = rememberUpdatedState(onSelectionChange)
    val latestIdsInScreenRect = rememberUpdatedState(::idsInScreenRect)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(grid.background)
            .onSizeChanged { onViewSize(it) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode: CanvasGesture? = null
                    var marqueeOrigin = Offset.Unspecified
                    var marqueeEnd = Offset.Unspecified

                    do {
                        val initial = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = initial.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2 || mode == CanvasGesture.Transform) {
                            mode = CanvasGesture.Transform
                            marqueeScreen = null
                            val zoomChange = initial.calculateZoom()
                            val pan = initial.calculatePan()
                            val centroid = initial.calculateCentroid(useCurrent = false)
                            if (centroid != Offset.Unspecified &&
                                (zoomChange != 1f || pan != Offset.Zero)
                            ) {
                                initial.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                                val vp = latestViewport.value
                                latestOnViewportChange.value(
                                    vp.zoomBy(zoomChange, centroid).panBy(pan.x, pan.y),
                                )
                            }
                            continue
                        }

                        val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                        val change = finalEvent.changes.firstOrNull { it.pressed }
                            ?: finalEvent.changes.firstOrNull()
                            ?: break
                        if (change.isConsumed && mode != CanvasGesture.Marquee) {
                            break
                        }

                        if (mode == null) {
                            if (marqueeOrigin == Offset.Unspecified) {
                                marqueeOrigin = change.previousPosition
                            }
                            val travel = change.position - marqueeOrigin
                            if (hypot(travel.x.toDouble(), travel.y.toDouble()) >= touchSlop) {
                                mode = CanvasGesture.Marquee
                            }
                        }

                        if (mode == CanvasGesture.Marquee) {
                            marqueeEnd = change.position
                            val rect = Rect(
                                min(marqueeOrigin.x, marqueeEnd.x),
                                min(marqueeOrigin.y, marqueeEnd.y),
                                max(marqueeOrigin.x, marqueeEnd.x),
                                max(marqueeOrigin.y, marqueeEnd.y),
                            )
                            marqueeScreen = rect
                            latestOnSelectionChange.value(
                                latestIdsInScreenRect.value(rect, latestViewport.value),
                            )
                            if (change.positionChanged()) change.consume()
                        }
                    } while (true)

                    if (mode == CanvasGesture.Marquee &&
                        marqueeOrigin != Offset.Unspecified &&
                        marqueeEnd != Offset.Unspecified
                    ) {
                        val rect = Rect(
                            min(marqueeOrigin.x, marqueeEnd.x),
                            min(marqueeOrigin.y, marqueeEnd.y),
                            max(marqueeOrigin.x, marqueeEnd.x),
                            max(marqueeOrigin.y, marqueeEnd.y),
                        )
                        latestOnSelectionChange.value(
                            latestIdsInScreenRect.value(rect, latestViewport.value),
                        )
                    }
                    marqueeScreen = null
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestOnSelectionChange.value(emptySet()) })
            },
    ) {
        Box(
            modifier = Modifier
                .size(CanvasMetrics.WORLD_WIDTH.dp, CanvasMetrics.WORLD_HEIGHT.dp)
                .graphicsLayer {
                    translationX = viewport.panX
                    translationY = viewport.panY
                    scaleX = viewport.zoom
                    scaleY = viewport.zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val step = CanvasMetrics.GRID.dp.toPx()
                var gx = 0f
                while (gx <= size.width) {
                    var gy = 0f
                    while (gy <= size.height) {
                        drawCircle(grid.dot, radius = 1.5f, center = Offset(gx, gy))
                        gy += step
                    }
                    gx += step
                }
                val dens = density.density
                nodes.forEach { child ->
                    val parentId = child.parentId ?: return@forEach
                    val parent = snapshot.node(parentId) ?: return@forEach
                    if (parent.kind != kind) return@forEach
                    val fromC = posOf(parent)
                    val toC = posOf(child)
                    val fromRim = CanvasMetrics.rimPoint(fromC.x, fromC.y, toC.x, toC.y)
                    val toRim = CanvasMetrics.rimPoint(toC.x, toC.y, fromC.x, fromC.y)
                    val x1 = fromRim.first * dens
                    val y1 = fromRim.second * dens
                    val x2 = toRim.first * dens
                    val y2 = toRim.second * dens
                    drawLine(
                        color = edgeColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round,
                    )
                    drawArrowHead(Offset(x1, y1), Offset(x2, y2), edgeColor, 12f)
                }
            }
            nodes.forEach { node ->
                val p = posOf(node)
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            ((p.x - CanvasMetrics.NODE_RADIUS) * density.density).roundToInt(),
                            ((p.y - CanvasMetrics.NODE_RADIUS) * density.density).roundToInt(),
                        )
                    },
                ) {
                    IboNodeCard(
                        node = node,
                        member = snapshot.member(node.memberId),
                        payout = payouts[node.id],
                        selected = node.id in selectedIds,
                        dropTarget = node.id == hoverTargetId,
                        onSelect = { onSelectionChange(setOf(node.id)) },
                        onMove = { amountPx ->
                            draggingId = node.id
                            val current = posOf(node)
                            val next = Offset(
                                (current.x + amountPx.x / density.density)
                                    .coerceIn(CanvasMetrics.NODE_RADIUS, CanvasMetrics.WORLD_WIDTH - CanvasMetrics.NODE_RADIUS),
                                (current.y + amountPx.y / density.density)
                                    .coerceIn(CanvasMetrics.NODE_RADIUS, CanvasMetrics.WORLD_HEIGHT - CanvasMetrics.NODE_RADIUS),
                            )
                            localPos[node.id] = next
                            val hit = hitNodeAt(next, excludeId = node.id)
                            hoverTargetId = hit?.id
                            if (hit == null && node.parentId != null) {
                                val now = System.currentTimeMillis()
                                if (emptyDwellStartedAt == 0L) {
                                    emptyDwellStartedAt = now
                                    detachArmed = false
                                } else if (now - emptyDwellStartedAt >= CanvasMetrics.DETACH_DWELL_MS) {
                                    detachArmed = true
                                }
                            } else {
                                emptyDwellStartedAt = 0L
                                detachArmed = false
                            }
                        },
                        onMoveEnd = {
                            val current = posOf(node)
                            val snapped = CanvasMetrics.snapPoint(current.x, current.y)
                            localPos[node.id] = Offset(snapped.first, snapped.second)
                            val target = hoverTargetId?.let { snapshot.node(it) }
                            val edit = when {
                                target != null ->
                                    LosGraph.resolveReparent(snapshot, node.id, target.id)
                                detachArmed && node.parentId != null ->
                                    LosGraph.resolveDetach(snapshot, node.id)
                                else -> null
                            }
                            hoverTargetId = null
                            detachArmed = false
                            emptyDwellStartedAt = 0L
                            draggingId = null
                            onMoveEnd(node, snapped.first, snapped.second)
                            edit?.let(onApplyConnection)
                        },
                    )
                }
            }
        }

        if (detachArmed && draggingId != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Release to detach from upline",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        marqueeScreen?.let { rect ->
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = marqueeFill,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                )
                drawRect(
                    color = marqueeStroke,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    color: Color,
    size: Float,
) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(
            (to.x - size * cos(angle - Math.PI / 6)).toFloat(),
            (to.y - size * sin(angle - Math.PI / 6)).toFloat(),
        )
        lineTo(
            (to.x - size * cos(angle + Math.PI / 6)).toFloat(),
            (to.y - size * sin(angle + Math.PI / 6)).toFloat(),
        )
        close()
    }
    drawPath(path, color)
}

private enum class CanvasGesture {
    Transform,
    Marquee,
}

private data class GridColors(
    val background: Color,
    val dot: Color,
    val edge: Color,
    val activeEdge: Color,
)

@Composable
private fun canvasGridColors(): GridColors {
    val scheme = MaterialTheme.colorScheme
    return GridColors(
        background = scheme.surfaceContainerLowest,
        dot = scheme.outline.copy(alpha = 0.35f),
        edge = scheme.outline.copy(alpha = 0.55f),
        activeEdge = scheme.primary,
    )
}
