package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable

/**
 * The full planner document shared across clients (web, phone browser, Android app).
 * The server is the source of truth for this state; clients read and write it.
 */
@Serializable
data class PlannerState(
    val snapshot: OrgSnapshot = OrgSnapshot(),
    val goals: UserGoals = UserGoals(),
    val settings: PlannerSettings = PlannerSettings(),
)
