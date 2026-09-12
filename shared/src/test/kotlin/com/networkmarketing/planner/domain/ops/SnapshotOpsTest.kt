package com.networkmarketing.planner.domain.ops

import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.model.PlanProfile
import com.networkmarketing.planner.domain.model.StructureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotOpsTest {

    @Test
    fun sampleHasDefaultPlanProfile() {
        val snap = SampleData.snapshot(3.43)
        assertEquals(1, snap.planProfiles.size)
        assertEquals(PlanProfile.DEFAULT_ID, snap.planProfiles.first().id)
        assertTrue(snap.planNodes(PlanProfile.DEFAULT_ID).isNotEmpty())
        assertTrue(snap.planNodes(PlanProfile.DEFAULT_ID).all { it.planProfileId == PlanProfile.DEFAULT_ID })
    }

    @Test
    fun normalizeMigratesLegacyIdealWithoutProfiles() {
        val seeded = SampleData.snapshot(3.43)
        val legacy = seeded.copy(
            planProfiles = emptyList(),
            nodes = seeded.nodes.map { if (it.kind == StructureKind.IDEAL) it.copy(planProfileId = null) else it },
            planClaims = emptyMap(),
        )
        val normalized = legacy.withNormalizedPlans()
        assertEquals(listOf(PlanProfile.default()), normalized.planProfiles)
        assertTrue(normalized.nodes.filter { it.kind == StructureKind.IDEAL }
            .all { it.planProfileId == PlanProfile.DEFAULT_ID })
    }

    @Test
    fun createAndRenamePlanProfile() {
        val snap = SampleData.snapshot(3.43)
        val (withPlan, id) = SnapshotOps.createPlanProfile(snap, "Emerald", 3.43)
        assertEquals(2, withPlan.planProfiles.size)
        assertNotNull(withPlan.root(StructureKind.IDEAL, id))
        val renamed = SnapshotOps.renamePlanProfile(withPlan, id, "Ruby track")
        assertEquals("Ruby track", renamed.planProfile(id)?.name)
    }

    @Test
    fun copyCurrentToPlanReplacesOnlyThatProfile() {
        val snap = SampleData.snapshot(3.43)
        val (withPlan, id) = SnapshotOps.createPlanProfile(snap, "Next pin", 3.43)
        val copied = SnapshotOps.copyCurrentToPlan(withPlan, id, 3.43)
        assertEquals(
            snap.nodes(StructureKind.CURRENT).size,
            copied.planNodes(id).size,
        )
        // Default Ideal sample legs remain.
        assertEquals(
            snap.planNodes(PlanProfile.DEFAULT_ID).size,
            copied.planNodes(PlanProfile.DEFAULT_ID).size,
        )
    }

    @Test
    fun claimAndUnclaimPlanSlot() {
        val snap = SampleData.snapshot(3.43)
        val currentId = snap.nodes(StructureKind.CURRENT).first { !snap.isYou(it) }.id
        val planId = snap.planNodes(PlanProfile.DEFAULT_ID).first { !snap.isYou(it) }.id
        val claimed = SnapshotOps.claimPlanSlot(snap, currentId, planId)
        assertNotNull(claimed)
        assertEquals(planId, claimed!!.planClaims[currentId])
        val cleared = SnapshotOps.unclaimPlanSlot(claimed, currentId)
        assertNull(cleared.planClaims[currentId])
    }

    @Test
    fun deletePlanProfileKeepsAtLeastOne() {
        val snap = SampleData.snapshot(3.43)
        val alone = SnapshotOps.deletePlanProfile(snap, PlanProfile.DEFAULT_ID)
        assertEquals(1, alone.planProfiles.size)
        val (withPlan, id) = SnapshotOps.createPlanProfile(snap, "Extra", 3.43)
        val after = SnapshotOps.deletePlanProfile(withPlan, id)
        assertEquals(1, after.planProfiles.size)
        assertEquals(PlanProfile.DEFAULT_ID, after.primaryPlanProfileId())
    }
}
