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
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
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
)

class PlannerViewModel(
    private val repository: PlannerStore,
    private val engine: CompensationEngine,
    private val gapAnalyzer: GapAnalyzer,
) : ViewModel() {

    private val calculatorOverrides = MutableStateFlow(CalculatorFields())

    val uiState: StateFlow<PlannerUiState> = combine(
        repository.snapshot,
        repository.prefs,
        calculatorOverrides,
    ) { snapshot, prefs, calc ->
        val (goals, settings) = prefs
        val current = engine.evaluateRoot(snapshot, StructureKind.CURRENT, settings)
        val ideal = engine.evaluateRoot(snapshot, StructureKind.IDEAL, settings)
        PlannerUiState(
            isReady = true,
            snapshot = snapshot,
            goals = goals,
            settings = settings,
            currentPayout = current,
            idealPayout = ideal,
            gap = gapAnalyzer.compare(snapshot, settings, goals),
            selectedNodeId = calc.selectedNodeId,
            calculatorPersonalPv = calc.personalPv,
            calculatorGroupPv = calc.groupPv,
            calculatorMaxLegs = calc.maxLegs,
            calculatorUseOrg = calc.useOrg,
            currentPayouts = payoutsFor(snapshot, StructureKind.CURRENT, settings),
            idealPayouts = payoutsFor(snapshot, StructureKind.IDEAL, settings),
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
        calculatorOverrides.update { it.copy(selectedNodeId = id) }
    }

    fun addChild(parent: OrgNode, name: String, personalPv: Double) {
        viewModelScope.launch {
            val kids = uiState.value.snapshot.children(parent.id).size
            val pos = CanvasMetrics.snapPoint(
                parent.canvasX + kids * (CanvasMetrics.NODE_WIDTH + CanvasMetrics.GAP_X),
                parent.canvasY + CanvasMetrics.nodeHeight(uiState.value.snapshot.isCouple(parent)) +
                    CanvasMetrics.GAP_Y,
            )
            repository.addNode(
                kind = parent.kind,
                canvasX = pos.first,
                canvasY = pos.second,
                parentId = parent.id,
                name = name,
                personalPv = personalPv,
                bvPerPv = uiState.value.settings.bvPerPv,
            )
        }
    }

    fun addNodeAt(kind: StructureKind, x: Float, y: Float, parent: OrgNode? = null, name: String = "New partner") {
        viewModelScope.launch {
            val pos = CanvasMetrics.snapPoint(x, y)
            val id = repository.addNode(
                kind = kind,
                canvasX = pos.first,
                canvasY = pos.second,
                parentId = parent?.id,
                name = name,
                personalPv = 100.0,
                bvPerPv = uiState.value.settings.bvPerPv,
            )
            selectNode(id)
        }
    }

    fun saveNode(node: OrgNode, name: String, personalPv: Double) {
        viewModelScope.launch {
            val member = uiState.value.snapshot.member(node.memberId)
            val bv = personalPv * uiState.value.settings.bvPerPv
            repository.savePerson(
                node = node,
                name = name,
                partnerName = member?.partnerName.orEmpty(),
                isCouple = member?.isCouple == true,
                notes = member?.notes.orEmpty(),
                personalPv = personalPv,
                personalBv = bv,
            )
        }
    }

    fun savePerson(
        node: OrgNode,
        name: String,
        partnerName: String,
        isCouple: Boolean,
        notes: String,
        personalPv: Double,
        personalBv: Double,
    ) {
        viewModelScope.launch {
            repository.savePerson(node, name, partnerName, isCouple, notes, personalPv, personalBv)
        }
    }

    fun moveNode(node: OrgNode, x: Float, y: Float) {
        viewModelScope.launch {
            val snapped = CanvasMetrics.snapPoint(x, y)
            repository.updatePosition(node, snapped.first, snapped.second)
        }
    }

    fun applyLosEdit(edit: LosGraph.ConnectionEdit) {
        viewModelScope.launch {
            val snapshot = uiState.value.snapshot
            edit.detachId?.let { repository.setParent(snapshot, it, null) }
            repository.setParent(uiState.value.snapshot, edit.childId, edit.newParentId)
        }
    }

    fun applyLayout(kind: StructureKind) {
        viewModelScope.launch {
            repository.applyLayout(uiState.value.snapshot, kind)
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            repository.deleteSubtree(uiState.value.snapshot, nodeId)
            calculatorOverrides.update { fields ->
                fields.copy(selectedNodeId = fields.selectedNodeId.takeUnless { it == nodeId })
            }
        }
    }

    fun restoreSample() {
        viewModelScope.launch { repository.restoreSampleData() }
    }

    fun copyCurrentToIdeal() {
        viewModelScope.launch {
            repository.copyCurrentToIdeal(uiState.value.snapshot, uiState.value.settings.bvPerPv)
        }
    }

    fun setCalculatorFields(personalPv: String, groupPv: String, maxLegs: String, useOrg: Boolean) {
        calculatorOverrides.update {
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

    private fun payoutsFor(
        snapshot: OrgSnapshot,
        kind: StructureKind,
        settings: PlannerSettings,
    ): Map<String, PayoutBreakdown> =
        snapshot.nodes(kind).associate { it.id to engine.evaluateNode(snapshot, it.id, settings) }

    companion object {
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

private data class CalculatorFields(
    val selectedNodeId: String? = null,
    val personalPv: String = "250",
    val groupPv: String = "1500",
    val maxLegs: String = "0",
    val useOrg: Boolean = true,
)

fun PlannerUiState.selectedNode(): OrgNode? =
    selectedNodeId?.let { snapshot.node(it) } ?: snapshot.root(StructureKind.CURRENT)
