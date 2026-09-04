package com.networkmarketing.planner.domain.canvas

import com.networkmarketing.planner.domain.model.OrgNode

import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind

/**
 * Simple tidy-tree layout so sample (and auto-layout) orgs land as a navigable graph.
 */
object TreeLayout {
    fun applyPositions(snapshot: OrgSnapshot, kind: StructureKind): List<OrgNode> {
        val ofKind = snapshot.nodes(kind)
        if (ofKind.isEmpty()) return emptyList()
        val positions = positions(snapshot, kind)
        return ofKind.map { node ->
            val pos = positions[node.id] ?: (node.canvasX to node.canvasY)
            val snapped = CanvasMetrics.snapPoint(pos.first, pos.second)
            node.copy(canvasX = snapped.first, canvasY = snapped.second)
        }
    }

    fun positions(snapshot: OrgSnapshot, kind: StructureKind): Map<String, Pair<Float, Float>> {
        val ofKind = snapshot.nodes(kind)
        val root = snapshot.root(kind)
        val widths = mutableMapOf<String, Float>()
        fun widthOf(id: String): Float {
            widths[id]?.let { return it }
            val kids = snapshot.children(id)
            val w = if (kids.isEmpty()) {
                CanvasMetrics.NODE_WIDTH + CanvasMetrics.GAP_X
            } else {
                kids.sumOf { widthOf(it.id).toDouble() }.toFloat()
                    .coerceAtLeast(CanvasMetrics.NODE_WIDTH + CanvasMetrics.GAP_X)
            }
            widths[id] = w
            return w
        }
        val pos = mutableMapOf<String, Pair<Float, Float>>()
        if (root != null) {
            widthOf(root.id)
            fun place(id: String, left: Float, y: Float) {
                val member = snapshot.member(snapshot.node(id)?.memberId.orEmpty())
                val h = CanvasMetrics.nodeHeight(member?.isCouple == true)
                val w = widths.getValue(id)
                pos[id] = (left + w / 2f - CanvasMetrics.NODE_WIDTH / 2f) to y
                var childLeft = left
                for (child in snapshot.children(id)) {
                    val cw = widthOf(child.id)
                    place(child.id, childLeft, y + h + CanvasMetrics.GAP_Y)
                    childLeft += cw
                }
            }
            place(root.id, 48f, 48f)
        }
        var extraX = 48f
        val extraY = (pos.values.maxOfOrNull { it.second } ?: 48f) +
            CanvasMetrics.NODE_HEIGHT_COUPLE + CanvasMetrics.GAP_Y * 2
        ofKind.filter { it.id !in pos }.forEach { node ->
            pos[node.id] = extraX to extraY
            extraX += CanvasMetrics.NODE_WIDTH + CanvasMetrics.GAP_X
        }
        return pos
    }
}
