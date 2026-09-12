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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
    planProfileId: String? = null,
    ghostNodes: List<OrgNode> = emptyList(),
    ghostPayouts: Map<String, PayoutBreakdown> = emptyMap(),
    onClaimGhost: ((currentNodeId: String, planNodeId: String) -> Unit)? = null,
    planTargets: Map<String, PlanTargetDisplay> = emptyMap(),
) {
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val nodes = snapshot.nodes(kind, planProfileId)
    val claimedPlanIds = snapshot.planClaims.values.toSet()
    val visibleGhosts = ghostNodes.filter { it.id !in claimedPlanIds }
    val localPos = remember { mutableStateMapOf<String, Offset>() }
    var marqueeScreen by remember { mutableStateOf<Rect?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoverTargetId by remember { mutableStateOf<String?>(null) }
    var dwellKind by remember { mutableStateOf(NodeDwellKind.None) }
    var dwellStartedAt by remember { mutableLongStateOf(0L) }
    var dwellProgress by remember { mutableFloatStateOf(0f) }
    var dwellCommitted by remember { mutableStateOf(false) }
    var dwellAnchor by remember { mutableStateOf(Offset.Unspecified) }
    val grid = canvasGridColors()
    val marqueeFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val marqueeStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val edgeColor = Color(0xFF1E1E1E)
    val ghostEdge = Color(0xFF5A5A5A).copy(alpha = 0.45f)

    fun posOf(node: OrgNode): Offset = localPos[node.id] ?: Offset(node.canvasX, node.canvasY)

    fun resetDwell() {
        dwellKind = NodeDwellKind.None
        dwellStartedAt = 0L
        dwellProgress = 0f
        dwellCommitted = false
        hoverTargetId = null
        dwellAnchor = Offset.Unspecified
    }

    fun beginDwell(kind: NodeDwellKind, targetId: String?, anchor: Offset = Offset.Unspecified) {
        val sameTarget = dwellKind == kind && hoverTargetId == targetId && dwellStartedAt > 0L
        if (sameTarget) {
            if (dwellAnchor != Offset.Unspecified && anchor != Offset.Unspecified) {
                val moved = hypot(
                    (anchor.x - dwellAnchor.x).toDouble(),
                    (anchor.y - dwellAnchor.y).toDouble(),
                )
                if (moved > CanvasMetrics.NODE_RADIUS * 0.75) {
                    dwellStartedAt = System.currentTimeMillis()
                    dwellProgress = 0f
                    dwellCommitted = false
                    dwellAnchor = anchor
                }
            }
            return
        }
        dwellKind = kind
        hoverTargetId = targetId
        dwellStartedAt = System.currentTimeMillis()
        dwellProgress = 0f
        dwellCommitted = false
        dwellAnchor = anchor
    }

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

    fun hitNodeAt(world: Offset, excludeId: String?, radius: Float = CanvasMetrics.ATTACH_HIT_RADIUS): OrgNode? {
        var best: OrgNode? = null
        var bestDist = radius
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

    fun hitGhostAt(world: Offset, radius: Float = CanvasMetrics.ATTACH_HIT_RADIUS): OrgNode? {
        var best: OrgNode? = null
        var bestDist = radius
        visibleGhosts.forEach { node ->
            val p = Offset(node.canvasX, node.canvasY)
            val d = hypot((world.x - p.x).toDouble(), (world.y - p.y).toDouble()).toFloat()
            if (d <= bestDist) {
                bestDist = d
                best = node
            }
        }
        return best
    }

    val latestSnapshot = rememberUpdatedState(snapshot)
    val latestOnApplyConnection = rememberUpdatedState(onApplyConnection)
    val latestOnClaimGhost = rememberUpdatedState(onClaimGhost)
    val latestDraggingId = rememberUpdatedState(draggingId)

    LaunchedEffect(draggingId, dwellKind, dwellStartedAt) {
        if (draggingId == null || dwellKind == NodeDwellKind.None || dwellStartedAt == 0L) {
            dwellProgress = 0f
            return@LaunchedEffect
        }
        val duration = when (dwellKind) {
            NodeDwellKind.Attach, NodeDwellKind.Claim -> CanvasMetrics.ATTACH_DWELL_MS
            NodeDwellKind.Detach -> CanvasMetrics.DETACH_DWELL_MS
            NodeDwellKind.None -> return@LaunchedEffect
        }
        while (true) {
            withFrameMillis { }
            val elapsed = System.currentTimeMillis() - dwellStartedAt
            val p = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            dwellProgress = p
            if (p >= 1f && !dwellCommitted) {
                dwellCommitted = true
                val childId = latestDraggingId.value ?: break
                val snap = latestSnapshot.value
                when (dwellKind) {
                    NodeDwellKind.Attach ->
                        hoverTargetId?.let { LosGraph.resolveReparent(snap, childId, it) }
                            ?.let { latestOnApplyConnection.value(it) }
                    NodeDwellKind.Detach ->
                        LosGraph.resolveDetach(snap, childId)
                            ?.let { latestOnApplyConnection.value(it) }
                    NodeDwellKind.Claim ->
                        hoverTargetId?.let { planId ->
                            latestOnClaimGhost.value?.invoke(childId, planId)
                        }
                    NodeDwellKind.None -> Unit
                }
            }
            if (p >= 1f) break
        }
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
                val majorEvery = 5
                var i = 0
                var gx = 0f
                while (gx <= size.width) {
                    val major = i % majorEvery == 0
                    drawLine(
                        color = if (major) grid.lineMajor else grid.lineMinor,
                        start = Offset(gx, 0f),
                        end = Offset(gx, size.height),
                        strokeWidth = if (major) 1.25f else 0.75f,
                    )
                    gx += step
                    i++
                }
                i = 0
                var gy = 0f
                while (gy <= size.height) {
                    val major = i % majorEvery == 0
                    drawLine(
                        color = if (major) grid.lineMajor else grid.lineMinor,
                        start = Offset(0f, gy),
                        end = Offset(size.width, gy),
                        strokeWidth = if (major) 1.25f else 0.75f,
                    )
                    gy += step
                    i++
                }
                val dens = density.density
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                visibleGhosts.forEach { child ->
                    val parentId = child.parentId ?: return@forEach
                    val parent = visibleGhosts.firstOrNull { it.id == parentId }
                        ?: snapshot.node(parentId)
                        ?: return@forEach
                    val fromC = Offset(parent.canvasX, parent.canvasY)
                    val toC = Offset(child.canvasX, child.canvasY)
                    val fromRim = CanvasMetrics.rimPoint(fromC.x, fromC.y, toC.x, toC.y)
                    val toRim = CanvasMetrics.rimPoint(toC.x, toC.y, fromC.x, fromC.y)
                    drawLine(
                        color = ghostEdge,
                        start = Offset(fromRim.first * dens, fromRim.second * dens),
                        end = Offset(toRim.first * dens, toRim.second * dens),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                        pathEffect = dash,
                    )
                }
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

            visibleGhosts.forEach { node ->
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            ((node.canvasX - CanvasMetrics.NODE_RADIUS) * density.density).roundToInt(),
                            ((node.canvasY - CanvasMetrics.NODE_RADIUS) * density.density).roundToInt(),
                        )
                    },
                ) {
                    IboNodeCard(
                        node = node,
                        member = snapshot.member(node.memberId),
                        payout = ghostPayouts[node.id],
                        selected = false,
                        dropTarget = node.id == hoverTargetId && dwellKind == NodeDwellKind.Claim,
                        dwellProgress = if (dwellKind == NodeDwellKind.Claim && node.id == hoverTargetId) {
                            dwellProgress
                        } else {
                            0f
                        },
                        dwellKind = if (dwellKind == NodeDwellKind.Claim && node.id == hoverTargetId) {
                            NodeDwellKind.Claim
                        } else {
                            NodeDwellKind.None
                        },
                        onSelect = {},
                        onMove = {},
                        onMoveEnd = {},
                        ghost = true,
                    )
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
                        dropTarget = node.id == hoverTargetId &&
                            (dwellKind == NodeDwellKind.Attach || dwellKind == NodeDwellKind.Detach),
                        dwellProgress = when {
                            dwellKind == NodeDwellKind.Attach && node.id == hoverTargetId -> dwellProgress
                            dwellKind == NodeDwellKind.Detach && node.id == hoverTargetId -> dwellProgress
                            dwellKind == NodeDwellKind.Claim && node.id == draggingId && dwellCommitted -> 1f
                            dwellKind == NodeDwellKind.Attach && node.id == draggingId && dwellCommitted -> 1f
                            else -> 0f
                        },
                        dwellKind = when {
                            dwellKind == NodeDwellKind.Attach && node.id == hoverTargetId -> NodeDwellKind.Attach
                            dwellKind == NodeDwellKind.Detach && node.id == hoverTargetId -> NodeDwellKind.Detach
                            dwellKind == NodeDwellKind.Claim && node.id == draggingId && dwellCommitted ->
                                NodeDwellKind.Claim
                            dwellKind == NodeDwellKind.Attach && node.id == draggingId && dwellCommitted ->
                                NodeDwellKind.Attach
                            else -> NodeDwellKind.None
                        },
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
                            val ghostHit = if (onClaimGhost != null && hit == null) hitGhostAt(next) else null
                            when {
                                hit != null && hit.id == node.parentId ->
                                    beginDwell(NodeDwellKind.Detach, hit.id, next)
                                hit != null ->
                                    beginDwell(NodeDwellKind.Attach, hit.id, next)
                                ghostHit != null ->
                                    beginDwell(NodeDwellKind.Claim, ghostHit.id, next)
                                else -> resetDwell()
                            }
                        },
                        onMoveEnd = {
                            val current = posOf(node)
                            val snapped = CanvasMetrics.snapPoint(current.x, current.y)
                            localPos[node.id] = Offset(snapped.first, snapped.second)
                            resetDwell()
                            draggingId = null
                            onMoveEnd(node, snapped.first, snapped.second)
                        },
                        planTarget = planTargets[node.id],
                    )
                }
            }
        }

        when {
            draggingId != null && dwellKind == NodeDwellKind.Claim && !dwellCommitted -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        "Hold to claim plan slot…",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            draggingId != null && dwellKind == NodeDwellKind.Attach && !dwellCommitted -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Hold to link as downline…",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            draggingId != null && dwellKind == NodeDwellKind.Detach && !dwellCommitted -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "Hold over upline to detach…",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            draggingId != null && dwellCommitted && dwellKind == NodeDwellKind.Claim -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "Claimed — keep dragging to place",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            draggingId != null && dwellCommitted && dwellKind == NodeDwellKind.Attach -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "Linked — keep dragging to place",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            draggingId != null && dwellCommitted && dwellKind == NodeDwellKind.Detach -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "Detached — keep dragging to place",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
    val lineMinor: Color,
    val lineMajor: Color,
    val edge: Color,
    val activeEdge: Color,
)

@Composable
private fun canvasGridColors(): GridColors {
    val scheme = MaterialTheme.colorScheme
    return GridColors(
        background = scheme.surfaceContainerLowest,
        lineMinor = scheme.outline.copy(alpha = 0.14f),
        lineMajor = scheme.outline.copy(alpha = 0.28f),
        edge = scheme.outline.copy(alpha = 0.55f),
        activeEdge = scheme.primary,
    )
}
