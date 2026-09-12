package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable

/**
 * A named planned organization structure (Plan tab). Replaces the single global Ideal tree
 * with multiple parallel targets (e.g. Emerald, Ruby, next pin).
 *
 * Nodes with [StructureKind.IDEAL] belong to exactly one profile via [OrgNode.planProfileId].
 */
@Serializable
data class PlanProfile(
    val id: String,
    val name: String,
) {
    companion object {
        const val DEFAULT_ID = "plan-ideal"
        const val DEFAULT_NAME = "Ideal"

        fun default(): PlanProfile = PlanProfile(id = DEFAULT_ID, name = DEFAULT_NAME)
    }
}
