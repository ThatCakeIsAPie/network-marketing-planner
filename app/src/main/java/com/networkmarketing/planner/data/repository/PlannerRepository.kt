package com.networkmarketing.planner.data.repository

import com.networkmarketing.planner.data.local.MemberEntity
import com.networkmarketing.planner.data.local.OrgNodeEntity
import com.networkmarketing.planner.data.local.PlannerDao
import com.networkmarketing.planner.data.local.PrefsEntity
import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlannerRepository(
    private val dao: PlannerDao,
) {
    val snapshot: Flow<OrgSnapshot> = combine(
        dao.observeMembers(),
        dao.observeNodes(),
    ) { members, nodes ->
        OrgSnapshot(
            members = members.map { it.toModel() },
            nodes = nodes.map { it.toModel() },
        )
    }

    val prefs: Flow<Pair<UserGoals, PlannerSettings>> = dao.observePrefs().map { entity ->
        val prefs = entity ?: defaultPrefs()
        prefs.toGoals() to prefs.toSettings()
    }

    suspend fun ensureSeeded() {
        if (dao.nodeCount() == 0) {
            val seeded = SampleData.snapshot(PlannerSettings.DEFAULT_BV_PER_PV)
            dao.replaceOrganization(seeded.members.map { it.toEntity() }, seeded.nodes.map { it.toEntity() })
        }
        if (dao.getPrefs() == null) {
            dao.upsertPrefs(defaultPrefs())
        }
    }

    suspend fun restoreSampleData() {
        val current = dao.getPrefs() ?: defaultPrefs()
        val seeded = SampleData.snapshot(current.bvPerPv)
        dao.replaceOrganization(seeded.members.map { it.toEntity() }, seeded.nodes.map { it.toEntity() })
    }

    suspend fun saveGoals(goals: UserGoals) {
        val current = dao.getPrefs() ?: defaultPrefs()
        dao.upsertPrefs(
            current.copy(
                onboardingComplete = goals.onboardingComplete,
                disclaimerAccepted = goals.disclaimerAccepted,
                monthlyIncomeTarget = goals.monthlyIncomeTarget,
                targetRankId = goals.targetRankId,
            ),
        )
    }

    suspend fun saveSettings(settings: PlannerSettings) {
        val current = dao.getPrefs() ?: defaultPrefs()
        dao.upsertPrefs(
            current.copy(
                bvPerPv = settings.bvPerPv,
                retailMarginPercent = settings.retailMarginPercent,
                includeRetailMargin = settings.includeRetailMargin,
                customerSalesPercent = settings.customerSalesPercent,
                vcsPercent = settings.vcsPercent,
                meetsRule413 = settings.meetsRule413,
                includeLeadershipBonus = settings.includeLeadershipBonus,
                includeDepthBonus = settings.includeDepthBonus,
                includeRubyBonus = settings.includeRubyBonus,
                includePerformancePlus = settings.includePerformancePlus,
                includeCsi = settings.includeCsi,
                csiEligible = settings.csiEligible,
                bfiEligible = settings.bfiEligible,
                bbiEligible = settings.bbiEligible,
                isPlatinumOrAbove = settings.isPlatinumOrAbove,
                silverProducerMonthsPy = settings.silverProducerMonthsPy,
                consecutiveSilverMonths = settings.consecutiveSilverMonths,
                pqMonthsPy = settings.pqMonthsPy,
                rubyPvPy = settings.rubyPvPy,
                personalPvPy = settings.personalPvPy,
                groupPvPy = settings.groupPvPy,
                totalDownlinePvPy = settings.totalDownlinePvPy,
                fqsPy = settings.fqsPy,
                priorYearPqMonths = settings.priorYearPqMonths,
                newIboBaselineMonths = settings.newIboBaselineMonths,
            ),
        )
    }

    suspend fun upsertMember(member: Member) {
        dao.upsertMember(member.toEntity())
    }

    suspend fun upsertNode(node: OrgNode) {
        dao.upsertNode(node.toEntity())
    }

    suspend fun addChild(
        parent: OrgNode,
        name: String,
        personalPv: Double,
        bvPerPv: Double,
    ) {
        val memberId = SampleData.newId("member")
        dao.upsertMember(
            MemberEntity(
                id = memberId,
                name = name.ifBlank { "New partner" },
                notes = "",
                isYou = false,
            ),
        )
        dao.upsertNode(
            OrgNodeEntity(
                id = SampleData.newId("node"),
                memberId = memberId,
                parentId = parent.id,
                kind = parent.kind.name,
                personalPv = personalPv,
                personalBv = personalPv * bvPerPv,
            ),
        )
    }

    suspend fun updateNodeVolume(node: OrgNode, personalPv: Double, personalBv: Double, name: String) {
        dao.upsertMember(
            MemberEntity(
                id = node.memberId,
                name = name,
                notes = "",
                isYou = node.memberId == SampleData.YOU_ID,
            ),
        )
        dao.updateNode(node.copy(personalPv = personalPv, personalBv = personalBv).toEntity())
    }

    suspend fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String) {
        val node = snapshot.node(nodeId) ?: return
        if (snapshot.isYou(node)) return
        val ids = listOf(nodeId) + snapshot.descendants(nodeId).map { it.id }
        dao.deleteNodes(ids)
        dao.deleteMember(node.memberId)
    }

    suspend fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double) {
        val mapping = mutableMapOf<String, String>()
        val rebuilt = topological(snapshot.nodes(StructureKind.CURRENT)).map { src ->
            val newId = if (src.parentId == null) "n-you-ideal" else SampleData.newId("node")
            mapping[src.id] = newId
            src.copy(
                id = newId,
                parentId = src.parentId?.let { mapping.getValue(it) },
                kind = StructureKind.IDEAL,
                personalBv = src.personalPv * bvPerPv,
            )
        }
        dao.replaceOrganization(
            snapshot.members.map { it.toEntity() },
            (snapshot.nodes(StructureKind.CURRENT) + rebuilt).map { it.toEntity() },
        )
    }

    private fun topological(nodes: List<OrgNode>): List<OrgNode> {
        val byParent = nodes.groupBy { it.parentId }
        val result = mutableListOf<OrgNode>()
        val roots = byParent[null].orEmpty()
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            result += n
            queue.addAll(byParent[n.id].orEmpty())
        }
        return result
    }

    private fun defaultPrefs(): PrefsEntity = PrefsEntity(
        onboardingComplete = false,
        disclaimerAccepted = false,
        monthlyIncomeTarget = 2_000.0,
        targetRankId = RankIds.SILVER,
        bvPerPv = PlannerSettings.DEFAULT_BV_PER_PV,
        retailMarginPercent = PlannerSettings.DEFAULT_RETAIL_MARGIN,
        includeRetailMargin = true,
        customerSalesPercent = PlannerSettings.DEFAULT_CUSTOMER_SALES,
        vcsPercent = PlannerSettings.DEFAULT_VCS,
        meetsRule413 = true,
        includeLeadershipBonus = true,
        includeDepthBonus = true,
        includeRubyBonus = true,
        includePerformancePlus = true,
        includeCsi = false,
        csiEligible = false,
        bfiEligible = true,
        bbiEligible = true,
        isPlatinumOrAbove = false,
        silverProducerMonthsPy = 0,
        consecutiveSilverMonths = 0,
        pqMonthsPy = 0,
        rubyPvPy = 0.0,
        personalPvPy = 0.0,
        groupPvPy = 0.0,
        totalDownlinePvPy = 0.0,
        fqsPy = 0,
        priorYearPqMonths = 0,
        newIboBaselineMonths = 0,
    )
}

private fun MemberEntity.toModel() = Member(id, name, notes, isYou)
private fun Member.toEntity() = MemberEntity(id, name, notes, isYou)

private fun OrgNodeEntity.toModel() = OrgNode(
    id = id,
    memberId = memberId,
    parentId = parentId,
    kind = StructureKind.valueOf(kind),
    personalPv = personalPv,
    personalBv = personalBv,
)

private fun OrgNode.toEntity() = OrgNodeEntity(
    id = id,
    memberId = memberId,
    parentId = parentId,
    kind = kind.name,
    personalPv = personalPv,
    personalBv = personalBv,
)

private fun PrefsEntity.toGoals() = UserGoals(
    monthlyIncomeTarget = monthlyIncomeTarget,
    targetRankId = targetRankId,
    onboardingComplete = onboardingComplete,
    disclaimerAccepted = disclaimerAccepted,
)

private fun PrefsEntity.toSettings() = PlannerSettings(
    bvPerPv = bvPerPv,
    retailMarginPercent = retailMarginPercent,
    includeRetailMargin = includeRetailMargin,
    customerSalesPercent = customerSalesPercent,
    vcsPercent = vcsPercent,
    meetsRule413 = meetsRule413,
    includeLeadershipBonus = includeLeadershipBonus,
    includeDepthBonus = includeDepthBonus,
    includeRubyBonus = includeRubyBonus,
    includePerformancePlus = includePerformancePlus,
    includeCsi = includeCsi,
    csiEligible = csiEligible,
    bfiEligible = bfiEligible,
    bbiEligible = bbiEligible,
    isPlatinumOrAbove = isPlatinumOrAbove,
    silverProducerMonthsPy = silverProducerMonthsPy,
    consecutiveSilverMonths = consecutiveSilverMonths,
    pqMonthsPy = pqMonthsPy,
    rubyPvPy = rubyPvPy,
    personalPvPy = personalPvPy,
    groupPvPy = groupPvPy,
    totalDownlinePvPy = totalDownlinePvPy,
    fqsPy = fqsPy,
    priorYearPqMonths = priorYearPqMonths,
    newIboBaselineMonths = newIboBaselineMonths,
)
