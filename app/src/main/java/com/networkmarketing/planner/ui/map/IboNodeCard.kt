package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.DockKind
import com.networkmarketing.planner.domain.canvas.NodePort
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.ui.components.percent
import com.networkmarketing.planner.ui.components.qty

@Composable
fun IboNodeCard(
    node: OrgNode,
    member: Member?,
    payout: PayoutBreakdown?,
    selected: Boolean,
    downlinePorts: Int,
    onSelect: () -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onDockDragStart: (NodePort) -> Unit,
    onDockDrag: (NodePort, Offset) -> Unit,
    onDockDragEnd: (NodePort) -> Unit,
) {
    val couple = member?.isCouple == true
    val height = CanvasMetrics.nodeHeight(couple)
    val scheme = MaterialTheme.colorScheme
    val maxLeg = payout != null && payout.performancePercent >= 0.249
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(CanvasMetrics.NODE_WIDTH.dp)
            .height(height.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height((height - CanvasMetrics.DOCK).dp)
                .align(Alignment.Center)
                .shadow(if (selected) 8.dp else 2.dp, RoundedCornerShape(14.dp))
                .pointerInput(node.id) { detectTapGestures(onTap = { onSelect() }) }
                .pointerInput(node.id) {
                    detectDragGestures(
                        onDragEnd = { onMoveEnd() },
                        onDragCancel = { onMoveEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onMove(amount)
                        },
                    )
                },
            shape = RoundedCornerShape(14.dp),
            color = when {
                selected -> scheme.secondaryContainer
                member?.isYou == true -> scheme.primaryContainer
                else -> scheme.surface
            },
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    maxLeg -> scheme.secondary
                    selected -> scheme.primary
                    else -> scheme.outline.copy(alpha = 0.4f)
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = member?.displayName() ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (maxLeg) {
                        Text(
                            "25%",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.secondary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (couple) {
                    Text("Couple", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
                Text(
                    "${qty(node.personalPv)} PV · ${qty(node.personalBv)} BV",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val status = if (payout != null) {
                    "${percent(payout.performancePercent)} · G ${qty(payout.group.pv)} PV"
                } else {
                    "${node.personalPv.toInt()} personal"
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        DockHandle(
            port = NodePort(node.id, DockKind.UPLINE, 0),
            enabled = member?.isYou != true,
            occupied = node.parentId != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ((CanvasMetrics.uplineDock(0f, 0f).second - CanvasMetrics.DOCK_HIT / 2f)).dp),
            onDragStart = onDockDragStart,
            onDrag = onDockDrag,
            onDragEnd = onDockDragEnd,
        )
        val ports = downlinePorts.coerceAtLeast(1)
        repeat(ports) { index ->
            val dock = CanvasMetrics.downlineDock(0f, 0f, height, index, ports)
            val occupied = index < ports - 1
            DockHandle(
                port = NodePort(node.id, DockKind.DOWNLINE, index),
                enabled = true,
                occupied = occupied,
                modifier = Modifier.offset(
                    x = with(density) { (dock.first - CanvasMetrics.DOCK_HIT / 2f).dp },
                    y = with(density) { (dock.second - CanvasMetrics.DOCK_HIT / 2f).dp },
                ),
                onDragStart = onDockDragStart,
                onDrag = onDockDrag,
                onDragEnd = onDockDragEnd,
            )
        }
    }
}

@Composable
private fun DockHandle(
    port: NodePort,
    enabled: Boolean,
    occupied: Boolean,
    modifier: Modifier,
    onDragStart: (NodePort) -> Unit,
    onDrag: (NodePort, Offset) -> Unit,
    onDragEnd: (NodePort) -> Unit,
) {
    val density = LocalDensity.current
    val color = if (port.kind == DockKind.UPLINE) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val label = if (port.kind == DockKind.UPLINE) "Upline dock" else "Downline dock ${port.index + 1}"
    Box(
        modifier = modifier
            .size(CanvasMetrics.DOCK_HIT.dp)
            .semantics { contentDescription = label }
            .pointerInput(port, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { onDragStart(port) },
                    onDragEnd = { onDragEnd(port) },
                    onDragCancel = { onDragEnd(port) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(port, Offset(amount.x / density.density, amount.y / density.density))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (occupied) CanvasMetrics.DOCK.dp else (CanvasMetrics.DOCK - 2f).dp)
                .clip(CircleShape)
                .background(if (enabled) color.copy(alpha = if (occupied) 1f else 0.55f) else color.copy(alpha = 0.3f))
                .border(2.dp, Color.White, CircleShape),
        )
    }
}
