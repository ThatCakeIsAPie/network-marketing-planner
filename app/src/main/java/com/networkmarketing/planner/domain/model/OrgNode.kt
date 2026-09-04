package com.networkmarketing.planner.domain.model

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
 */
data class OrgNode(
    val id: String,
    val memberId: String,
    val parentId: String?,
    val kind: StructureKind,
    val personalPv: Double,
    val personalBv: Double,
    val canvasX: Float = 0f,
    val canvasY: Float = 0f,
)
