package com.networkmarketing.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.networkmarketing.planner.data.repository.PlannerRepository
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
)

class PlannerViewModel(
    private val repository: PlannerRepository,
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
            repository.addChild(parent, name, personalPv, uiState.value.settings.bvPerPv)
        }
    }

    fun saveNode(node: OrgNode, name: String, personalPv: Double) {
        viewModelScope.launch {
            val bv = personalPv * uiState.value.settings.bvPerPv
            repository.updateNodeVolume(node, personalPv, bv, name)
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
        val remaining = (groupPv - personalPv).coerceAtLeast(0.0)
        val frontline = buildList {
            val maxPv = engine.config().brackets.maxBy { it.minPv }.minPv
            repeat(maxLegs) {
                add(FrontlineVolume("Max-bracket leg ${it + 1}", maxPv, engine.bvForPv(maxPv, state.settings)))
            }
            if (remaining > maxLegs * maxPv) {
                val leftover = remaining - maxLegs * maxPv
                add(FrontlineVolume("Other volume", leftover, engine.bvForPv(leftover, state.settings)))
            } else if (maxLegs == 0 && remaining > 0) {
                add(FrontlineVolume("Downline", remaining, engine.bvForPv(remaining, state.settings)))
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

    companion object {
        fun factory(
            repository: PlannerRepository,
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
