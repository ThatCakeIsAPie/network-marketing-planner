package com.networkmarketing.planner.domain.ops

import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.LosGraph
import com.networkmarketing.planner.domain.canvas.TreeLayout
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
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
    ): Pair<OrgSnapshot, String> {
        val memberId = SampleData.newId("member")
        val nodeId = SampleData.newId("node")

        val parent = parentId?.let { snapshot.node(it) }
        val siblings = parentId?.let { snapshot.children(it).size } ?: 0
        val fallbackX = (parent?.canvasX ?: 48f) + siblings * (CanvasMetrics.NODE_WIDTH + CanvasMetrics.GAP_X)
        val fallbackY = (parent?.canvasY ?: 48f) + CanvasMetrics.NODE_HEIGHT + CanvasMetrics.GAP_Y
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
        )
        val updated = snapshot.copy(
            members = snapshot.members + member,
            nodes = snapshot.nodes + node,
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
        val nodes = snapshot.nodes.map {
            if (it.id == nodeId) it.copy(personalPv = personalPv, personalBv = personalBv) else it
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
        // Only drop members that are no longer referenced by any remaining node.
        val stillReferenced = remainingNodes.map { it.memberId }.toSet()
        val members = snapshot.members.filter { it.id !in removedMemberIds || it.id in stillReferenced }
        return snapshot.copy(members = members, nodes = remainingNodes)
    }

    fun applyLayout(snapshot: OrgSnapshot, kind: StructureKind): OrgSnapshot {
        val placed = TreeLayout.applyPositions(snapshot, kind).associateBy { it.id }
        val nodes = snapshot.nodes.map { placed[it.id] ?: it }
        return snapshot.copy(nodes = nodes)
    }

    fun restoreSample(bvPerPv: Double): OrgSnapshot = SampleData.snapshot(bvPerPv)

    /** Replace the ideal structure with a copy of the current one (same people, IDEAL kind). */
    fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double): OrgSnapshot {
        val mapping = mutableMapOf<String, String>()
        val rebuilt = topological(snapshot.nodes(StructureKind.CURRENT)).map { src ->
            val newId = if (snapshot.isYou(src)) "n-you-ideal" else SampleData.newId("node")
            mapping[src.id] = newId
            src.copy(
                id = newId,
                parentId = src.parentId?.let { mapping[it] },
                kind = StructureKind.IDEAL,
                personalBv = src.personalPv * bvPerPv,
            )
        }
        return snapshot.copy(nodes = snapshot.nodes(StructureKind.CURRENT) + rebuilt)
    }

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
