package com.networkmarketing.planner.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.ui.components.percent
import com.networkmarketing.planner.ui.components.qty
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgNodeSheet(
    snapshot: OrgSnapshot,
    node: OrgNode,
    settings: PlannerSettings,
    payout: PayoutBreakdown?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
        vcsPv: Double,
    ) -> Unit,
    onAddDownline: () -> Unit,
    onDelete: () -> Unit,
    onDetachUpline: () -> Unit,
) {
    val member = snapshot.member(node.memberId)
    val isYou = snapshot.isYou(node)
    var name by rememberSaveable(node.id) { mutableStateOf(member?.name ?: "") }
    var partner by rememberSaveable(node.id) { mutableStateOf(member?.partnerName.orEmpty()) }
    var couple by rememberSaveable(node.id) { mutableStateOf(member?.isCouple == true) }
    var notes by rememberSaveable(node.id) { mutableStateOf(member?.notes.orEmpty()) }
    var pv by rememberSaveable(node.id) { mutableStateOf(formatNum(node.personalPv)) }
    var bv by rememberSaveable(node.id) { mutableStateOf(formatNum(node.personalBv)) }
    var vcs by rememberSaveable(node.id) {
        mutableStateOf(formatNum(node.effectiveVcsPv(settings)))
    }
    val derived = abs(node.personalBv - node.personalPv * settings.bvPerPv) < 0.05
    var deriveBv by rememberSaveable(node.id) { mutableStateOf(derived) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val personalPvValue = pv.toDoubleOrNull() ?: 0.0
    val vcsPvValue = (vcs.toDoubleOrNull() ?: 0.0).coerceIn(0.0, personalPvValue.coerceAtLeast(0.0))
    val vcsRatio = if (personalPvValue > 1e-9) vcsPvValue / personalPvValue else 0.0
    val meets60 = vcsRatio + 1e-9 >= 0.60

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (isYou) "Your IBO" else "IBO details",
                style = MaterialTheme.typography.titleLarge,
            )
            payout?.let {
                Text(
                    "${it.currentRank.title} · ${percent(it.performancePercent)} · Group ${qty(it.group.pv)} PV · Ruby ${qty(it.rubyPv)} PV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !couple,
                    onClick = { couple = false },
                    label = { Text("Single") },
                )
                FilterChip(
                    selected = couple,
                    onClick = { couple = true },
                    label = { Text("Couple") },
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (couple) "Name 1" else "Name") },
                enabled = !isYou,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (couple) {
                OutlinedTextField(
                    value = partner,
                    onValueChange = { partner = it },
                    label = { Text("Name 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = pv,
                onValueChange = {
                    pv = it
                    if (deriveBv) {
                        val p = it.toDoubleOrNull() ?: 0.0
                        bv = formatNum(p * settings.bvPerPv)
                    }
                },
                label = { Text("Personal PV") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("BV from PV × ${settings.bvPerPv}", modifier = Modifier.weight(1f))
                Switch(
                    checked = deriveBv,
                    onCheckedChange = {
                        deriveBv = it
                        if (it) {
                            val p = pv.toDoubleOrNull() ?: 0.0
                            bv = formatNum(p * settings.bvPerPv)
                        }
                    },
                )
            }
            OutlinedTextField(
                value = bv,
                onValueChange = { bv = it },
                label = { Text("Personal BV") },
                enabled = !deriveBv,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vcs,
                onValueChange = { vcs = it },
                label = { Text("VCS PV (of personal)") },
                supportingText = {
                    Text(
                        if (meets60) {
                            "≥60% VCS met (${percent(vcsRatio)}) — Rule 4.12 / baseline"
                        } else {
                            "Below 60% VCS (${percent(vcsRatio)}) — Personal BV prorated"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FilterChip(
                selected = meets60,
                onClick = {
                    val p = pv.toDoubleOrNull() ?: 0.0
                    vcs = formatNum(if (meets60) p * 0.59 else p * 0.60)
                },
                label = { Text(if (meets60) "≥60% VCS" else "Set to 60% VCS") },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val p = pv.toDoubleOrNull() ?: 0.0
                    val b = if (deriveBv) p * settings.bvPerPv else bv.toDoubleOrNull() ?: (p * settings.bvPerPv)
                    val v = (vcs.toDoubleOrNull() ?: (p * settings.vcsPercent)).coerceIn(0.0, p.coerceAtLeast(0.0))
                    onSave(name, partner, couple, notes, p, b, v)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            OutlinedButton(onClick = onAddDownline, modifier = Modifier.fillMaxWidth()) {
                Text("Add downline")
            }
            if (!isYou) {
                TextButton(onClick = onDetachUpline) { Text("Detach upline (keep on canvas)") }
                TextButton(onClick = onDelete) {
                    Text("Delete IBO and downline", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
