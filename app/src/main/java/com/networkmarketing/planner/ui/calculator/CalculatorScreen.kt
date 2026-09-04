package com.networkmarketing.planner.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.ui.PlannerUiState
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.DisclaimerBanner
import com.networkmarketing.planner.ui.components.MetricCard
import com.networkmarketing.planner.ui.components.MetricRow
import com.networkmarketing.planner.ui.components.PayoutSummary
import com.networkmarketing.planner.ui.components.money
import com.networkmarketing.planner.ui.components.percent
import com.networkmarketing.planner.ui.components.qty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
) {
    val payout = viewModel.calculatorPayout(state)
    val target = viewModel.engine().config().rank(state.goals.targetRankId)
    val need = viewModel.engine().neededForRank(payout, target)

    Scaffold(topBar = { TopAppBar(title = { Text("Volume calculator") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerBanner(compact = true)
            FilterChip(
                selected = state.calculatorUseOrg,
                onClick = {
                    viewModel.setCalculatorFields(
                        state.calculatorPersonalPv,
                        state.calculatorGroupPv,
                        state.calculatorMaxLegs,
                        !state.calculatorUseOrg,
                    )
                },
                label = { Text(if (state.calculatorUseOrg) "Using current organization" else "Manual inputs") },
            )
            if (!state.calculatorUseOrg) {
                OutlinedTextField(
                    value = state.calculatorPersonalPv,
                    onValueChange = {
                        viewModel.setCalculatorFields(it, state.calculatorGroupPv, state.calculatorMaxLegs, false)
                    },
                    label = { Text("Personal PV") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.calculatorGroupPv,
                    onValueChange = {
                        viewModel.setCalculatorFields(state.calculatorPersonalPv, it, state.calculatorMaxLegs, false)
                    },
                    label = { Text("Group PV (Personal + non-25% pass-up)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.calculatorMaxLegs,
                    onValueChange = {
                        viewModel.setCalculatorFields(state.calculatorPersonalPv, state.calculatorGroupPv, it, false)
                    },
                    label = { Text("Frontline at 25% this month") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Profile ${viewModel.engine().config().profileId}. BV = PV × ${state.settings.bvPerPv}. " +
                        "25% legs are excluded from Group/Ruby PV. Rule 4.12 factor ${percent(payout.rule412Factor)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PayoutSummary(payout, state.goals.monthlyIncomeTarget)
            MetricRow {
                MetricCard("Group PV", qty(payout.group.pv), Modifier.weight(1f), "${qty(payout.passUp.pv)} pass-up")
                MetricCard("Ruby / side", qty(payout.rubyPv), Modifier.weight(1f), "${qty(payout.totalDownline.pv)} team PV")
            }
            MetricRow {
                MetricCard("Performance", money(payout.performanceBonus), Modifier.weight(1f), percent(payout.performancePercent))
                MetricCard("Differential", money(payout.differential), Modifier.weight(1f), "in the performance total")
            }
            MetricRow {
                MetricCard("Leadership", money(payout.leadershipBonus), Modifier.weight(1f), "pass-up ${money(payout.leadershipPassedToSponsor)}")
                MetricCard("Depth", money(payout.depthBonus), Modifier.weight(1f))
                MetricCard("Ruby bonus", money(payout.rubyBonus), Modifier.weight(1f), "≥15k Ruby PV")
            }
            MetricRow {
                MetricCard("Plus / Elite", money(payout.corePlus.performancePlusAmount), Modifier.weight(1f), percent(payout.corePlus.performancePlusPercent))
                MetricCard("Retail", money(payout.retailMargin), Modifier.weight(1f), percent(state.settings.retailMarginPercent))
            }
            Card(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Toward ${target.title}", style = MaterialTheme.typography.titleLarge)
                    Text(need.hint)
                    if (need.met) {
                        Text("These inputs already meet the selected rank under the AmwayNA_PY2027 snapshot rules.")
                    } else {
                        if (need.pvNeeded > 0) Text("Need ${qty(need.pvNeeded)} more Group PV (or use another Q-month path).")
                        if (need.maxPercentLegsNeeded > 0) Text("Need ${need.maxPercentLegsNeeded} more 25% frontline.")
                        if (need.rubyPvNeeded > 0) Text("Need ${qty(need.rubyPvNeeded)} more Ruby PV.")
                    }
                    Text("Q month: ${if (payout.silverProducerMonth) "yes" else "no"}. PQ: ${if (payout.corePlus.pqMonth) "yes" else "no"}. FQ this month: ${payout.corePlus.fqCount}.")
                    Text(
                        "Formulas: CompensationEngine + AmwayNA_PY2027. Annual Core Plus (PQ/TTCI) uses YTD fields on Goals.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
