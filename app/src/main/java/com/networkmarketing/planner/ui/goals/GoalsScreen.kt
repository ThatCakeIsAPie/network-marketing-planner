package com.networkmarketing.planner.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.compensation.AmwayNaPy2027
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
    val config = viewModel.engine().config()
    val ranks = config.ranks
    var income by rememberSaveable(state.goals.monthlyIncomeTarget) {
        mutableStateOf(state.goals.monthlyIncomeTarget.toInt().toString())
    }
    var rankId by rememberSaveable(state.goals.targetRankId) { mutableStateOf(state.goals.targetRankId) }
    var rankMenu by remember { mutableStateOf(false) }
    var bvPerPv by rememberSaveable(state.settings.bvPerPv) { mutableStateOf(state.settings.bvPerPv.toString()) }
    var customerSales by rememberSaveable(state.settings.customerSalesPercent) {
        mutableStateOf((state.settings.customerSalesPercent * 100).toInt().toString())
    }
    var vcs by rememberSaveable(state.settings.vcsPercent) {
        mutableStateOf((state.settings.vcsPercent * 100).toInt().toString())
    }
    var spMonths by rememberSaveable(state.settings.silverProducerMonthsPy) {
        mutableStateOf(state.settings.silverProducerMonthsPy.toString())
    }
    var consecutive by rememberSaveable(state.settings.consecutiveSilverMonths) {
        mutableStateOf(state.settings.consecutiveSilverMonths.toString())
    }
    var pqMonths by rememberSaveable(state.settings.pqMonthsPy) { mutableStateOf(state.settings.pqMonthsPy.toString()) }
    var rubyYtd by rememberSaveable(state.settings.rubyPvPy) { mutableStateOf(state.settings.rubyPvPy.toInt().toString()) }
    var groupYtd by rememberSaveable(state.settings.groupPvPy) { mutableStateOf(state.settings.groupPvPy.toInt().toString()) }
    var downlineYtd by rememberSaveable(state.settings.totalDownlinePvPy) {
        mutableStateOf(state.settings.totalDownlinePvPy.toInt().toString())
    }
    var newIboMonths by rememberSaveable(state.settings.newIboBaselineMonths) {
        mutableStateOf(state.settings.newIboBaselineMonths.toString())
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
            Text(config.profileTitle, style = MaterialTheme.typography.titleLarge)
            Text(config.sourceNote, style = MaterialTheme.typography.bodyMedium)

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
                onClick = { viewModel.updateGoals(income.toDoubleOrNull() ?: 0.0, rankId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save goals") }

            state.currentPayout?.let { payout ->
                val target = config.rank(state.goals.targetRankId)
                val need = viewModel.engine().neededForRank(payout, target)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("From the current map", style = MaterialTheme.typography.titleLarge)
                        Text("Estimated ${money(payout.estimatedMonthly)} vs ${money(state.goals.monthlyIncomeTarget)} goal.")
                        Text("Rank now: ${payout.currentRank.title}. Target: ${target.title}. Q month: ${if (payout.silverProducerMonth) "yes" else "no"}.")
                        Text(need.hint)
                        payout.corePlus.progressNotes.take(4).forEach { Text("• $it") }
                    }
                }
            }

            Text("Retail margin", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                config.retailMargins.forEach { margin ->
                    FilterChip(
                        selected = kotlin.math.abs(state.settings.retailMarginPercent - margin) < 0.001,
                        onClick = { viewModel.updateSettings(state.settings.copy(retailMarginPercent = margin)) },
                        label = { Text("${(margin * 100).toInt()}%") },
                    )
                }
            }

            Text("Rule 4.12 / baseline", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = customerSales,
                onValueChange = { customerSales = it },
                label = { Text("Personal volume from customer sales %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vcs,
                onValueChange = { vcs = it },
                label = { Text("Verified customer sales (VCS) %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Full Personal BV bonus needs ≥70% customer sales and ≥60% VCS; otherwise Personal BV is prorated.")

            Text("Performance year (YTD, excluding this month)", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = spMonths,
                onValueChange = { spMonths = it },
                label = { Text("Silver Producer months so far this PY") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = consecutive,
                onValueChange = { consecutive = it },
                label = { Text("Consecutive Q months already") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pqMonths,
                onValueChange = { pqMonths = it },
                label = { Text("PQ months so far this PY") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rubyYtd,
                onValueChange = { rubyYtd = it },
                label = { Text("Ruby PV so far this PY") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = groupYtd,
                onValueChange = { groupYtd = it },
                label = { Text("Group PV so far this PY (Founders VE)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = downlineYtd,
                onValueChange = { downlineYtd = it },
                label = { Text("Total downline PV so far this PY (Founders VE)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newIboMonths,
                onValueChange = { newIboMonths = it },
                label = { Text("New-IBO baseline months (SSI tracker)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            SettingSwitch("I already qualify Platinum+", state.settings.isPlatinumOrAbove) {
                viewModel.updateSettings(state.settings.copy(isPlatinumOrAbove = it))
            }
            SettingSwitch("Include retail margin", state.settings.includeRetailMargin) {
                viewModel.updateSettings(state.settings.copy(includeRetailMargin = it))
            }
            SettingSwitch("Rule 4.13 met (differential eligibility)", state.settings.meetsRule413) {
                viewModel.updateSettings(state.settings.copy(meetsRule413 = it))
            }
            SettingSwitch("Include leadership bonus", state.settings.includeLeadershipBonus) {
                viewModel.updateSettings(state.settings.copy(includeLeadershipBonus = it))
            }
            SettingSwitch("Include depth bonus", state.settings.includeDepthBonus) {
                viewModel.updateSettings(state.settings.copy(includeDepthBonus = it))
            }
            SettingSwitch("Include Ruby Bonus (15k Ruby PV)", state.settings.includeRubyBonus) {
                viewModel.updateSettings(state.settings.copy(includeRubyBonus = it))
            }
            SettingSwitch("Include Performance Plus / Elite", state.settings.includePerformancePlus) {
                viewModel.updateSettings(state.settings.copy(includePerformancePlus = it))
            }
            SettingSwitch("CSI eligible (new IBO years, ≤9%)", state.settings.csiEligible) {
                viewModel.updateSettings(state.settings.copy(csiEligible = it, includeCsi = it))
            }
            SettingSwitch("BFI eligible", state.settings.bfiEligible) {
                viewModel.updateSettings(state.settings.copy(bfiEligible = it))
            }
            SettingSwitch("BBI eligible", state.settings.bbiEligible) {
                viewModel.updateSettings(state.settings.copy(bbiEligible = it))
            }

            OutlinedTextField(
                value = bvPerPv,
                onValueChange = { bvPerPv = it },
                label = { Text("BV per 1 PV (default 3.43)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.updateSettings(
                        state.settings.copy(
                            bvPerPv = bvPerPv.toDoubleOrNull() ?: state.settings.bvPerPv,
                            customerSalesPercent = (customerSales.toDoubleOrNull() ?: 70.0) / 100.0,
                            vcsPercent = (vcs.toDoubleOrNull() ?: 60.0) / 100.0,
                            silverProducerMonthsPy = spMonths.toIntOrNull() ?: 0,
                            consecutiveSilverMonths = consecutive.toIntOrNull() ?: 0,
                            pqMonthsPy = pqMonths.toIntOrNull() ?: 0,
                            rubyPvPy = rubyYtd.toDoubleOrNull() ?: 0.0,
                            groupPvPy = groupYtd.toDoubleOrNull() ?: 0.0,
                            totalDownlinePvPy = downlineYtd.toDoubleOrNull() ?: 0.0,
                            newIboBaselineMonths = newIboMonths.toIntOrNull() ?: 0,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save formula & YTD settings") }

            Text("Performance brackets (${AmwayNaPy2027.PROFILE_ID})", style = MaterialTheme.typography.titleLarge)
            config.brackets.filter { it.minPv > 0 }.forEach { bracket ->
                val upper = bracket.maxPvExclusive?.let { "–${(it - 0.01)}" } ?: "+"
                Text("${bracket.minPv.toInt()}$upper PV → ${percent(bracket.percent)} of BV")
            }

            Text("Engine assumptions", style = MaterialTheme.typography.titleLarge)
            config.assumptions.forEach { Text("• $it") }

            OutlinedButton(
                onClick = { viewModel.restoreSample() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text("Restore sample organization") }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
