package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.DockKind
import com.networkmarketing.planner.domain.canvas.ElbowPath
import com.networkmarketing.planner.domain.canvas.LosGraph
import com.networkmarketing.planner.domain.canvas.NodePort
import com.networkmarketing.planner.domain.canvas.WorldPoint
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind
import kotlin.math.hypot
import kotlin.math.roundToInt

data class PendingConnection(
    val from: NodePort,
    val point: Offset,
)

@Composable
fun OrgCanvas(
    snapshot: OrgSnapshot,
    kind: StructureKind,
    payouts: Map<String, PayoutBreakdown>,
    selectedId: String?,
    viewport: CanvasViewport,
    onViewportChange: (CanvasViewport) -> Unit,
    onSelect: (String?) -> Unit,
    onMoveEnd: (OrgNode, Float, Float) -> Unit,
    onApplyConnection: (LosGraph.ConnectionEdit) -> Unit,
    onCreateDownline: (parent: OrgNode, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    onViewSize: (IntSize) -> Unit = {},
) {
    val density = LocalDensity.current
    val nodes = snapshot.nodes(kind)
    val localPos = remember { mutableStateMapOf<String, Offset>() }
    var connecting by remember { mutableStateOf<PendingConnection?>(null) }
    val grid = canvasGridColors()

    fun posOf(node: OrgNode): Offset = localPos[node.id] ?: Offset(node.canvasX, node.canvasY)

    fun heightOf(node: OrgNode): Float = CanvasMetrics.nodeHeight(snapshot.isCouple(node))

    fun portWorld(port: NodePort): Offset? {
        val node = snapshot.node(port.nodeId) ?: return null
        val p = posOf(node)
        val h = heightOf(node)
        return if (port.kind == DockKind.UPLINE) {
            val d = CanvasMetrics.uplineDock(p.x, p.y)
            Offset(d.first, d.second)
        } else {
            val count = LosGraph.downlinePortCount(snapshot, node.id)
            val d = CanvasMetrics.downlineDock(p.x, p.y, h, port.index, count)
            Offset(d.first, d.second)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(grid.background)
            .onSizeChanged { onViewSize(it) },
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
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(viewport) {
                        detectTransformGestures { centroid, pan, zoomChange, _ ->
                            val screen = Offset(
                                centroid.x * viewport.zoom + viewport.panX,
                                centroid.y * viewport.zoom + viewport.panY,
                            )
                            onViewportChange(
                                viewport.zoomBy(zoomChange, screen)
                                    .panBy(pan.x * viewport.zoom, pan.y * viewport.zoom),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onSelect(null) })
                    },
            ) {
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
                nodes.forEach { child ->
                    val parentId = child.parentId ?: return@forEach
                    val parent = snapshot.node(parentId) ?: return@forEach
                    if (parent.kind != kind) return@forEach
                    val siblings = snapshot.children(parentId)
                    val index = siblings.indexOfFirst { it.id == child.id }.coerceAtLeast(0)
                    val from = portWorld(NodePort(parentId, DockKind.DOWNLINE, index)) ?: return@forEach
                    val to = portWorld(NodePort(child.id, DockKind.UPLINE, 0)) ?: return@forEach
                    drawPath(
                        elbowPath(from, to, density.density),
                        grid.edge,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                connecting?.let { link ->
                    val from = portWorld(link.from) ?: return@let
                    drawPath(
                        elbowPath(from, link.point, density.density),
                        grid.activeEdge,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            nodes.forEach { node ->
                val p = posOf(node)
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            (p.x * density.density).roundToInt(),
                            (p.y * density.density).roundToInt(),
                        )
                    },
                ) {
                    IboNodeCard(
                        node = node,
                        member = snapshot.member(node.memberId),
                        payout = payouts[node.id],
                        selected = node.id == selectedId,
                        downlinePorts = LosGraph.downlinePortCount(snapshot, node.id),
                        onSelect = { onSelect(node.id) },
                        onMove = { amountPx ->
                            val current = posOf(node)
                            localPos[node.id] = Offset(
                                (current.x + amountPx.x / density.density)
                                    .coerceIn(0f, CanvasMetrics.WORLD_WIDTH - CanvasMetrics.NODE_WIDTH),
                                (current.y + amountPx.y / density.density)
                                    .coerceIn(0f, CanvasMetrics.WORLD_HEIGHT - 180f),
                            )
                        },
                        onMoveEnd = {
                            val current = posOf(node)
                            val snapped = CanvasMetrics.snapPoint(current.x, current.y)
                            localPos[node.id] = Offset(snapped.first, snapped.second)
                            onMoveEnd(node, snapped.first, snapped.second)
                        },
                        onDockDragStart = { port ->
                            connecting = PendingConnection(port, portWorld(port) ?: Offset.Zero)
                        },
                        onDockDrag = { port, deltaWorld ->
                            val link = connecting ?: return@IboNodeCard
                            if (link.from != port) return@IboNodeCard
                            connecting = link.copy(point = link.point + deltaWorld)
                        },
                        onDockDragEnd = { port ->
                            val link = connecting
                            connecting = null
                            if (link == null) return@IboNodeCard
                            val target = hitPort(snapshot, nodes, localPos, link.point, excludeId = node.id)
                            if (target != null) {
                                LosGraph.resolveConnection(snapshot, port, target)?.let(onApplyConnection)
                                return@IboNodeCard
                            }
                            val vacantDownline = port.kind == DockKind.DOWNLINE &&
                                LosGraph.downlineChild(snapshot, port.nodeId, port.index) == null
                            if (vacantDownline) {
                                onCreateDownline(
                                    node,
                                    link.point.x - CanvasMetrics.NODE_WIDTH / 2f,
                                    link.point.y,
                                )
                            } else {
                                LosGraph.resolveDropOnEmpty(snapshot, port)?.let(onApplyConnection)
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun hitPort(
    snapshot: OrgSnapshot,
    nodes: List<OrgNode>,
    localPos: Map<String, Offset>,
    world: Offset,
    excludeId: String,
): NodePort? {
    var best: NodePort? = null
    var bestDist = CanvasMetrics.DOCK_HIT
    nodes.forEach { node ->
        if (node.id == excludeId) return@forEach
        val p = localPos[node.id] ?: Offset(node.canvasX, node.canvasY)
        val h = CanvasMetrics.nodeHeight(snapshot.isCouple(node))
        val top = CanvasMetrics.uplineDock(p.x, p.y)
        val dTop = hypot(world.x - top.first, world.y - top.second)
        if (dTop < bestDist) {
            bestDist = dTop
            best = NodePort(node.id, DockKind.UPLINE, 0)
        }
        val count = LosGraph.downlinePortCount(snapshot, node.id)
        repeat(count) { index ->
            val dock = CanvasMetrics.downlineDock(p.x, p.y, h, index, count)
            val d = hypot(world.x - dock.first, world.y - dock.second)
            if (d < bestDist) {
                bestDist = d
                best = NodePort(node.id, DockKind.DOWNLINE, index)
            }
        }
    }
    return best
}

private fun elbowPath(fromDp: Offset, toDp: Offset, density: Float): Path {
    val pts = ElbowPath.midX(
        WorldPoint(fromDp.x * density, fromDp.y * density),
        WorldPoint(toDp.x * density, toDp.y * density),
    )
    return Path().apply {
        moveTo(pts.first().x, pts.first().y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
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
        background = scheme.background,
        dot = scheme.outline.copy(alpha = 0.35f),
        edge = scheme.primary.copy(alpha = 0.72f),
        activeEdge = scheme.secondary,
    )
}
