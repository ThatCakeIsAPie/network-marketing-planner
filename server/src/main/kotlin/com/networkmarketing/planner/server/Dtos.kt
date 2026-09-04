package com.networkmarketing.planner.server

import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.model.PlannerState
import com.networkmarketing.planner.domain.model.StructureKind
import kotlinx.serialization.Serializable

@Serializable
data class AddNodeRequest(
    val kind: StructureKind = StructureKind.CURRENT,
    val parentId: String? = null,
    val name: String = "New partner",
    val personalPv: Double = 100.0,
    val partnerName: String = "",
    val isCouple: Boolean = false,
)

@Serializable
data class UpdateNodeRequest(
    val name: String,
    val partnerName: String = "",
    val isCouple: Boolean = false,
    val notes: String = "",
    val personalPv: Double,
    val canvasX: Float? = null,
    val canvasY: Float? = null,
)

@Serializable
data class MoveRequest(val x: Float, val y: Float)

@Serializable
data class ReparentRequest(val parentId: String? = null)

@Serializable
data class AddNodeResponse(val nodeId: String, val state: PlannerState)

/** Payout for a whole structure plus the per-node breakdowns the map/plan views annotate with. */
@Serializable
data class CalculatorResponse(
    val kind: StructureKind,
    val root: PayoutBreakdown?,
    val perNode: Map<String, PayoutBreakdown>,
)

@Serializable
data class ErrorResponse(val error: String)
