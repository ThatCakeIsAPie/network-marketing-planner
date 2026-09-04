package com.networkmarketing.planner.domain.canvas

import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LosGraphTest {

    private val snapshot = OrgSnapshot(
        members = listOf(
            Member("you", "You", isYou = true),
            Member("a", "Alex", partnerName = "Chris", isCouple = true),
            Member("b", "Blair"),
            Member("c", "Casey"),
        ),
        nodes = listOf(
            OrgNode("n-you", "you", null, StructureKind.CURRENT, 100.0, 343.0, 100f, 20f),
            OrgNode("n-a", "a", "n-you", StructureKind.CURRENT, 80.0, 274.0, 20f, 200f),
            OrgNode("n-b", "b", "n-you", StructureKind.CURRENT, 50.0, 171.0, 260f, 200f),
            OrgNode("n-c", "c", "n-a", StructureKind.CURRENT, 40.0, 137.0, 20f, 380f),
        ),
    )

    @Test
    fun snapAlignsToTwentyDpGrid() {
        assertEquals(20f, CanvasMetrics.snap(18f), 0.0f)
        assertEquals(40f, CanvasMetrics.snap(31f), 0.0f)
        val pt = CanvasMetrics.snapPoint(33f, 47f)
        assertEquals(40f, pt.first, 0.0f)
        assertEquals(40f, pt.second, 0.0f)
    }

    @Test
    fun midXElbowIsHorizontalThenVerticalThenHorizontal() {
        val pts = ElbowPath.midX(WorldPoint(0f, 0f), WorldPoint(100f, 80f))
        assertEquals(4, pts.size)
        assertEquals(50f, pts[1].x, 0.0f)
        assertEquals(0f, pts[1].y, 0.0f)
        assertEquals(50f, pts[2].x, 0.0f)
        assertEquals(80f, pts[2].y, 0.0f)
    }

    @Test
    fun cycleDetectionBlocksParentingAnAncestor() {
        assertTrue(LosGraph.wouldCreateCycle(snapshot, "n-you", "n-c"))
        assertFalse(LosGraph.wouldCreateCycle(snapshot, "n-c", "n-b"))
        assertFalse(LosGraph.canSetParent(snapshot, "n-you", "n-a"))
    }

    @Test
    fun downlineToUplineSetsChildParent() {
        val edit = LosGraph.resolveConnection(
            snapshot,
            NodePort("n-b", DockKind.DOWNLINE, 1),
            NodePort("n-c", DockKind.UPLINE, 0),
        )
        requireNotNull(edit)
        assertEquals("n-c", edit.childId)
        assertEquals("n-b", edit.newParentId)
    }

    @Test
    fun occupiedDownlinePortDetachesPreviousChildOnRewire() {
        val edit = LosGraph.resolveConnection(
            snapshot,
            NodePort("n-you", DockKind.DOWNLINE, 0),
            NodePort("n-c", DockKind.UPLINE, 0),
        )
        requireNotNull(edit)
        assertEquals("n-c", edit.childId)
        assertEquals("n-you", edit.newParentId)
        assertEquals("n-a", edit.detachId)
    }

    @Test
    fun coupleDisplayNameJoinsBothPeople() {
        assertEquals("Alex & Chris", snapshot.displayName(snapshot.node("n-a")!!))
        assertEquals("You", snapshot.displayName(snapshot.node("n-you")!!))
    }

    @Test
    fun rootPrefersYouWhenOtherNodesAreUnattached() {
        val extra = snapshot.copy(
            members = snapshot.members + Member("float", "Floater"),
            nodes = snapshot.nodes + OrgNode("n-float", "float", null, StructureKind.CURRENT, 10.0, 34.0),
        )
        assertTrue(extra.isYou(extra.root(StructureKind.CURRENT)!!))
    }

    @Test
    fun sampleOrgHasDistinctSnappedPositions() {
        val seeded = SampleData.snapshot(3.43)
        val current = seeded.nodes(StructureKind.CURRENT)
        val xs = current.map { it.canvasX to it.canvasY }
        assertTrue(current.size > 5)
        assertEquals(xs.size, xs.toSet().size)
        current.forEach { node ->
            assertEquals(0f, node.canvasX % 20f, 0.01f)
            assertEquals(0f, node.canvasY % 20f, 0.01f)
        }
        assertNotEquals(0f, current.maxOf { it.canvasY })
    }
}
