package com.networkmarketing.planner.data.repository

import com.networkmarketing.planner.data.local.MemberEntity
import com.networkmarketing.planner.data.local.OrgNodeEntity
import com.networkmarketing.planner.data.local.PlannerDao
import com.networkmarketing.planner.data.local.PrefsEntity
import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlanProfile
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import com.networkmarketing.planner.domain.ops.SnapshotOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlannerRepository(
    private val dao: PlannerDao,
) : PlannerStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val snapshot: Flow<OrgSnapshot> = combine(
        dao.observeMembers(),
        dao.observeNodes(),
        dao.observePrefs(),
    ) { members, nodes, prefs ->
        OrgSnapshot(
            members = members.map { it.toModel() },
            nodes = nodes.map { it.toModel() },
            planProfiles = decodeProfiles(prefs?.planProfilesJson),
            planClaims = decodeClaims(prefs?.planClaimsJson),
        ).withNormalizedPlans()
    }

    override val prefs: Flow<Pair<UserGoals, PlannerSettings>> = dao.observePrefs().map { entity ->
        val prefs = entity ?: defaultPrefs()
        prefs.toGoals() to prefs.toSettings()
    }

    override suspend fun ensureSeeded() {
        if (dao.nodeCount() == 0) {
            replaceSnapshot(SampleData.snapshot(PlannerSettings.DEFAULT_BV_PER_PV))
        } else {
            migratePlansIfNeeded()
        }
        if (dao.getPrefs() == null) {
            dao.upsertPrefs(defaultPrefs())
        }
    }

    private suspend fun migratePlansIfNeeded() {
        val snap = loadSnapshotOnce()
        val normalized = snap.withNormalizedPlans()
        if (normalized != snap) {
            replaceSnapshot(normalized)
            return
        }
        val prefs = dao.getPrefs() ?: return
        if (prefs.planProfilesJson == "[]" || prefs.planProfilesJson.isBlank()) {
            dao.upsertPrefs(
                prefs.copy(
                    planProfilesJson = json.encodeToString(listOf(PlanProfile.default())),
                    planClaimsJson = prefs.planClaimsJson.ifBlank { "{}" },
                ),
            )
        }
    }

    override suspend fun restoreSampleData() {
        val current = dao.getPrefs() ?: defaultPrefs()
        replaceSnapshot(SampleData.snapshot(current.bvPerPv))
    }

    override suspend fun saveGoals(goals: UserGoals) {
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

    override suspend fun saveSettings(settings: PlannerSettings) {
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
                fsiEligible = settings.fsiEligible,
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

    override suspend fun replaceSnapshot(snapshot: OrgSnapshot) {
        val normalized = snapshot.withNormalizedPlans()
        dao.replaceOrganization(
            normalized.members.map { it.toEntity() },
            normalized.nodes.map { it.toEntity() },
        )
        val prefs = dao.getPrefs() ?: defaultPrefs()
        dao.upsertPrefs(
            prefs.copy(
                planProfilesJson = json.encodeToString(normalized.planProfiles),
                planClaimsJson = json.encodeToString(normalized.planClaims),
            ),
        )
    }

    override suspend fun addNode(
        kind: StructureKind,
        canvasX: Float,
        canvasY: Float,
        parentId: String?,
        name: String,
        personalPv: Double,
        bvPerPv: Double,
        partnerName: String,
        isCouple: Boolean,
        planProfileId: String?,
    ): String {
        val current = currentSnapshot()
        val (updated, nodeId) = SnapshotOps.addNode(
            snapshot = current,
            kind = kind,
            parentId = parentId,
            name = name,
            personalPv = personalPv,
            bvPerPv = bvPerPv,
            partnerName = partnerName,
            isCouple = isCouple,
            canvasX = canvasX,
            canvasY = canvasY,
            planProfileId = planProfileId,
        )
        replaceSnapshot(updated)
        return nodeId
    }

    override suspend fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
        vcsPv: Double?,
    ) {
        val updated = SnapshotOps.updatePerson(
            currentSnapshot(),
            node.id,
            name,
            partnerName,
            isCouple,
            notes,
            personalPv,
            personalBv,
            vcsPv,
        )
        replaceSnapshot(updated)
    }

    override suspend fun updatePosition(node: OrgNode, canvasX: Float, canvasY: Float) {
        replaceSnapshot(SnapshotOps.move(currentSnapshot(), node.id, canvasX, canvasY))
    }

    override suspend fun setParent(snapshot: OrgSnapshot, childId: String, parentId: String?): Boolean {
        val updated = SnapshotOps.setParent(snapshot, childId, parentId) ?: return false
        replaceSnapshot(updated)
        return true
    }

    override suspend fun applyLayout(snapshot: OrgSnapshot, kind: StructureKind, planProfileId: String?) {
        replaceSnapshot(SnapshotOps.applyLayout(snapshot, kind, planProfileId))
    }

    override suspend fun deleteSubtree(snapshot: OrgSnapshot, nodeId: String) {
        replaceSnapshot(SnapshotOps.deleteSubtree(snapshot, nodeId))
    }

    override suspend fun copyCurrentToIdeal(snapshot: OrgSnapshot, bvPerPv: Double) {
        replaceSnapshot(SnapshotOps.copyCurrentToIdeal(snapshot, bvPerPv))
    }

    override suspend fun copyCurrentToPlan(snapshot: OrgSnapshot, planProfileId: String, bvPerPv: Double) {
        replaceSnapshot(SnapshotOps.copyCurrentToPlan(snapshot, planProfileId, bvPerPv))
    }

    override suspend fun createPlanProfile(snapshot: OrgSnapshot, name: String, bvPerPv: Double): String {
        val (updated, id) = SnapshotOps.createPlanProfile(snapshot, name, bvPerPv)
        replaceSnapshot(updated)
        return id
    }

    override suspend fun renamePlanProfile(snapshot: OrgSnapshot, profileId: String, name: String) {
        replaceSnapshot(SnapshotOps.renamePlanProfile(snapshot, profileId, name))
    }

    override suspend fun deletePlanProfile(snapshot: OrgSnapshot, profileId: String) {
        replaceSnapshot(SnapshotOps.deletePlanProfile(snapshot, profileId))
    }

    override suspend fun claimPlanSlot(snapshot: OrgSnapshot, currentNodeId: String, planNodeId: String): Boolean {
        val updated = SnapshotOps.claimPlanSlot(snapshot, currentNodeId, planNodeId) ?: return false
        replaceSnapshot(updated)
        return true
    }

    override suspend fun unclaimPlanSlot(snapshot: OrgSnapshot, currentNodeId: String) {
        replaceSnapshot(SnapshotOps.unclaimPlanSlot(snapshot, currentNodeId))
    }

    /**
     * One-shot snapshot from Room for mutations. Uses the latest prefs + a full table read
     * via replaceOrganization's counterpart: we store enough in prefs and re-read nodes
     * through temporary upserts — actually use [dao] clear-free path with observe is async.
     * Instead keep an in-memory cache updated by the Flow would be ideal; for simplicity
     * we add suspend getters on the DAO.
     */
    private suspend fun currentSnapshot(): OrgSnapshot {
        // Prefer building from the last known Flow value isn't available here.
        // Use Dao suspend queries added below.
        return loadSnapshotOnce()
    }

    private suspend fun loadSnapshotOnce(): OrgSnapshot {
        val members = dao.getMembers()
        val nodes = dao.getNodes()
        val prefs = dao.getPrefs()
        return OrgSnapshot(
            members = members.map { it.toModel() },
            nodes = nodes.map { it.toModel() },
            planProfiles = decodeProfiles(prefs?.planProfilesJson),
            planClaims = decodeClaims(prefs?.planClaimsJson),
        ).withNormalizedPlans()
    }

    private fun decodeProfiles(raw: String?): List<PlanProfile> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<PlanProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun decodeClaims(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank() || raw == "{}") return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
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
        fsiEligible = true,
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
        planProfilesJson = json.encodeToString(listOf(PlanProfile.default())),
        planClaimsJson = "{}",
    )
}

private fun MemberEntity.toModel() = Member(id, name, notes, isYou, partnerName, isCouple)
private fun Member.toEntity() = MemberEntity(id, name, notes, isYou, partnerName, isCouple)

private fun OrgNodeEntity.toModel() = OrgNode(
    id = id,
    memberId = memberId,
    parentId = parentId,
    kind = StructureKind.valueOf(kind),
    personalPv = personalPv,
    personalBv = personalBv,
    vcsPv = vcsPv,
    canvasX = canvasX,
    canvasY = canvasY,
    planProfileId = planProfileId,
)

private fun OrgNode.toEntity() = OrgNodeEntity(
    id = id,
    memberId = memberId,
    parentId = parentId,
    kind = kind.name,
    personalPv = personalPv,
    personalBv = personalBv,
    vcsPv = vcsPv,
    canvasX = canvasX,
    canvasY = canvasY,
    planProfileId = planProfileId,
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
    fsiEligible = fsiEligible,
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
