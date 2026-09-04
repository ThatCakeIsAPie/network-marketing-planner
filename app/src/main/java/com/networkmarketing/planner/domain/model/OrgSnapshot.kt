package com.networkmarketing.planner.domain.model

/**
 * In-memory view of members plus current/ideal tree nodes.
 * Group volume is personal volume plus all descendants in the same structure.
 */
data class OrgSnapshot(
    val members: List<Member> = emptyList(),
    val nodes: List<OrgNode> = emptyList(),
) {
    private val membersById: Map<String, Member> = members.associateBy { it.id }
    private val nodesById: Map<String, OrgNode> = nodes.associateBy { it.id }
    private val childrenByParent: Map<String?, List<OrgNode>> =
        nodes.groupBy { it.parentId }

    fun member(id: String): Member? = membersById[id]

    fun node(id: String): OrgNode? = nodesById[id]

    fun nodes(kind: StructureKind): List<OrgNode> = nodes.filter { it.kind == kind }

    fun root(kind: StructureKind): OrgNode? =
        nodes(kind).firstOrNull { it.parentId == null }

    fun children(nodeId: String): List<OrgNode> =
        childrenByParent[nodeId].orEmpty().sortedBy { member(it.memberId)?.name.orEmpty() }

    fun displayName(node: OrgNode): String = member(node.memberId)?.name ?: "Unknown"

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
```
