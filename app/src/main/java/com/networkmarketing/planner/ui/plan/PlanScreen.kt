package com.networkmarketing.planner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.DisclaimerBanner
import com.networkmarketing.planner.ui.components.ExpandableTree
import com.networkmarketing.planner.ui.components.MetricCard
import com.networkmarketing.planner.ui.components.MetricRow
import com.networkmarketing.planner.ui.components.NodeEditorDialog
import com.networkmarketing.planner.ui.components.OrgChart
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.qty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { state.snapshot.node(it) } ?: state.snapshot.root(StructureKind.IDEAL)
    val gap = state.gap

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ideal structure") }) },
        floatingActionButton = {
            val parent = selected
            if (parent != null) {
                FloatingActionButton(onClick = { editorOpen = true }) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Edit or add")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerBanner(compact = true)
            Text(
                "Sketch the organization you want, then compare it with the current map. Gaps are estimates from the same compensation engine.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (gap != null) {
                MetricRow {
                    MetricCard("PV to ideal", qty(gap.groupPvGap), Modifier.weight(1f))
                    MetricCard("Income gap", money(gap.incomeGapToIdeal), Modifier.weight(1f))
                    MetricCard("People gap", gap.peopleGap.toString(), Modifier.weight(1f))
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What looks missing", style = MaterialTheme.typography.titleLarge)
                        gap.suggestions.forEach { Text("• $it") }
                    }
                }
            }
            MetricRow {
                MetricCard(
                    "Current estimate",
                    money(state.currentPayout?.estimatedMonthly ?: 0.0),
                    Modifier.weight(1f),
                    state.currentPayout?.currentRank?.title,
                )
                MetricCard(
                    "Ideal estimate",
                    money(state.idealPayout?.estimatedMonthly ?: 0.0),
                    Modifier.weight(1f),
                    state.idealPayout?.currentRank?.title,
                )
            }
            OutlinedButton(onClick = { viewModel.copyCurrentToIdeal() }, modifier = Modifier.fillMaxWidth()) {
                Text("Copy current map into ideal")
            }
            Text("Ideal map", style = MaterialTheme.typography.titleLarge)
            OrgChart(
                snapshot = state.snapshot,
                kind = StructureKind.IDEAL,
                selectedId = selected?.id,
                onSelect = {
                    selectedId = it.id
                    editorOpen = true
                },
            )
            ExpandableTree(
                snapshot = state.snapshot,
                kind = StructureKind.IDEAL,
                selectedId = selected?.id,
                onSelect = {
                    selectedId = it.id
                    editorOpen = true
                },
            )
            Button(
                onClick = { selectedId = state.snapshot.root(StructureKind.IDEAL)?.id; editorOpen = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 72.dp),
            ) {
                Text("Edit selected / add frontline")
            }
        }
    }

    if (editorOpen && selected != null) {
        NodeEditorDialog(
            snapshot = state.snapshot,
            node = selected,
            onDismiss = { editorOpen = false },
            onSave = { name, pv ->
                viewModel.saveNode(selected, name, pv)
                editorOpen = false
            },
            onAddChild = { name, pv ->
                viewModel.addChild(selected, name, pv)
                editorOpen = false
            },
            onDelete = {
                viewModel.deleteNode(selected.id)
                editorOpen = false
            },
        )
    }
}
```
