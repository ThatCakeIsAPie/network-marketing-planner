package com.networkmarketing.planner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.MetricCard
import com.networkmarketing.planner.ui.components.MetricRow
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.qty
import com.networkmarketing.planner.ui.map.CanvasViewport
import com.networkmarketing.planner.ui.map.OrgCanvas
import com.networkmarketing.planner.ui.map.OrgNodeSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(CanvasViewport()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val nodes = state.snapshot.nodes(StructureKind.IDEAL)
    val selected = selectedId?.let { state.snapshot.node(it) }
    val gap = state.gap

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ideal structure") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val center = viewport.visibleCenter(
                        viewSize.width.toFloat(),
                        viewSize.height.toFloat(),
                        density.density,
                    )
                    val parent = selected ?: state.snapshot.root(StructureKind.IDEAL)
                    viewModel.addNodeAt(StructureKind.IDEAL, center.x, center.y, parent)
                    editorOpen = true
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add ideal IBO")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Surface(tonalElevation = 1.dp) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (gap != null) {
                        MetricRow {
                            MetricCard("PV to ideal", qty(gap.groupPvGap), Modifier.weight(1f))
                            MetricCard("Income gap", money(gap.incomeGapToIdeal), Modifier.weight(1f))
                            MetricCard("People gap", gap.peopleGap.toString(), Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.copyCurrentToIdeal() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy current") }
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.FitScreen, contentDescription = null)
                            Text(" Fit")
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                OrgCanvas(
                    snapshot = state.snapshot,
                    kind = StructureKind.IDEAL,
                    payouts = state.idealPayouts,
                    selectedId = selected?.id,
                    viewport = viewport,
                    onViewportChange = { viewport = it },
                    onSelect = {
                        selectedId = it
                        editorOpen = it != null
                    },
                    onMoveEnd = { node, x, y -> viewModel.moveNode(node, x, y) },
                    onApplyConnection = { viewModel.applyLosEdit(it) },
                    onCreateDownline = { parent, x, y ->
                        viewModel.addNodeAt(StructureKind.IDEAL, x, y, parent)
                        editorOpen = true
                    },
                    onViewSize = { viewSize = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (editorOpen && selected != null) {
        OrgNodeSheet(
            snapshot = state.snapshot,
            node = selected,
            settings = state.settings,
            payout = state.idealPayouts[selected.id],
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
