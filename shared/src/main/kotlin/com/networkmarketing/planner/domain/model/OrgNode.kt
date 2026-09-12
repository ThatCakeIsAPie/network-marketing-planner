package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class StructureKind {
    CURRENT,
    IDEAL,
}

/**
 * One placement of a [Member] in either the current or ideal organization.
 *
 * Volume is monthly personal PV/BV. Line of Sponsorship is [parentId]
 * (upline). [canvasX] / [canvasY] are persisted world-space positions in dp
 * for the Map / Plan node canvas.
 *
 * [vcsPv] is Verified Customer Sales PV for Rule 4.12 / baseline. Null means
 * use Goals [PlannerSettings.vcsPercent] × [personalPv] (legacy / default).
 */
@Serializable
data class OrgNode(
    val id: String,
    val memberId: String,
    val parentId: String?,
    val kind: StructureKind,
    val personalPv: Double,
    val personalBv: Double,
    val canvasX: Float = 0f,
    val canvasY: Float = 0f,
    /** Verified Customer Sales PV. Null = Goals [PlannerSettings.vcsPercent] × personal PV. */
    val vcsPv: Double? = null,
) {
    fun effectiveVcsPv(settings: PlannerSettings): Double {
        val cap = personalPv.coerceAtLeast(0.0)
        return when {
            vcsPv != null -> vcsPv.coerceIn(0.0, cap)
            else -> cap * settings.vcsPercent.coerceIn(0.0, 1.0)
        }
    }

    fun effectiveVcsPercent(settings: PlannerSettings): Double {
        if (personalPv <= 1e-9) return settings.vcsPercent.coerceIn(0.0, 1.0)
        return (effectiveVcsPv(settings) / personalPv).coerceIn(0.0, 1.0)
    }

    fun meetsVcs60(settings: PlannerSettings, minRatio: Double = 0.60): Boolean =
        effectiveVcsPercent(settings) + 1e-9 >= minRatio
}
