package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.qty
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

enum class NodeDwellKind {
    None,
    Attach,
    Detach,
    Claim,
}

data class PlanTargetDisplay(
    val targetPv: Double,
    val currentPv: Double,
    val targetMoney: Double?,
    val currentMoney: Double?,
)

/**
 * tldraw / Obsidian-style circular org node: name · PV · estimated $.
 * Optional plan-target lines render below in red/green when claimed.
 */
@Composable
fun IboNodeCard(
    node: OrgNode,
    member: Member?,
    payout: PayoutBreakdown?,
    selected: Boolean,
    dropTarget: Boolean,
    dwellProgress: Float,
    dwellKind: NodeDwellKind,
    onSelect: () -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    ghost: Boolean = false,
    planTarget: PlanTargetDisplay? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val maxLeg = payout != null && payout.performancePercent >= 0.249
    val diameter = CanvasMetrics.NODE_DIAMETER.dp
    val name = member?.displayName() ?: "Unknown"
    val pvLabel = "${qty(node.personalPv)}PV"
    val moneyLabel = payout?.estimatedMonthly?.let { money(it) } ?: "—"
    val progress = dwellProgress.coerceIn(0f, 1f)
    val ringColor = when (dwellKind) {
        NodeDwellKind.Attach, NodeDwellKind.Claim -> scheme.primary
        NodeDwellKind.Detach -> scheme.error
        NodeDwellKind.None -> Color.Transparent
    }
    val ringTrack = when (dwellKind) {
        NodeDwellKind.Attach, NodeDwellKind.Claim -> scheme.primary.copy(alpha = 0.22f)
        NodeDwellKind.Detach -> scheme.error.copy(alpha = 0.22f)
        NodeDwellKind.None -> Color.Transparent
    }
    val metGreen = Color(0xFF1B7F3A)
    val missRed = Color(0xFFB3261E)

    Box(
        modifier = Modifier
            .size(diameter)
            .semantics { contentDescription = if (ghost) "Plan ghost $name" else "IBO $name" },
        contentAlignment = Alignment.Center,
    ) {
        if (dwellKind != NodeDwellKind.None && progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = ringColor,
                trackColor = ringTrack,
                strokeWidth = 5.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (dwellKind != NodeDwellKind.None) 5.dp else 0.dp)
                .then(
                    if (ghost) {
                        Modifier
                    } else {
                        Modifier
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
                            }
                    },
                ),
            shape = CircleShape,
            color = if (ghost) Color.White.copy(alpha = 0.35f) else Color.White,
            shadowElevation = when {
                ghost -> 0.dp
                selected || dropTarget || dwellKind != NodeDwellKind.None -> 6.dp
                else -> 2.dp
            },
            border = BorderStroke(
                width = when {
                    ghost -> 1.5.dp
                    dropTarget || dwellKind == NodeDwellKind.Attach || dwellKind == NodeDwellKind.Claim -> 2.5.dp
                    selected -> 2.5.dp
                    else -> 1.5.dp
                },
                color = when {
                    ghost -> Color(0xFF5A5A5A).copy(alpha = 0.55f)
                    dwellKind == NodeDwellKind.Detach -> scheme.error
                    dropTarget || dwellKind == NodeDwellKind.Attach || dwellKind == NodeDwellKind.Claim ->
                        scheme.primary
                    selected -> scheme.primary
                    maxLeg -> scheme.secondary
                    member?.isYou == true -> scheme.tertiary
                    else -> Color(0xFF1E1E1E)
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    ),
                    color = Color(0xFF1E1E1E).copy(alpha = if (ghost) 0.55f else 1f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pvLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = Color(0xFF1E1E1E).copy(alpha = if (ghost) 0.55f else 1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = moneyLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color(0xFF1E1E1E).copy(alpha = if (ghost) 0.55f else 1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (planTarget != null && !ghost) {
                    val pvColor = if (planTarget.currentPv + 1e-6 >= planTarget.targetPv) metGreen else missRed
                    Text(
                        text = "→ ${qty(planTarget.targetPv)}PV",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = pvColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    val targetMoney = planTarget.targetMoney
                    if (targetMoney != null) {
                        val cur = planTarget.currentMoney ?: 0.0
                        val moneyColor = if (cur + 1e-6 >= targetMoney) metGreen else missRed
                        Text(
                            text = "→ ${money(targetMoney)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = moneyColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
