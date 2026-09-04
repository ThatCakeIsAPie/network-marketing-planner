package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * In-memory view of members plus current/ideal tree nodes.
 * Group volume is personal volume plus all descendants in the same structure.
 */
@Serializable
data class OrgSnapshot(
    val members: List<Member> = emptyList(),
    val nodes: List<OrgNode> = emptyList(),
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

    fun nodes(kind: StructureKind): List<OrgNode> = nodes.filter { it.kind == kind }

    fun root(kind: StructureKind): OrgNode? {
        val ofKind = nodes(kind)
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
}
