package com.networkmarketing.planner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot

@Composable
fun NodeEditorDialog(
    snapshot: OrgSnapshot,
    node: OrgNode,
    onDismiss: () -> Unit,
    onSave: (name: String, personalPv: Double) -> Unit,
    onAddChild: (name: String, personalPv: Double) -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(node.id) { mutableStateOf(snapshot.displayName(node)) }
    var pv by rememberSaveable(node.id) { mutableStateOf(node.personalPv.toInt().toString()) }
    var childName by rememberSaveable(node.id) { mutableStateOf("") }
    var childPv by rememberSaveable(node.id) { mutableStateOf("100") }
    val isYou = snapshot.isYou(node)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isYou) "Your volume" else "Edit person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    enabled = !isYou,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pv,
                    onValueChange = { pv = it },
                    label = { Text("Personal PV") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("BV is stored as personal PV × the BV/PV ratio from Goals.")
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it },
                    label = { Text("Add frontline name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = childPv,
                    onValueChange = { childPv = it },
                    label = { Text("New person personal PV") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, pv.toDoubleOrNull() ?: 0.0) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onAddChild(childName.ifBlank { "New partner" }, childPv.toDoubleOrNull() ?: 100.0)
                    },
                ) { Text("Add frontline") }
                if (!isYou) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
