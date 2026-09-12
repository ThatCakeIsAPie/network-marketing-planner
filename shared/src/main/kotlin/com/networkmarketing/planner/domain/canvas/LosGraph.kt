package com.networkmarketing.planner.domain.canvas

import com.networkmarketing.planner.domain.model.OrgSnapshot

/**
 * Line-of-Sponsorship helpers for the node canvas.
 *
 * Graph UX: drag a node onto another to make it downline; dwell on empty to detach.
 * Port/dock APIs remain for older tests but are unused by the circle canvas.
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

    data class ConnectionEdit(
        val childId: String,
        val newParentId: String?,
        val detachId: String? = null,
    )

    /** Make [childId] a downline of [newParentId] (or detach when null). */
    fun resolveReparent(
        snapshot: OrgSnapshot,
        childId: String,
        newParentId: String?,
    ): ConnectionEdit? {
        if (!canSetParent(snapshot, childId, newParentId)) return null
        return ConnectionEdit(childId = childId, newParentId = newParentId)
    }

    fun resolveDetach(snapshot: OrgSnapshot, childId: String): ConnectionEdit? =
        resolveReparent(snapshot, childId, null)

    @Deprecated("Dock ports removed; use resolveReparent")
    fun downlineChild(snapshot: OrgSnapshot, parentId: String, portIndex: Int) =
        snapshot.children(parentId).getOrNull(portIndex)

    @Deprecated("Dock ports removed")
    fun downlinePortCount(snapshot: OrgSnapshot, parentId: String): Int =
        snapshot.children(parentId).size + 1

    @Deprecated("Dock ports removed; use resolveReparent")
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

    @Deprecated("Dock ports removed; use resolveDetach")
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

enum class DockKind {
    UPLINE,
    DOWNLINE,
}

data class NodePort(
    val nodeId: String,
    val kind: DockKind,
    val index: Int = 0,
)
