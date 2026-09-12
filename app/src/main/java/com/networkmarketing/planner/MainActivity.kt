package com.networkmarketing.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.networkmarketing.planner.di.AppContainer
import com.networkmarketing.planner.ui.navigation.PlannerApp
import com.networkmarketing.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Built per-activity so changing the sync server (and calling recreate()) rebuilds
        // the container with the right local/remote repository.
        val container = AppContainer(this)
        setContent {
            PlannerTheme {
                PlannerApp(container)
            }
        }
    }
}
