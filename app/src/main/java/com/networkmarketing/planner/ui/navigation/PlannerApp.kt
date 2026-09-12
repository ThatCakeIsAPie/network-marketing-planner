package com.networkmarketing.planner.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.networkmarketing.planner.di.AppContainer
import com.networkmarketing.planner.ui.PlannerViewModel
import com.networkmarketing.planner.ui.calculator.CalculatorScreen
import com.networkmarketing.planner.ui.goals.GoalsScreen
import com.networkmarketing.planner.ui.map.MapScreen
import com.networkmarketing.planner.ui.onboarding.OnboardingScreen
import com.networkmarketing.planner.ui.plan.PlanScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("map", "Map", Icons.Filled.AccountTree),
    Tab("plan", "Plan", Icons.Filled.Schema),
    Tab("calc", "Calculator", Icons.Filled.Calculate),
    Tab("goals", "Goals", Icons.Filled.Flag),
)

@Composable
fun PlannerApp(container: AppContainer) {
    val viewModel: PlannerViewModel = viewModel(
        key = if (container.isRemote) "remote:${container.serverUrl}" else "local",
        factory = PlannerViewModel.factory(
            container.repository,
            container.compensationEngine,
            container.gapAnalyzer,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (!state.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!state.goals.onboardingComplete) {
        OnboardingScreen(viewModel)
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("map") { MapScreen(state, viewModel) }
            composable("plan") { PlanScreen(state, viewModel) }
            composable("calc") { CalculatorScreen(state, viewModel) }
            composable("goals") { GoalsScreen(state, viewModel) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            FilledTonalIconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                tabs.forEach { tab ->
                    DropdownMenuItem(
                        text = { Text(tab.label) },
                        leadingIcon = { Icon(tab.icon, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            if (current != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
