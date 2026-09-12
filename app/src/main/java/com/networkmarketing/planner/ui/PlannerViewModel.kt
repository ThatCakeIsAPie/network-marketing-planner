package com.networkmarketing.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.networkmarketing.planner.data.repository.PlannerStore
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.canvas.LosGraph
import com.networkmarketing.planner.domain.compensation.CompensationEngine
import com.networkmarketing.planner.domain.compensation.FrontlineVolume
import com.networkmarketing.planner.domain.compensation.GapAnalyzer
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import com.networkmarketing.planner.domain.compensation.StructureGap
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlanProfile
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import com.networkmarketing.planner.domain.ops.SnapshotOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlannerUiState(
    val isReady: Boolean = false,
    val snapshot: OrgSnapshot = OrgSnapshot(),
    val goals: UserGoals = UserGoals(),
    val settings: PlannerSettings = PlannerSettings(),
    val currentPayout: PayoutBreakdown? = null,
    val idealPayout: PayoutBreakdown? = null,
    val gap: StructureGap? = null,
    val selectedNodeId: String? = null,
    val calculatorPersonalPv: String = "250",
    val calculatorGroupPv: String = "1500",
    val calculatorMaxLegs: String = "0",
    val calculatorUseOrg: Boolean = true,
    val currentPayouts: Map<String, PayoutBreakdown> = emptyMap(),
    val idealPayouts: Map<String, PayoutBreakdown> = emptyMap(),
    /** Plan tab: which profile is being edited. */
    val editingPlanProfileId: String = PlanProfile.DEFAULT_ID,
    /** Map tab: which profile’s ghosts to overlay; null = none. */
    val mapGhostProfileId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class PlannerViewModel(
    private val repository: PlannerStore,
    private val engine: CompensationEngine,
    private val gapAnalyzer: GapAnalyzer,
) : ViewModel() {

    private val session = MutableStateFlow(SessionFields())
    private val undoStack = ArrayDeque<OrgSnapshot>()
    private val redoStack = ArrayDeque<OrgSnapshot>()

    val uiState: StateFlow<PlannerUiState> = combine(
        repository.snapshot,
        repository.prefs,
        session,
    ) { snapshot, prefs, sess ->
        val snap = snapshot.withNormalizedPlans()
        val (goals, settings) = prefs
        val editProfile = sess.editingPlanProfileId
            ?.takeIf { id -> snap.planProfiles.any { it.id == id } }
            ?: snap.primaryPlanProfileId()
        val ghostProfile = sess.mapGhostProfileId
            ?.takeIf { id -> snap.planProfiles.any { it.id == id } }
        val current = engine.evaluateRoot(snap, StructureKind.CURRENT, settings)
        val ideal = engine.evaluateRoot(snap, StructureKind.IDEAL, settings, editProfile)
        PlannerUiState(
            isReady = true,
            snapshot = snap,
            goals = goals,
            settings = settings,
            currentPayout = current,
            idealPayout = ideal,
            gap = gapAnalyzer.compare(snap, settings, goals, editProfile),
            selectedNodeId = sess.selectedNodeId,
            calculatorPersonalPv = sess.personalPv,
            calculatorGroupPv = sess.groupPv,
            calculatorMaxLegs = sess.maxLegs,
            calculatorUseOrg = sess.useOrg,
            currentPayouts = payoutsFor(snap, StructureKind.CURRENT, settings, null),
            idealPayouts = payoutsFor(snap, StructureKind.IDEAL, settings, editProfile),
            editingPlanProfileId = editProfile,
            mapGhostProfileId = ghostProfile,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }

    fun completeOnboarding(income: Double, rankId: String, accepted: Boolean) {
        viewModelScope.launch {
            repository.saveGoals(
                uiState.value.goals.copy(
                    monthlyIncomeTarget = income,
                    targetRankId = rankId,
                    onboardingComplete = true,
                    disclaimerAccepted = accepted,
                ),
            )
        }
    }

    fun updateGoals(income: Double, rankId: String) {
        viewModelScope.launch {
            repository.saveGoals(
                uiState.value.goals.copy(
                    monthlyIncomeTarget = income,
                    targetRankId = rankId,
                ),
            )
        }
    }

    fun updateSettings(settings: PlannerSettings) {
        viewModelScope.launch { repository.saveSettings(settings) }
    }

    fun selectNode(id: String?) {
        session.update { it.copy(selectedNodeId = id) }
    }

    fun selectEditingPlanProfile(profileId: String) {
        session.update { it.copy(editingPlanProfileId = profileId) }
    }

    fun selectMapGhostProfile(profileId: String?) {
        session.update { it.copy(mapGhostProfileId = profileId) }
    }

    fun addChild(parent: OrgNode, name: String, personalPv: Double) {
        mutate {
            val kids = it.children(parent.id).size
            val pos = CanvasMetrics.snapPoint(
                parent.canvasX + kids * (CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_X),
                parent.canvasY + CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_Y,
            )
            SnapshotOps.addNode(
                snapshot = it,
                kind = parent.kind,
                parentId = parent.id,
                name = name,
                personalPv = personalPv,
                bvPerPv = uiState.value.settings.bvPerPv,
                canvasX = pos.first,
                canvasY = pos.second,
                planProfileId = parent.planProfileId,
            ).first
        }
    }

    fun addNodeAt(
        kind: StructureKind,
        x: Float,
        y: Float,
        parent: OrgNode? = null,
        name: String = "New partner",
        planProfileId: String? = null,
    ) {
        viewModelScope.launch {
            pushUndo(uiState.value.snapshot)
            val pos = CanvasMetrics.snapPoint(x, y)
            val profileId = planProfileId
                ?: if (kind == StructureKind.IDEAL) uiState.value.editingPlanProfileId else null
            val id = repository.addNode(
                kind = kind,
                canvasX = pos.first,
                canvasY = pos.second,
                parentId = parent?.id,
                name = name,
                personalPv = 100.0,
                bvPerPv = uiState.value.settings.bvPerPv,
                planProfileId = profileId,
            )
            selectNode(id)
            bumpHistoryFlags()
        }
    }

    fun saveNode(node: OrgNode, name: String, personalPv: Double) {
        val member = uiState.value.snapshot.member(node.memberId)
        val bv = personalPv * uiState.value.settings.bvPerPv
        savePerson(
            node,
            name,
            member?.partnerName.orEmpty(),
            member?.isCouple == true,
            member?.notes.orEmpty(),
            personalPv,
            bv,
        )
    }

    fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
        vcsPv: Double? = null,
    ) {
        mutate {
            SnapshotOps.updatePerson(it, node.id, name, partnerName, isCouple, notes, personalPv, personalBv, vcsPv)
        }
    }

    fun moveNode(node: OrgNode, x: Float, y: Float) {
        mutate {
            SnapshotOps.move(it, node.id, x, y)
        }
    }

    fun applyLosEdit(edit: LosGraph.ConnectionEdit) {
        mutate { snap ->
            var next = snap
            edit.detachId?.let { id ->
                next = SnapshotOps.setParent(next, id, null) ?: next
            }
            SnapshotOps.setParent(next, edit.childId, edit.newParentId) ?: next
        }
    }

    fun applyLayout(kind: StructureKind, planProfileId: String? = null) {
        mutate {
            val profile = planProfileId
                ?: if (kind == StructureKind.IDEAL) uiState.value.editingPlanProfileId else null
            SnapshotOps.applyLayout(it, kind, profile)
        }
    }

    fun deleteNode(nodeId: String) {
        mutate { SnapshotOps.deleteSubtree(it, nodeId) }
        session.update { fields ->
            fields.copy(selectedNodeId = fields.selectedNodeId.takeUnless { it == nodeId })
        }
    }

    fun restoreSample() {
        viewModelScope.launch {
            pushUndo(uiState.value.snapshot)
            repository.restoreSampleData()
            bumpHistoryFlags()
        }
    }

    fun copyCurrentToIdeal() {
        copyCurrentToSelectedPlan()
    }

    fun copyCurrentToSelectedPlan() {
        mutate {
            SnapshotOps.copyCurrentToPlan(it, uiState.value.editingPlanProfileId, uiState.value.settings.bvPerPv)
        }
    }

    fun createPlanProfile(name: String) {
        viewModelScope.launch {
            pushUndo(uiState.value.snapshot)
            val id = repository.createPlanProfile(
                uiState.value.snapshot,
                name,
                uiState.value.settings.bvPerPv,
            )
            if (id.isNotBlank()) selectEditingPlanProfile(id)
            bumpHistoryFlags()
        }
    }

    fun renamePlanProfile(profileId: String, name: String) {
        mutate { SnapshotOps.renamePlanProfile(it, profileId, name) }
    }

    fun deletePlanProfile(profileId: String) {
        mutate { SnapshotOps.deletePlanProfile(it, profileId) }
        val remaining = uiState.value.snapshot.planProfiles.filter { it.id != profileId }
        if (remaining.isNotEmpty() && uiState.value.editingPlanProfileId == profileId) {
            selectEditingPlanProfile(remaining.first().id)
        }
        if (uiState.value.mapGhostProfileId == profileId) {
            selectMapGhostProfile(null)
        }
    }

    fun claimPlanSlot(currentNodeId: String, planNodeId: String) {
        mutate { snap ->
            SnapshotOps.claimPlanSlot(snap, currentNodeId, planNodeId) ?: snap
        }
    }

    fun unclaimPlanSlot(currentNodeId: String) {
        mutate { SnapshotOps.unclaimPlanSlot(it, currentNodeId) }
    }

    fun undo() {
        viewModelScope.launch {
            val previous = undoStack.removeLastOrNull() ?: return@launch
            redoStack.addLast(uiState.value.snapshot)
            trim(redoStack)
            repository.replaceSnapshot(previous)
            bumpHistoryFlags()
        }
    }

    fun redo() {
        viewModelScope.launch {
            val next = redoStack.removeLastOrNull() ?: return@launch
            undoStack.addLast(uiState.value.snapshot)
            trim(undoStack)
            repository.replaceSnapshot(next)
            bumpHistoryFlags()
        }
    }

    fun setCalculatorFields(personalPv: String, groupPv: String, maxLegs: String, useOrg: Boolean) {
        session.update {
            it.copy(personalPv = personalPv, groupPv = groupPv, maxLegs = maxLegs, useOrg = useOrg)
        }
    }

    fun calculatorPayout(state: PlannerUiState): PayoutBreakdown {
        if (state.calculatorUseOrg && state.currentPayout != null) return state.currentPayout
        val personalPv = state.calculatorPersonalPv.toDoubleOrNull() ?: 0.0
        val groupPv = state.calculatorGroupPv.toDoubleOrNull() ?: personalPv
        val maxLegs = state.calculatorMaxLegs.toIntOrNull() ?: 0
        val passUp = (groupPv - personalPv).coerceAtLeast(0.0)
        val maxPv = engine.config().silverProducerGroupPv
        val passUpPercent = engine.config().bracketFor(passUp.coerceAtMost(maxPv - 0.01)).percent
        val frontline = buildList {
            repeat(maxLegs) {
                add(FrontlineVolume("25% leg ${it + 1}", maxPv, engine.bvForPv(maxPv, state.settings)))
            }
            if (passUp > 0) {
                add(
                    FrontlineVolume(
                        name = "Pass-up (non-25%)",
                        groupPv = passUp,
                        groupBv = engine.bvForPv(passUp, state.settings),
                        performancePercent = passUpPercent,
                    ),
                )
            }
        }
        return engine.evaluateInputs(
            personalPv = personalPv,
            personalBv = engine.bvForPv(personalPv, state.settings),
            frontline = frontline,
            settings = state.settings,
        )
    }

    fun engine(): CompensationEngine = engine

    fun youNode(kind: StructureKind): OrgNode? = uiState.value.snapshot.root(kind)

    fun planGhostPayouts(state: PlannerUiState): Map<String, PayoutBreakdown> {
        val profileId = state.mapGhostProfileId ?: return emptyMap()
        return payoutsFor(state.snapshot, StructureKind.IDEAL, state.settings, profileId)
    }

    private fun mutate(block: (OrgSnapshot) -> OrgSnapshot) {
        viewModelScope.launch {
            val before = uiState.value.snapshot
            pushUndo(before)
            repository.replaceSnapshot(block(before))
            bumpHistoryFlags()
        }
    }

    private fun pushUndo(snapshot: OrgSnapshot) {
        undoStack.addLast(snapshot)
        trim(undoStack)
        redoStack.clear()
    }

    private fun trim(stack: ArrayDeque<OrgSnapshot>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    private fun bumpHistoryFlags() {
        // Trigger ui recombine so canUndo/canRedo refresh.
        session.update { it.copy(historyEpoch = it.historyEpoch + 1) }
    }

    private fun payoutsFor(
        snapshot: OrgSnapshot,
        kind: StructureKind,
        settings: PlannerSettings,
        planProfileId: String?,
    ): Map<String, PayoutBreakdown> =
        snapshot.nodes(kind, planProfileId).associate { it.id to engine.evaluateNode(snapshot, it.id, settings) }

    companion object {
        private const val MAX_HISTORY = 50

        fun factory(
            repository: PlannerStore,
            engine: CompensationEngine,
            gapAnalyzer: GapAnalyzer,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlannerViewModel(repository, engine, gapAnalyzer) as T
            }
        }
    }
}

private data class SessionFields(
    val selectedNodeId: String? = null,
    val personalPv: String = "250",
    val groupPv: String = "1500",
    val maxLegs: String = "0",
    val useOrg: Boolean = true,
    val editingPlanProfileId: String? = null,
    val mapGhostProfileId: String? = null,
    val historyEpoch: Int = 0,
)

fun PlannerUiState.selectedNode(): OrgNode? =
    selectedNodeId?.let { snapshot.node(it) } ?: snapshot.root(StructureKind.CURRENT)
