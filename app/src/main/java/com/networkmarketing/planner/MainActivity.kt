package com.networkmarketing.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.networkmarketing.planner.ui.navigation.PlannerApp
import com.networkmarketing.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PlannerApplication
        setContent {
            PlannerTheme {
                PlannerApp(app.container)
            }
        }
    }
}
