package com.networkmarketing.planner.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.qty
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(CanvasViewport()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var zoomMenuOpen by remember { mutableStateOf(false) }
    var ghostMenuOpen by remember { mutableStateOf(false) }
    val nodes = state.snapshot.nodes(StructureKind.CURRENT)
    val selected = selectedIds.singleOrNull()?.let { state.snapshot.node(it) }
    val payout = state.currentPayout
    val peopleCount = nodes.sumOf { node ->
        val n: Int = if (state.snapshot.isCouple(node)) 2 else 1
        n
    }
    val zoomLabel = "${(viewport.zoom * 100f).roundToInt()}%"
    val statsStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        shadow = Shadow(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            blurRadius = 6f,
        ),
    )
    val statsColor = MaterialTheme.colorScheme.onSurface
    val ghostProfileId = state.mapGhostProfileId
    val ghostNodes = if (ghostProfileId != null) state.snapshot.planNodes(ghostProfileId) else emptyList()
    val ghostPayouts = remember(state.snapshot, ghostProfileId, state.settings) {
        viewModel.planGhostPayouts(state)
    }
    val planTargets = remember(state.snapshot, state.currentPayouts, ghostPayouts) {
        state.snapshot.planClaims.mapNotNull { (currentId, planId) ->
            val current = state.snapshot.node(currentId) ?: return@mapNotNull null
            val plan = state.snapshot.node(planId) ?: return@mapNotNull null
            currentId to PlanTargetDisplay(
                targetPv = plan.personalPv,
                currentPv = current.personalPv,
                targetMoney = ghostPayouts[planId]?.estimatedMonthly
                    ?: state.idealPayouts[planId]?.estimatedMonthly,
                currentMoney = state.currentPayouts[currentId]?.estimatedMonthly,
            )
        }.toMap()
    }
    val ghostLabel = ghostProfileId?.let { state.snapshot.planProfile(it)?.name } ?: "Ghost: none"

    LaunchedEffect(state.selectedNodeId) {
        val id = state.selectedNodeId ?: return@LaunchedEffect
        if (id !in selectedIds) selectedIds = setOf(id)
    }

    LaunchedEffect(viewSize, nodes.size) {
        if (!fitted && viewSize.width > 0 && nodes.isNotEmpty()) {
            viewport = CanvasViewport.fit(
                nodes = nodes,
                coupleOf = { state.snapshot.isCouple(it) },
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                density = density.density,
            )
            fitted = true
        }
    }

    fun pivot() = Offset(viewSize.width / 2f, viewSize.height / 2f)

    Box(modifier = Modifier.fillMaxSize()) {
        OrgCanvas(
            snapshot = state.snapshot,
            kind = StructureKind.CURRENT,
            payouts = state.currentPayouts,
            selectedIds = selectedIds,
            viewport = viewport,
            onViewportChange = { viewport = it },
            onSelectionChange = { ids ->
                selectedIds = ids
                viewModel.selectNode(ids.singleOrNull())
                editorOpen = ids.size == 1
            },
            onMoveEnd = { node, x, y -> viewModel.moveNode(node, x, y) },
            onApplyConnection = { viewModel.applyLosEdit(it) },
            onCreateDownline = { parent, x, y ->
                viewModel.addNodeAt(StructureKind.CURRENT, x, y, parent)
                editorOpen = true
            },
            onViewSize = { viewSize = it },
            ghostNodes = ghostNodes,
            ghostPayouts = ghostPayouts,
            onClaimGhost = { currentId, planId -> viewModel.claimPlanSlot(currentId, planId) },
            planTargets = planTargets,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 10.dp, end = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (payout != null) "${qty(payout.group.pv)} PV" else "— PV",
                    style = statsStyle,
                    color = statsColor,
                )
                Text(
                    text = if (payout != null) money(payout.estimatedMonthly) else "—",
                    style = statsStyle,
                    color = statsColor,
                )
            }
            Text(
                text = "$peopleCount people",
                style = statsStyle,
                color = statsColor,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                FilledTonalIconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
            }
            Box {
                OutlinedButton(onClick = { ghostMenuOpen = true }) {
                    Text(ghostLabel, maxLines = 1)
                }
                DropdownMenu(expanded = ghostMenuOpen, onDismissRequest = { ghostMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            viewModel.selectMapGhostProfile(null)
                            ghostMenuOpen = false
                        },
                    )
                    state.snapshot.planProfiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                viewModel.selectMapGhostProfile(p.id)
                                ghostMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            AnimatedVisibility(
                visible = zoomMenuOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = { viewport = viewport.zoomBy(1.2f, pivot()) }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Zoom in")
                    }
                    FilledTonalIconButton(onClick = { viewport = viewport.zoomBy(1 / 1.2f, pivot()) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                    }
                    FilledTonalIconButton(onClick = { viewport = viewport.withZoom(1f, pivot()) }) {
                        Text("1×", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalIconButton(
                        onClick = {
                            viewport = CanvasViewport.fit(
                                nodes = nodes,
                                coupleOf = { state.snapshot.isCouple(it) },
                                viewWidth = viewSize.width.toFloat(),
                                viewHeight = viewSize.height.toFloat(),
                                density = density.density,
                            )
                        },
                    ) {
                        Icon(Icons.Filled.FitScreen, contentDescription = "Fit")
                    }
                    FilledTonalIconButton(onClick = { viewModel.applyLayout(StructureKind.CURRENT) }) {
                        Icon(Icons.Outlined.FilterCenterFocus, contentDescription = "Layout")
                    }
                }
            }
            FilledTonalButton(onClick = { zoomMenuOpen = !zoomMenuOpen }) {
                Text(zoomLabel)
            }
        }

        FloatingActionButton(
            onClick = {
                val center = viewport.visibleCenter(
                    viewSize.width.toFloat(),
                    viewSize.height.toFloat(),
                    density.density,
                )
                viewModel.addNodeAt(StructureKind.CURRENT, center.x, center.y)
                editorOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add IBO")
        }
    }

    if (editorOpen && selected != null) {
        OrgNodeSheet(
            snapshot = state.snapshot,
            node = selected,
            settings = state.settings,
            payout = state.currentPayouts[selected.id],
            onDismiss = { editorOpen = false },
            onSave = { name, partner, couple, notes, pv, bv, vcs ->
                viewModel.savePerson(selected, name, partner, couple, notes, pv, bv, vcs)
                editorOpen = false
            },
            onAddDownline = {
                viewModel.addChild(selected, "New partner", 100.0)
                editorOpen = false
            },
            onDelete = {
                viewModel.deleteNode(selected.id)
                editorOpen = false
            },
            onDetachUpline = {
                viewModel.applyLosEdit(
                    com.networkmarketing.planner.domain.canvas.LosGraph.ConnectionEdit(
                        childId = selected.id,
                        newParentId = null,
                    ),
                )
            },
        )
    }
}
