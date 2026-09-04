package com.networkmarketing.planner.data.repository

import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import kotlinx.coroutines.flow.Flow

/**
 * The data surface the UI depends on. Two implementations exist:
 *  - [PlannerRepository] keeps everything on-device in Room (offline).
 *  - [com.networkmarketing.planner.data.remote.RemotePlannerRepository] reads and writes
 *    the shared server so the app, phone browser, and desktop browser see the same data.
 */
interface PlannerStore {
    val snapshot: Flow<OrgSnapshot>
    val prefs: Flow<Pair<UserGoals, PlannerSettings>>

    suspend fun ensureSeeded()
    suspend fun restoreSampleData()
    suspend fun saveGoals(goals: UserGoals)
    suspend fun saveSettings(settings: PlannerSettings)
    suspend fun addNode(
        kind: StructureKind,
        canvasX: Float,
        canvasY: Float,
        parentId: String?,
        name: String,
        personalPv: Double,
        bvPerPv: Double,
        partnerName: String = "",
        isCouple: Boolean = false,
    ): String
    suspend fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
    )
    suspend fun updatePosition(node: OrgNode, canvasX: Float, canvasY: Float)
    suspend fun setParent(snapshot: OrgSnapshot, childId: String, parentId: String?): Boolean
    suspend fun applyLayout(snapshot: OrgSnapshot, kind: StructureKind)
    suspend fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String)
    suspend fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double)
}
