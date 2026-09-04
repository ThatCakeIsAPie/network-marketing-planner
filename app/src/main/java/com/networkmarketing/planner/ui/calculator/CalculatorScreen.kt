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
                    label = { Text("Group PV (personal + downline)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.calculatorMaxLegs,
                    onValueChange = {
                        viewModel.setCalculatorFields(state.calculatorPersonalPv, state.calculatorGroupPv, it, false)
                    },
                    label = { Text("Frontline already at max bracket") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "BV is personal/group PV × ${state.settings.bvPerPv} (editable in Goals). Max-bracket legs are modeled at ${qty(viewModel.engine().config().brackets.maxBy { it.minPv }.minPv)} PV each.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PayoutSummary(payout, state.goals.monthlyIncomeTarget)
            MetricRow {
                MetricCard("Performance", money(payout.performanceBonus), Modifier.weight(1f), percent(payout.performancePercent))
                MetricCard("Differential", money(payout.differential), Modifier.weight(1f), "in the performance total")
            }
            MetricRow {
                MetricCard("Leadership", money(payout.leadershipBonus), Modifier.weight(1f))
                MetricCard("Ruby-volume", money(payout.rubyBonus), Modifier.weight(1f), "${qty(payout.rubyPv)} ruby PV")
                MetricCard("Customer profit", money(payout.customerProfit), Modifier.weight(1f))
            }
            Card(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Toward ${target.title}", style = MaterialTheme.typography.titleLarge)
                    if (need.met) {
                        Text("These inputs already meet the selected rank under the default engine.")
                    } else {
                        if (need.pvNeeded > 0) Text("Need ${qty(need.pvNeeded)} more group PV.")
                        if (need.maxPercentLegsNeeded > 0) Text("Need ${need.maxPercentLegsNeeded} more max-bracket frontline.")
                        if (need.rubyPvNeeded > 0) Text("Need ${qty(need.rubyPvNeeded)} more ruby PV.")
                    }
                    Text(
                        "Formulas live in CompensationEngine / CompensationConfig. Toggle leadership, ruby, and customer profit in Goals.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
```
