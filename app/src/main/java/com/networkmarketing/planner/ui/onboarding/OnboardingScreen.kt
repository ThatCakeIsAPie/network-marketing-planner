package com.networkmarketing.planner.ui.onboarding

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.DisclaimerBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: PlannerViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var income by rememberSaveable { mutableStateOf("2000") }
    var rankId by rememberSaveable { mutableStateOf(RankIds.SILVER) }
    var rankMenu by remember { mutableStateOf(false) }
    val ranks = viewModel.engine().config().ranks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Network Marketing Planner", style = MaterialTheme.typography.headlineLarge)
        when (step) {
            0 -> {
                Text("Map your organization, sketch an ideal structure, and estimate what volume it takes to hit an income or rank goal.", style = MaterialTheme.typography.bodyLarge)
                Text("Local-first. Nothing leaves this device. A sample team is included so you can tap around before entering your own numbers.", style = MaterialTheme.typography.bodyLarge)
                DisclaimerBanner()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("I understand this is unofficial, educational, and not a promise of income.")
                }
                Button(
                    onClick = { step = 1 },
                    enabled = accepted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue") }
            }
            else -> {
                Text("What are you building toward?", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = it },
                    label = { Text("Monthly income goal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                text = { Text(rank.title) },
                                onClick = {
                                    rankId = rank.id
                                    rankMenu = false
                                },
                            )
                        }
                    }
                }
                Text("You can change both later. The Map tab opens on sample data.")
                Button(
                    onClick = {
                        viewModel.completeOnboarding(income.toDoubleOrNull() ?: 2_000.0, rankId, accepted)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open the planner") }
            }
        }
    }
}
```
