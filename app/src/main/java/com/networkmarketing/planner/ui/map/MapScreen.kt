package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.networkmarketing.planner.ui.components.PayoutSummary
import com.networkmarketing.planner.ui.components.percent
import com.networkmarketing.planner.ui.components.qty
import com.networkmarketing.planner.ui.selectedNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    var chartMode by rememberSaveable { mutableStateOf(true) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    val payout = state.currentPayout
    val selected = state.selectedNode()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Organization map") })
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
            if (payout != null) {
                PayoutSummary(payout, state.goals.monthlyIncomeTarget)
                MetricRow {
                    MetricCard("Group PV", qty(payout.group.pv), Modifier.weight(1f), "${qty(payout.group.bv)} BV")
                    MetricCard("Rank", payout.currentRank.title, Modifier.weight(1f), percent(payout.performancePercent))
                    MetricCard("Max-bracket legs", payout.maxPercentLegs.toString(), Modifier.weight(1f), "${payout.frontlineCount} frontline")
                }
            }
            FilterChip(
                selected = chartMode,
                onClick = { chartMode = !chartMode },
                label = { Text(if (chartMode) "Chart view" else "Tree view") },
                leadingIcon = {
                    Icon(
                        if (chartMode) Icons.Filled.AccountTree else Icons.Filled.ViewAgenda,
                        contentDescription = null,
                    )
                },
            )
            Text(
                "Tap a person to edit volume or add someone on their frontline. Demo data is loaded so you can explore immediately.",
                modifier = Modifier.padding(bottom = 24.dp),
            )
            if (chartMode) {
                OrgChart(
                    snapshot = state.snapshot,
                    kind = StructureKind.CURRENT,
                    selectedId = selected?.id,
                    onSelect = {
                        viewModel.selectNode(it.id)
                        editorOpen = true
                    },
                )
            } else {
                ExpandableTree(
                    snapshot = state.snapshot,
                    kind = StructureKind.CURRENT,
                    selectedId = selected?.id,
                    onSelect = {
                        viewModel.selectNode(it.id)
                        editorOpen = true
                    },
                )
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
