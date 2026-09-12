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
    suspend fun replaceSnapshot(snapshot: OrgSnapshot)
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
        planProfileId: String? = null,
    ): String
    suspend fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
        vcsPv: Double? = null,
    )
    suspend fun updatePosition(node: OrgNode, canvasX: Float, canvasY: Float)
    suspend fun setParent(snapshot: OrgSnapshot, childId: String, parentId: String?): Boolean
    suspend fun applyLayout(snapshot: OrgSnapshot, kind: StructureKind, planProfileId: String? = null)
    suspend fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String)
    suspend fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double)
    suspend fun copyCurrentToPlan(snapshot: OrgSnapshot, planProfileId: String, bvPerPv: Double)
    suspend fun createPlanProfile(snapshot: OrgSnapshot, name: String, bvPerPv: Double): String
    suspend fun renamePlanProfile(snapshot: OrgSnapshot, profileId: String, name: String)
    suspend fun deletePlanProfile(snapshot: OrgSnapshot, profileId: String)
    suspend fun claimPlanSlot(snapshot: OrgSnapshot, currentNodeId: String, planNodeId: String): Boolean
    suspend fun unclaimPlanSlot(snapshot: OrgSnapshot, currentNodeId: String)
}
