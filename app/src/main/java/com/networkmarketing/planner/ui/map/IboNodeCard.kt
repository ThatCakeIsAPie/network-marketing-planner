package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

/**
 * tldraw / Obsidian-style circular org node: name · PV · estimated $.
 */
@Composable
fun IboNodeCard(
    node: OrgNode,
    member: Member?,
    payout: PayoutBreakdown?,
    selected: Boolean,
    dropTarget: Boolean,
    onSelect: () -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val maxLeg = payout != null && payout.performancePercent >= 0.249
    val diameter = CanvasMetrics.NODE_DIAMETER.dp
    val name = member?.displayName() ?: "Unknown"
    val pvLabel = "${qty(node.personalPv)}PV"
    val moneyLabel = payout?.estimatedMonthly?.let { money(it) } ?: "—"

    Box(
        modifier = Modifier
            .size(diameter)
            .semantics { contentDescription = "IBO $name" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
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
            shape = CircleShape,
            color = Color.White,
            shadowElevation = if (selected || dropTarget) 6.dp else 2.dp,
            border = BorderStroke(
                width = when {
                    dropTarget -> 3.dp
                    selected -> 2.5.dp
                    else -> 1.5.dp
                },
                color = when {
                    dropTarget -> scheme.primary
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
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    ),
                    color = Color(0xFF1E1E1E),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = pvLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = Color(0xFF1E1E1E),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = moneyLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color(0xFF1E1E1E),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
