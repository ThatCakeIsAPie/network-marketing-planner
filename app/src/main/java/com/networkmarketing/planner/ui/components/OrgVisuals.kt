package com.networkmarketing.planner.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind

@Composable
fun OrgChart(
    snapshot: OrgSnapshot,
    kind: StructureKind,
    selectedId: String?,
    onSelect: (OrgNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val generations = snapshot.generations(kind)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        generations.forEachIndexed { index, row ->
            if (index == 0) {
                Text("Generation $index (you)", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Generation $index", style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { node ->
                    val group = snapshot.groupVolume(node.id)
                    val selected = node.id == selectedId
                    Card(
                        onClick = { onSelect(node) },
                        modifier = Modifier.widthIn(min = 140.dp, max = 180.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(snapshot.displayName(node), fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("P ${qty(node.personalPv)} PV", style = MaterialTheme.typography.bodyMedium)
                            Text("G ${qty(group.pv)} PV", style = MaterialTheme.typography.bodyMedium)
                            Text("${snapshot.children(node.id).size} frontline", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableTree(
    snapshot: OrgSnapshot,
    kind: StructureKind,
    selectedId: String?,
    onSelect: (OrgNode) -> Unit,
) {
    val root = snapshot.root(kind) ?: return
    TreeNodeRow(snapshot, root, 0, selectedId, onSelect)
}

@Composable
private fun TreeNodeRow(
    snapshot: OrgSnapshot,
    node: OrgNode,
    depth: Int,
    selectedId: String?,
    onSelect: (OrgNode) -> Unit,
) {
    val group = snapshot.groupVolume(node.id)
    val selected = node.id == selectedId
    TextButton(
        onClick = { onSelect(node) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                snapshot.displayName(node),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Personal ${qty(node.personalPv)} PV · Group ${qty(group.pv)} PV / ${qty(group.bv)} BV",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    snapshot.children(node.id).forEach { child ->
        TreeNodeRow(snapshot, child, depth + 1, selectedId, onSelect)
    }
}
```
