package com.networkmarketing.planner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.qty
import com.networkmarketing.planner.ui.map.CanvasViewport
import com.networkmarketing.planner.ui.map.OrgCanvas
import com.networkmarketing.planner.ui.map.OrgNodeSheet

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
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var createText by remember { mutableStateOf("") }
    val profileId = state.editingPlanProfileId
    val profile = state.snapshot.planProfile(profileId)
    val nodes = state.snapshot.planNodes(profileId)
    val selected = selectedIds.singleOrNull()?.let { state.snapshot.node(it) }
    val gap = state.gap
    val idealPayout = state.idealPayout

    LaunchedEffect(profileId) {
        fitted = false
        selectedIds = emptySet()
        editorOpen = false
    }

    LaunchedEffect(viewSize, nodes.size, profileId) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        OrgCanvas(
            snapshot = state.snapshot,
            kind = StructureKind.IDEAL,
            payouts = state.idealPayouts,
            selectedIds = selectedIds,
            viewport = viewport,
            onViewportChange = { viewport = it },
            onSelectionChange = { ids ->
                selectedIds = ids
                editorOpen = ids.size == 1
            },
            onMoveEnd = { node, x, y -> viewModel.moveNode(node, x, y) },
            onApplyConnection = { viewModel.applyLosEdit(it) },
            onCreateDownline = { parent, x, y ->
                viewModel.addNodeAt(StructureKind.IDEAL, x, y, parent, planProfileId = profileId)
                editorOpen = true
            },
            onViewSize = { viewSize = it },
            planProfileId = profileId,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp, end = 56.dp)
                .widthIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                OutlinedButton(onClick = { profileMenuOpen = true }) {
                    Text(profile?.name ?: "Plan")
                }
                DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                    state.snapshot.planProfiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                viewModel.selectEditingPlanProfile(p.id)
                                profileMenuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("New profile…") },
                        onClick = {
                            profileMenuOpen = false
                            createText = ""
                            createOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename…") },
                        onClick = {
                            profileMenuOpen = false
                            renameText = profile?.name.orEmpty()
                            renameOpen = true
                        },
                    )
                    if (state.snapshot.planProfiles.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Delete profile") },
                            onClick = {
                                profileMenuOpen = false
                                viewModel.deletePlanProfile(profileId)
                            },
                        )
                    }
                }
            }
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = when {
                            idealPayout != null ->
                                "${profile?.name ?: "Plan"} · G ${qty(idealPayout.group.pv)} PV · ${money(idealPayout.estimatedMonthly)}"
                            else -> profile?.name ?: "Plan"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    if (gap != null) {
                        Text(
                            "Gap ${qty(gap.groupPvGap)} PV · ${money(gap.incomeGapToIdeal)} · ${gap.peopleGap} people",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalIconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            FilledTonalIconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { viewModel.copyCurrentToSelectedPlan() }) {
                Text("Copy current")
            }
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
                Text(" Fit")
            }
        }

        FloatingActionButton(
            onClick = {
                val center = viewport.visibleCenter(
                    viewSize.width.toFloat(),
                    viewSize.height.toFloat(),
                    density.density,
                )
                val parent = selected ?: state.snapshot.root(StructureKind.IDEAL, profileId)
                viewModel.addNodeAt(StructureKind.IDEAL, center.x, center.y, parent, planProfileId = profileId)
                editorOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add plan IBO")
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renamePlanProfile(profileId, renameText)
                        renameOpen = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (createOpen) {
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("New plan profile") },
            text = {
                OutlinedTextField(
                    value = createText,
                    onValueChange = { createText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    placeholder = { Text("Emerald") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlanProfile(createText.ifBlank { "Plan" })
                        createOpen = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { createOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (editorOpen && selected != null) {
        OrgNodeSheet(
            snapshot = state.snapshot,
            node = selected,
            settings = state.settings,
            payout = state.idealPayouts[selected.id],
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
