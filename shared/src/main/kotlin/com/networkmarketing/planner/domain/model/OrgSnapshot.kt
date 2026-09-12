package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * In-memory view of members plus current/plan tree nodes.
 * Group volume is personal volume plus all descendants in the same structure.
 *
 * Ideal nodes are partitioned by [PlanProfile] ([OrgNode.planProfileId]).
 * [planClaims] maps a current (Map) node id → a plan-profile node id when the
 * user claims a ghost slot on the Map.
 */
@Serializable
data class OrgSnapshot(
    val members: List<Member> = emptyList(),
    val nodes: List<OrgNode> = emptyList(),
    val planProfiles: List<PlanProfile> = emptyList(),
    /** currentNodeId → planNodeId */
    val planClaims: Map<String, String> = emptyMap(),
) {
    @Transient
    private val membersById: Map<String, Member> = members.associateBy { it.id }

    @Transient
    private val nodesById: Map<String, OrgNode> = nodes.associateBy { it.id }

    @Transient
    private val childrenByParent: Map<String?, List<OrgNode>> =
        nodes.groupBy { it.parentId }

    fun member(id: String): Member? = membersById[id]

    fun node(id: String): OrgNode? = nodesById[id]

    fun primaryPlanProfileId(): String =
        planProfiles.firstOrNull()?.id ?: PlanProfile.DEFAULT_ID

    fun planProfile(id: String): PlanProfile? = planProfiles.firstOrNull { it.id == id }

    /** Ideal nodes for one plan profile (after normalization, [planProfileId] is set). */
    fun planNodes(profileId: String): List<OrgNode> =
        nodes.filter {
            it.kind == StructureKind.IDEAL && it.effectivePlanProfileId() == profileId
        }

    fun nodes(kind: StructureKind): List<OrgNode> = nodes.filter { it.kind == kind }

    fun nodes(kind: StructureKind, planProfileId: String?): List<OrgNode> =
        when {
            kind == StructureKind.IDEAL && planProfileId != null -> planNodes(planProfileId)
            else -> nodes(kind)
        }

    fun root(kind: StructureKind): OrgNode? = root(kind, planProfileId = null)

    fun root(kind: StructureKind, planProfileId: String?): OrgNode? {
        val ofKind = nodes(kind, planProfileId)
        return ofKind.firstOrNull { isYou(it) } ?: ofKind.firstOrNull { it.parentId == null }
    }

    fun children(nodeId: String): List<OrgNode> =
        childrenByParent[nodeId].orEmpty().sortedWith(
            compareBy<OrgNode> { it.canvasX }.thenBy { member(it.memberId)?.name.orEmpty() },
        )

    fun displayName(node: OrgNode): String = member(node.memberId)?.displayName() ?: "Unknown"

    fun isCouple(node: OrgNode): Boolean = member(node.memberId)?.isCouple == true

    fun isYou(node: OrgNode): Boolean = member(node.memberId)?.isYou == true

    fun depth(node: OrgNode): Int {
        var d = 0
        var current = node
        val guard = mutableSetOf<String>()
        while (current.parentId != null && guard.add(current.id)) {
            current = nodesById[current.parentId] ?: break
            d++
        }
        return d
    }

    fun descendants(nodeId: String): List<OrgNode> {
        val result = mutableListOf<OrgNode>()
        val stack = ArrayDeque(children(nodeId))
        while (stack.isNotEmpty()) {
            val next = stack.removeFirst()
            result += next
            stack.addAll(children(next.id))
        }
        return result
    }

    fun groupVolume(nodeId: String): Volume {
        val node = nodesById[nodeId] ?: return Volume.ZERO
        var total = Volume(node.personalPv, node.personalBv)
        for (child in descendants(nodeId)) {
            total += Volume(child.personalPv, child.personalBv)
        }
        return total
    }

    fun personalVolume(node: OrgNode): Volume = Volume(node.personalPv, node.personalBv)

    fun frontline(nodeId: String): List<OrgNode> = children(nodeId)

    fun nodeCount(kind: StructureKind): Int = nodes(kind).size

    fun nodeCount(kind: StructureKind, planProfileId: String?): Int =
        nodes(kind, planProfileId).size

    fun generations(kind: StructureKind): List<List<OrgNode>> {
        val root = root(kind) ?: return emptyList()
        val rows = mutableListOf<List<OrgNode>>()
        var layer = listOf(root)
        val seen = mutableSetOf<String>()
        while (layer.isNotEmpty()) {
            rows += layer
            layer.forEach { seen += it.id }
            layer = layer.flatMap { children(it.id) }.filter { it.id !in seen }
        }
        return rows
    }

    /** Plan node claimed by this current node, if any. */
    fun claimedPlanNode(currentNodeId: String): OrgNode? =
        planClaims[currentNodeId]?.let { nodesById[it] }

    /** Current node that claimed this plan slot, if any. */
    fun claimerOfPlanNode(planNodeId: String): OrgNode? {
        val currentId = planClaims.entries.firstOrNull { it.value == planNodeId }?.key ?: return null
        return nodesById[currentId]
    }

    /**
     * Ensures at least one plan profile exists and Ideal nodes carry a profile id.
     * Legacy snapshots (Ideal-only, no profiles) become a single "Ideal" profile.
     */
    fun withNormalizedPlans(): OrgSnapshot {
        val profiles = if (planProfiles.isEmpty()) {
            listOf(PlanProfile.default())
        } else {
            planProfiles
        }
        val defaultId = profiles.first().id
        val normalizedNodes = nodes.map { node ->
            when {
                node.kind == StructureKind.IDEAL && node.planProfileId == null ->
                    node.copy(planProfileId = defaultId)
                node.kind == StructureKind.CURRENT ->
                    node.copy(planProfileId = null)
                else -> node
            }
        }
        val ids = normalizedNodes.map { it.id }.toSet()
        val cleanedClaims = planClaims.filter { (currentId, planId) ->
            currentId in ids && planId in ids &&
                normalizedNodes.find { it.id == currentId }?.kind == StructureKind.CURRENT &&
                normalizedNodes.find { it.id == planId }?.kind == StructureKind.IDEAL
        }
        return copy(
            nodes = normalizedNodes,
            planProfiles = profiles,
            planClaims = cleanedClaims,
        )
    }
}
