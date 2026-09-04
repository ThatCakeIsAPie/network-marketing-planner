package com.networkmarketing.planner.di

import android.content.Context
import com.networkmarketing.planner.data.local.PlannerDatabase
import com.networkmarketing.planner.data.repository.PlannerRepository
import com.networkmarketing.planner.domain.compensation.CompensationEngine
import com.networkmarketing.planner.domain.compensation.GapAnalyzer

class AppContainer(context: Context) {
    private val database = PlannerDatabase.create(context.applicationContext)
    val repository = PlannerRepository(database.plannerDao())
    val compensationEngine = CompensationEngine()
    val gapAnalyzer = GapAnalyzer(compensationEngine)
}
