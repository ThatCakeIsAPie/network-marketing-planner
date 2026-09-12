package com.networkmarketing.planner.domain.ops

import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.LosGraph
import com.networkmarketing.planner.domain.canvas.TreeLayout
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlanProfile
import com.networkmarketing.planner.domain.model.StructureKind

/**
 * Pure, platform-independent edits over an [OrgSnapshot]. Every function returns a new
 * snapshot instead of mutating in place, so the server and the app can share the same
 * organization logic (the app previously kept equivalent logic in its Room repository).
 */
object SnapshotOps {

    fun addNode(
        snapshot: OrgSnapshot,
        kind: StructureKind,
        parentId: String?,
        name: String,
        personalPv: Double,
        bvPerPv: Double,
        partnerName: String = "",
        isCouple: Boolean = false,
        canvasX: Float? = null,
        canvasY: Float? = null,
        planProfileId: String? = null,
    ): Pair<OrgSnapshot, String> {
        val snap = snapshot.withNormalizedPlans()
        val memberId = SampleData.newId("member")
        val nodeId = SampleData.newId("node")
        val profileId = when (kind) {
            StructureKind.IDEAL -> planProfileId ?: snap.primaryPlanProfileId()
            StructureKind.CURRENT -> null
        }

        val parent = parentId?.let { snap.node(it) }
        val siblings = parentId?.let { snap.children(it).size } ?: 0
        val fallbackX = (parent?.canvasX ?: (80f + CanvasMetrics.NODE_RADIUS)) +
            siblings * (CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_X)
        val fallbackY = (parent?.canvasY ?: (80f + CanvasMetrics.NODE_RADIUS)) +
            CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_Y
        val pos = CanvasMetrics.snapPoint(canvasX ?: fallbackX, canvasY ?: fallbackY)

        val member = Member(
            id = memberId,
            name = name.ifBlank { "New partner" },
            partnerName = partnerName,
            isCouple = isCouple,
        )
        val node = OrgNode(
            id = nodeId,
            memberId = memberId,
            parentId = parentId,
            kind = kind,
            personalPv = personalPv,
            personalBv = personalPv * bvPerPv,
            canvasX = pos.first,
            canvasY = pos.second,
            planProfileId = profileId,
        )
        val updated = snap.copy(
            members = snap.members + member,
            nodes = snap.nodes + node,
        )
        return updated to nodeId
    }

    fun updatePerson(
        snapshot: OrgSnapshot,
        nodeId: String,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
        vcsPv: Double? = null,
    ): OrgSnapshot {
        val node = snapshot.node(nodeId) ?: return snapshot
        val isYou = snapshot.isYou(node)
        val members = snapshot.members.map { member ->
            if (member.id == node.memberId) {
                member.copy(
                    name = name.ifBlank { "Unnamed" },
                    partnerName = partnerName,
                    isCouple = isCouple,
                    notes = notes,
                    isYou = isYou,
                )
            } else {
                member
            }
        }
        val cappedVcs = vcsPv?.coerceIn(0.0, personalPv.coerceAtLeast(0.0))
        val nodes = snapshot.nodes.map {
            if (it.id == nodeId) {
                it.copy(personalPv = personalPv, personalBv = personalBv, vcsPv = cappedVcs)
            } else {
                it
            }
        }
        return snapshot.copy(members = members, nodes = nodes)
    }

    fun move(snapshot: OrgSnapshot, nodeId: String, x: Float, y: Float): OrgSnapshot {
        val snapped = CanvasMetrics.snapPoint(x, y)
        val nodes = snapshot.nodes.map {
            if (it.id == nodeId) it.copy(canvasX = snapped.first, canvasY = snapped.second) else it
        }
        return snapshot.copy(nodes = nodes)
    }

    /** Returns the new snapshot, or null when the reparent would be invalid (cycle / cross-kind / moving You). */
    fun setParent(snapshot: OrgSnapshot, childId: String, parentId: String?): OrgSnapshot? {
        if (!LosGraph.canSetParent(snapshot, childId, parentId)) return null
        val nodes = snapshot.nodes.map {
            if (it.id == childId) it.copy(parentId = parentId) else it
        }
        return snapshot.copy(nodes = nodes)
    }

    fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String): OrgSnapshot {
        val node = snapshot.node(nodeId) ?: return snapshot
        if (snapshot.isYou(node)) return snapshot
        val removeNodeIds = (listOf(nodeId) + snapshot.descendants(nodeId).map { it.id }).toSet()
        val removedMemberIds = removeNodeIds.mapNotNull { snapshot.node(it)?.memberId }.toSet()
        val remainingNodes = snapshot.nodes.filter { it.id !in removeNodeIds }
        val stillReferenced = remainingNodes.map { it.memberId }.toSet()
        val members = snapshot.members.filter { it.id !in removedMemberIds || it.id in stillReferenced }
        val claims = snapshot.planClaims.filter { (currentId, planId) ->
            currentId !in removeNodeIds && planId !in removeNodeIds
        }
        return snapshot.copy(members = members, nodes = remainingNodes, planClaims = claims)
    }

    fun applyLayout(
        snapshot: OrgSnapshot,
        kind: StructureKind,
        planProfileId: String? = null,
    ): OrgSnapshot {
        val profileId = if (kind == StructureKind.IDEAL) {
            planProfileId ?: snapshot.withNormalizedPlans().primaryPlanProfileId()
        } else {
            null
        }
        val placed = TreeLayout.applyPositions(snapshot, kind, profileId).associateBy { it.id }
        val nodes = snapshot.nodes.map { placed[it.id] ?: it }
        return snapshot.copy(nodes = nodes)
    }

    fun restoreSample(bvPerPv: Double): OrgSnapshot = SampleData.snapshot(bvPerPv)

    /** Replace the Ideal structure with a copy of Current (legacy single-profile). */
    fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double): OrgSnapshot {
        val snap = snapshot.withNormalizedPlans()
        return copyCurrentToPlan(snap, snap.primaryPlanProfileId(), bvPerPv)
    }

    /** Replace one plan profile's nodes with a topological clone of Current. */
    fun copyCurrentToPlan(
        snapshot: OrgSnapshot,
        planProfileId: String,
        bvPerPv: Double,
    ): OrgSnapshot {
        val snap = snapshot.withNormalizedPlans()
        val mapping = mutableMapOf<String, String>()
        val rebuilt = topological(snap.nodes(StructureKind.CURRENT)).map { src ->
            val newId = if (snap.isYou(src)) {
                youNodeIdForPlan(planProfileId)
            } else {
                SampleData.newId("node")
            }
            mapping[src.id] = newId
            src.copy(
                id = newId,
                parentId = src.parentId?.let { mapping[it] },
                kind = StructureKind.IDEAL,
                personalBv = src.personalPv * bvPerPv,
                planProfileId = planProfileId,
            )
        }
        val keep = snap.nodes.filter {
            it.kind == StructureKind.CURRENT ||
                (it.kind == StructureKind.IDEAL && it.effectivePlanProfileId() != planProfileId)
        }
        val removedPlanIds = snap.planNodes(planProfileId).map { it.id }.toSet()
        val claims = snap.planClaims.filter { (_, planId) -> planId !in removedPlanIds }
        return snap.copy(nodes = keep + rebuilt, planClaims = claims)
    }

    fun createPlanProfile(
        snapshot: OrgSnapshot,
        name: String,
        bvPerPv: Double = 3.43,
    ): Pair<OrgSnapshot, String> {
        val snap = snapshot.withNormalizedPlans()
        val id = SampleData.newId("plan")
        val profile = PlanProfile(id = id, name = name.ifBlank { "Plan" })
        val youMemberId = snap.members.firstOrNull { it.isYou }?.id ?: SampleData.YOU_ID
        val youNode = OrgNode(
            id = youNodeIdForPlan(id),
            memberId = youMemberId,
            parentId = null,
            kind = StructureKind.IDEAL,
            personalPv = 250.0,
            personalBv = 250.0 * bvPerPv,
            canvasX = 80f + CanvasMetrics.NODE_RADIUS,
            canvasY = 80f + CanvasMetrics.NODE_RADIUS,
            planProfileId = id,
        )
        val members = if (snap.members.any { it.id == youMemberId }) {
            snap.members
        } else {
            snap.members + Member(id = youMemberId, name = "You", isYou = true)
        }
        return snap.copy(
            members = members,
            nodes = snap.nodes + youNode,
            planProfiles = snap.planProfiles + profile,
        ) to id
    }

    fun renamePlanProfile(snapshot: OrgSnapshot, profileId: String, name: String): OrgSnapshot {
        val snap = snapshot.withNormalizedPlans()
        if (snap.planProfiles.none { it.id == profileId }) return snap
        return snap.copy(
            planProfiles = snap.planProfiles.map {
                if (it.id == profileId) it.copy(name = name.ifBlank { it.name }) else it
            },
        )
    }

    fun deletePlanProfile(snapshot: OrgSnapshot, profileId: String): OrgSnapshot {
        val snap = snapshot.withNormalizedPlans()
        if (snap.planProfiles.size <= 1) return snap
        if (snap.planProfiles.none { it.id == profileId }) return snap
        val removeIds = snap.planNodes(profileId).map { it.id }.toSet()
        val remainingNodes = snap.nodes.filter { it.id !in removeIds }
        val stillReferenced = remainingNodes.map { it.memberId }.toSet()
        val members = snap.members.filter { it.isYou || it.id in stillReferenced }
        val claims = snap.planClaims.filter { (_, planId) -> planId !in removeIds }
        return snap.copy(
            members = members,
            nodes = remainingNodes,
            planProfiles = snap.planProfiles.filter { it.id != profileId },
            planClaims = claims,
        )
    }

    /**
     * Claim a plan ghost slot with a current Map node.
     * One current node → one plan node; claiming again replaces the previous claim.
     */
    fun claimPlanSlot(
        snapshot: OrgSnapshot,
        currentNodeId: String,
        planNodeId: String,
    ): OrgSnapshot? {
        val snap = snapshot.withNormalizedPlans()
        val current = snap.node(currentNodeId) ?: return null
        val plan = snap.node(planNodeId) ?: return null
        if (current.kind != StructureKind.CURRENT) return null
        if (plan.kind != StructureKind.IDEAL) return null
        val without = snap.planClaims.filter { (c, p) -> c != currentNodeId && p != planNodeId }
        return snap.copy(planClaims = without + (currentNodeId to planNodeId))
    }

    fun unclaimPlanSlot(snapshot: OrgSnapshot, currentNodeId: String): OrgSnapshot {
        val snap = snapshot.withNormalizedPlans()
        if (currentNodeId !in snap.planClaims) return snap
        return snap.copy(planClaims = snap.planClaims - currentNodeId)
    }

    fun youNodeIdForPlan(planProfileId: String): String =
        if (planProfileId == PlanProfile.DEFAULT_ID) "n-you-ideal" else "n-you-$planProfileId"

    private fun topological(nodes: List<OrgNode>): List<OrgNode> {
        val byParent = nodes.groupBy { it.parentId }
        val ids = nodes.map { it.id }.toSet()
        val result = mutableListOf<OrgNode>()
        val roots = nodes.filter { it.parentId == null || it.parentId !in ids }
        val queue = ArrayDeque(roots)
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!seen.add(n.id)) continue
            result += n
            queue.addAll(byParent[n.id].orEmpty())
        }
        return result
    }
}
