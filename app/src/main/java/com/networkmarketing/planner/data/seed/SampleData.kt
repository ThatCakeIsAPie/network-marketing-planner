package com.networkmarketing.planner.data.seed

import com.networkmarketing.planner.domain.canvas.TreeLayout
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind
import java.util.UUID

/**
 * Demo organization so the app is useful on first launch.
 *
 * Current map is a developing team (no 25% legs; Group PV in the low 1,000s).
 * Ideal map is a six-leg Diamond-track snapshot: six 25% frontline, two with 25% depth.
 * Both maps include canvas positions so Map/Plan open as a navigable node graph.
 */
object SampleData {
    const val YOU_ID = "member-you"

    fun snapshot(bvPerPv: Double): OrgSnapshot {
        val you = Member(id = YOU_ID, name = "You", notes = "Root of both maps", isYou = true)
        val currentPeople = listOf(
            Member(
                id = "m-alex",
                name = "Alex Rivera",
                notes = "Strongest current leg",
                partnerName = "Chris Rivera",
                isCouple = true,
            ),
            Member("m-jordan", "Jordan Lee"),
            Member("m-sam", "Sam Patel"),
            Member("m-casey", "Casey Kim"),
            Member("m-morgan", "Morgan Chen"),
            Member("m-riley", "Riley Brooks"),
            Member("m-avery", "Avery Diaz"),
            Member("m-taylor", "Taylor Nguyen"),
            Member("m-quinn", "Quinn Foster"),
            Member("m-jamie", "Jamie Okonkwo"),
            Member("m-drew", "Drew Hassan"),
            Member("m-blair", "Blair Santos"),
            Member("m-priya", "Priya Shah"),
        )
        val idealPeople = listOf(
            Member("m-leg1", "Leg 1 lead"),
            Member("m-leg2", "Leg 2 lead"),
            Member("m-leg3", "Leg 3 lead"),
            Member("m-leg4", "Leg 4 lead"),
            Member("m-leg5", "Leg 5 lead"),
            Member("m-leg6", "Leg 6 lead"),
            Member("m-depth-a", "Depth builder A"),
            Member("m-depth-b", "Depth builder B"),
        )
        val members = listOf(you) + currentPeople + idealPeople

        val currentRoot = node("n-you-current", YOU_ID, null, StructureKind.CURRENT, 220.0, bvPerPv)
        val idealRoot = node("n-you-ideal", YOU_ID, null, StructureKind.IDEAL, 250.0, bvPerPv)

        val currentNodes = listOf(
            currentRoot,
            node("n-alex", "m-alex", currentRoot.id, StructureKind.CURRENT, 180.0, bvPerPv),
            node("n-jordan", "m-jordan", "n-alex", StructureKind.CURRENT, 80.0, bvPerPv),
            node("n-sam", "m-sam", "n-alex", StructureKind.CURRENT, 95.0, bvPerPv),
            node("n-casey", "m-casey", "n-alex", StructureKind.CURRENT, 70.0, bvPerPv),
            node("n-morgan", "m-morgan", currentRoot.id, StructureKind.CURRENT, 160.0, bvPerPv),
            node("n-riley", "m-riley", "n-morgan", StructureKind.CURRENT, 90.0, bvPerPv),
            node("n-avery", "m-avery", "n-morgan", StructureKind.CURRENT, 75.0, bvPerPv),
            node("n-taylor", "m-taylor", currentRoot.id, StructureKind.CURRENT, 140.0, bvPerPv),
            node("n-quinn", "m-quinn", "n-taylor", StructureKind.CURRENT, 55.0, bvPerPv),
            node("n-jamie", "m-jamie", currentRoot.id, StructureKind.CURRENT, 110.0, bvPerPv),
            node("n-drew", "m-drew", "n-jamie", StructureKind.CURRENT, 60.0, bvPerPv),
            node("n-blair", "m-blair", "n-jamie", StructureKind.CURRENT, 40.0, bvPerPv),
            node("n-priya", "m-priya", currentRoot.id, StructureKind.CURRENT, 85.0, bvPerPv),
        )

        val idealNodes = listOf(
            idealRoot,
            node("n-leg1", "m-leg1", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-leg2", "m-leg2", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-leg3", "m-leg3", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-leg4", "m-leg4", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-leg5", "m-leg5", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-leg6", "m-leg6", idealRoot.id, StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-depth-a", "m-depth-a", "n-leg1", StructureKind.IDEAL, 7_500.0, bvPerPv),
            node("n-depth-b", "m-depth-b", "n-leg2", StructureKind.IDEAL, 7_500.0, bvPerPv),
        )

        val draft = OrgSnapshot(members = members, nodes = currentNodes + idealNodes)
        val placed = TreeLayout.applyPositions(draft, StructureKind.CURRENT) +
            TreeLayout.applyPositions(draft, StructureKind.IDEAL)
        return draft.copy(nodes = placed)
    }

    fun node(
        id: String,
        memberId: String,
        parentId: String?,
        kind: StructureKind,
        personalPv: Double,
        bvPerPv: Double,
        canvasX: Float = 0f,
        canvasY: Float = 0f,
    ): OrgNode = OrgNode(
        id = id,
        memberId = memberId,
        parentId = parentId,
        kind = kind,
        personalPv = personalPv,
        personalBv = personalPv * bvPerPv,
        canvasX = canvasX,
        canvasY = canvasY,
    )

    fun newId(prefix: String = "id"): String = "$prefix-${UUID.randomUUID().toString().take(8)}"
}
