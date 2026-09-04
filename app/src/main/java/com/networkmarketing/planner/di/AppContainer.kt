package com.networkmarketing.planner.di

import android.content.Context
import com.networkmarketing.planner.data.local.PlannerDatabase
import com.networkmarketing.planner.data.remote.RemotePlannerRepository
import com.networkmarketing.planner.data.remote.ServerPreferences
import com.networkmarketing.planner.data.repository.PlannerRepository
import com.networkmarketing.planner.data.repository.PlannerStore
import com.networkmarketing.planner.domain.compensation.CompensationEngine
import com.networkmarketing.planner.domain.compensation.GapAnalyzer

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val serverPreferences = ServerPreferences(appContext)

    /** When a server URL is configured the app reads/writes the shared backend; otherwise it stays on-device. */
    val serverUrl: String = serverPreferences.serverUrl
    val isRemote: Boolean = serverUrl.isNotBlank()

    val repository: PlannerStore = if (isRemote) {
        RemotePlannerRepository(serverUrl)
    } else {
        PlannerRepository(PlannerDatabase.create(appContext).plannerDao())
    }

    val compensationEngine = CompensationEngine()
    val gapAnalyzer = GapAnalyzer(compensationEngine)
}
