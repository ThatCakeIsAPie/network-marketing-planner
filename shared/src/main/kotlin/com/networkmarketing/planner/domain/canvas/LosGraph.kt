package com.networkmarketing.planner.domain.canvas

import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot

enum class DockKind {
    UPLINE,
    DOWNLINE,
}

data class NodePort(
    val nodeId: String,
    val kind: DockKind,
    val index: Int = 0,
)

/**
 * Line-of-Sponsorship helpers for the node canvas.
 *
 * Faleth: one edge per port; reconnect replaces. Here that means:
 * - Top (upline) port: at most one sponsor.
 * - Bottom ports: one per existing frontline plus one vacant port to add/rewire.
 */
object LosGraph {
    fun wouldCreateCycle(snapshot: OrgSnapshot, childId: String, newParentId: String?): Boolean {
        if (newParentId == null) return false
        if (childId == newParentId) return true
        var current: String? = newParentId
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            if (current == childId) return true
            current = snapshot.node(current)?.parentId
        }
        return false
    }

    fun canSetParent(snapshot: OrgSnapshot, childId: String, newParentId: String?): Boolean {
        val child = snapshot.node(childId) ?: return false
        if (snapshot.isYou(child) && newParentId != null) return false
        if (newParentId != null) {
            val parent = snapshot.node(newParentId) ?: return false
            if (parent.kind != child.kind) return false
        }
        return !wouldCreateCycle(snapshot, childId, newParentId)
    }

    fun downlineChild(snapshot: OrgSnapshot, parentId: String, portIndex: Int): OrgNode? =
        snapshot.children(parentId).getOrNull(portIndex)

    fun downlinePortCount(snapshot: OrgSnapshot, parentId: String): Int =
        CanvasMetrics.downlinePortCount(snapshot.children(parentId).size)

    data class ConnectionEdit(
        val childId: String,
        val newParentId: String?,
        val detachId: String? = null,
    )

    fun resolveConnection(
        snapshot: OrgSnapshot,
        from: NodePort,
        to: NodePort,
    ): ConnectionEdit? {
        if (from.nodeId == to.nodeId) return null
        val pair = when {
            from.kind == DockKind.DOWNLINE && to.kind == DockKind.UPLINE ->
                to.nodeId to from.nodeId
            from.kind == DockKind.UPLINE && to.kind == DockKind.DOWNLINE ->
                from.nodeId to to.nodeId
            else -> return null
        }
        val childId = pair.first
        val parentId = pair.second
        if (!canSetParent(snapshot, childId, parentId)) return null
        val occupied = when {
            from.kind == DockKind.DOWNLINE -> downlineChild(snapshot, from.nodeId, from.index)
            to.kind == DockKind.DOWNLINE -> downlineChild(snapshot, to.nodeId, to.index)
            else -> null
        }
        val detach = occupied?.id?.takeIf { it != childId }
        return ConnectionEdit(childId = childId, newParentId = parentId, detachId = detach)
    }

    fun resolveDropOnEmpty(snapshot: OrgSnapshot, from: NodePort): ConnectionEdit? {
        return when (from.kind) {
            DockKind.UPLINE -> {
                if (!canSetParent(snapshot, from.nodeId, null)) null
                else ConnectionEdit(childId = from.nodeId, newParentId = null)
            }
            DockKind.DOWNLINE -> {
                val existing = downlineChild(snapshot, from.nodeId, from.index) ?: return null
                if (!canSetParent(snapshot, existing.id, null)) null
                else ConnectionEdit(childId = existing.id, newParentId = null)
            }
        }
    }
}
