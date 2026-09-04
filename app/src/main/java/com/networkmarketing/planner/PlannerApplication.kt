package com.networkmarketing.planner

import android.app.Application
import com.networkmarketing.planner.di.AppContainer

class PlannerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
