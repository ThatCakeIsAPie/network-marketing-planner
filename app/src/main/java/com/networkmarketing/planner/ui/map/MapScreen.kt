package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.percent
import com.networkmarketing.planner.ui.components.qty

@OptIn(ExperimentalMaterial3Api::class)
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
    val nodes = state.snapshot.nodes(StructureKind.CURRENT)
    val selected = state.selectedNodeId?.let { state.snapshot.node(it) }
    val payout = state.currentPayout

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Organization map") }) },
        floatingActionButton = {
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
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add IBO")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OrgCanvas(
                snapshot = state.snapshot,
                kind = StructureKind.CURRENT,
                payouts = state.currentPayouts,
                selectedId = selected?.id,
                viewport = viewport,
                onViewportChange = { viewport = it },
                onSelect = {
                    viewModel.selectNode(it)
                    editorOpen = it != null
                },
                onMoveEnd = { node, x, y -> viewModel.moveNode(node, x, y) },
                onApplyConnection = { viewModel.applyLosEdit(it) },
                onCreateDownline = { parent, x, y ->
                    viewModel.addNodeAt(StructureKind.CURRENT, x, y, parent)
                    editorOpen = true
                },
                onViewSize = { viewSize = it },
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        "Drag the grid to pan · pinch or +/− to zoom · drag top/bottom docks to set LOS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (payout != null) {
                        Text(
                            "${money(payout.estimatedMonthly)} · ${payout.currentRank.title} · " +
                                "${percent(payout.performancePercent)} · G ${qty(payout.group.pv)} PV · " +
                                "${payout.maxPercentLegs}×25%",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = { viewport = viewport.zoomBy(1.2f, pivot()) }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Zoom in")
                    }
                    FilledTonalIconButton(onClick = { viewport = viewport.zoomBy(1 / 1.2f, pivot()) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                    }
                    FilledTonalButton(onClick = { viewport = viewport.withZoom(1f, pivot()) }) {
                        Text("100%")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
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
                        Icon(Icons.Filled.FitScreen, contentDescription = null)
                        Text(" Fit", modifier = Modifier.padding(start = 4.dp))
                    }
                    FilledTonalButton(onClick = { viewModel.applyLayout(StructureKind.CURRENT) }) {
                        Icon(Icons.Outlined.FilterCenterFocus, contentDescription = null)
                        Text(" Layout")
                    }
                }
            }
        }
    }

    if (editorOpen && selected != null) {
        OrgNodeSheet(
            snapshot = state.snapshot,
            node = selected,
            settings = state.settings,
            payout = state.currentPayouts[selected.id],
            onDismiss = { editorOpen = false },
            onSave = { name, partner, couple, notes, pv, bv ->
                viewModel.savePerson(selected, name, partner, couple, notes, pv, bv)
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
