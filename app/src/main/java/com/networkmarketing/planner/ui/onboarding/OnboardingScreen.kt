package com.networkmarketing.planner.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.components.DisclaimerBanner

@Composable
fun OnboardingScreen(viewModel: PlannerViewModel) {
    var accepted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Network Marketing Planner", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Map your organization, sketch an ideal structure, and estimate what volume it takes to hit an income or rank goal.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Local-first. Nothing leaves this device. A sample team is included so you can tap around before entering your own numbers. Set income and rank goals anytime on the Goals tab.",
            style = MaterialTheme.typography.bodyLarge,
        )
        DisclaimerBanner()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text("I understand this is unofficial, educational, and not a promise of income.")
        }
        Button(
            onClick = {
                viewModel.completeOnboarding(
                    income = 2_000.0,
                    rankId = RankIds.SILVER,
                    accepted = accepted,
                )
            },
            enabled = accepted,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open the planner") }
    }
}
