package com.networkmarketing.planner.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.DisclaimerBanner
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.percent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    val ranks = viewModel.engine().config().ranks
    var income by rememberSaveable(state.goals.monthlyIncomeTarget) {
        mutableStateOf(state.goals.monthlyIncomeTarget.toInt().toString())
    }
    var rankId by rememberSaveable(state.goals.targetRankId) { mutableStateOf(state.goals.targetRankId) }
    var rankMenu by remember { mutableStateOf(false) }
    var bvPerPv by rememberSaveable(state.settings.bvPerPv) { mutableStateOf(state.settings.bvPerPv.toString()) }
    var customerPct by rememberSaveable(state.settings.customerProfitPercent) {
        mutableStateOf((state.settings.customerProfitPercent * 100).toInt().toString())
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Goals & settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerBanner()
            OutlinedTextField(
                value = income,
                onValueChange = { income = it },
                label = { Text("Monthly income goal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(expanded = rankMenu, onExpandedChange = { rankMenu = it }) {
                OutlinedTextField(
                    value = ranks.firstOrNull { it.id == rankId }?.title ?: rankId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rank goal") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rankMenu) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = rankMenu, onDismissRequest = { rankMenu = false }) {
                    ranks.forEach { rank ->
                        DropdownMenuItem(
                            text = { Text("${rank.title} — ${rank.summary}") },
                            onClick = {
                                rankId = rank.id
                                rankMenu = false
                            },
                        )
                    }
                }
            }
            Button(
                onClick = {
                    viewModel.updateGoals(income.toDoubleOrNull() ?: 0.0, rankId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save goals") }

            state.currentPayout?.let { payout ->
                val target = viewModel.engine().config().rank(state.goals.targetRankId)
                val need = viewModel.engine().neededForRank(payout, target)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("From the current map", style = MaterialTheme.typography.titleLarge)
                        Text("Estimated ${money(payout.estimatedMonthly)} vs ${money(state.goals.monthlyIncomeTarget)} goal.")
                        Text("Rank now: ${payout.currentRank.title}. Target: ${target.title}.")
                        if (!need.met) {
                            Text("Still needed: ${need.pvNeeded.toInt()} PV, ${need.maxPercentLegsNeeded} max-bracket legs, ${need.rubyPvNeeded.toInt()} ruby PV.")
                        } else {
                            Text("Rank target is met on this month's snapshot.")
                        }
                    }
                }
            }

            Text("Compensation knobs", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = bvPerPv,
                onValueChange = { bvPerPv = it },
                label = { Text("BV per 1 PV") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = customerPct,
                onValueChange = { customerPct = it },
                label = { Text("Customer profit % of personal BV") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingSwitch("Include customer profit", state.settings.includeCustomerProfit) {
                viewModel.updateSettings(state.settings.copy(includeCustomerProfit = it))
            }
            SettingSwitch("Include leadership bonus estimate", state.settings.includeLeadershipBonus) {
                viewModel.updateSettings(state.settings.copy(includeLeadershipBonus = it))
            }
            SettingSwitch("Include ruby-volume bonus estimate", state.settings.includeRubyBonus) {
                viewModel.updateSettings(state.settings.copy(includeRubyBonus = it))
            }
            Button(
                onClick = {
                    viewModel.updateSettings(
                        state.settings.copy(
                            bvPerPv = bvPerPv.toDoubleOrNull() ?: state.settings.bvPerPv,
                            customerProfitPercent = (customerPct.toDoubleOrNull() ?: 10.0) / 100.0,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save formula settings") }

            Text("Performance brackets", style = MaterialTheme.typography.titleLarge)
            viewModel.engine().config().brackets.filter { it.minPv > 0 }.forEach { bracket ->
                Text("${bracket.minPv.toInt()}+ PV → ${percent(bracket.percent)} of BV")
            }

            Text("Engine assumptions", style = MaterialTheme.typography.titleLarge)
            viewModel.engine().config().assumptions.forEach { Text("• $it") }

            OutlinedButton(
                onClick = { viewModel.restoreSample() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text("Restore sample organization") }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
