package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class StructureGap(
    val current: PayoutBreakdown,
    val ideal: PayoutBreakdown?,
    val groupPvGap: Double,
    val incomeGapToGoal: Double,
    val incomeGapToIdeal: Double,
    val peopleGap: Int,
    val maxPercentLegsGap: Int,
    val suggestions: List<String>,
)

class GapAnalyzer(
    private val engine: CompensationEngine = CompensationEngine(),
) {
    fun compare(
        snapshot: OrgSnapshot,
        settings: PlannerSettings,
        goals: UserGoals,
    ): StructureGap? {
        val current = engine.evaluateRoot(snapshot, StructureKind.CURRENT, settings) ?: return null
        val ideal = engine.evaluateRoot(snapshot, StructureKind.IDEAL, settings)
        val targetRank = engine.config().rank(goals.targetRankId)
        val need = engine.neededForRank(current, targetRank)

        val suggestions = buildList {
            if (need.pvNeeded > 0) {
                add(
                    "Add about ${need.pvNeeded.toInt()} group PV to reach ${targetRank.title} volume " +
                        "(${current.group.pv.toInt()} → ${targetRank.minGroupPv.toInt()}).",
                )
            }
            if (need.maxPercentLegsNeeded > 0) {
                add(
                    "Grow ${need.maxPercentLegsNeeded} more max-bracket frontline " +
                        "(${current.maxPercentLegs} of ${targetRank.minMaxPercentLegs} today).",
                )
            }
            if (need.rubyPvNeeded > 0) {
                add(
                    "Build ${need.rubyPvNeeded.toInt()} more Ruby/Side PV (Personal + pass-up, excluding 25% legs).",
                )
            }
            if (!current.silverProducerMonth &&
                (targetRank.id == RankIds.SILVER || targetRank.minGroupPv >= 7_500)
            ) {
                add(engine.silverProducerHint(current))
            }
            val incomeGap = max(0.0, goals.monthlyIncomeTarget - current.estimatedMonthly)
            if (incomeGap > 1.0) {
                val extraBv = if (current.performancePercent > 0) {
                    incomeGap / current.performancePercent
                } else {
                    incomeGap / 0.03
                }
                add(
                    "Estimated payout is short of the income goal by " +
                        "${incomeGap.toInt()}. Roughly ${extraBv.toInt()} more BV at the current " +
                        "${(current.performancePercent * 100).toInt()}% bracket would close that gap " +
                        "(ignores differentials and leadership).",
                )
            }
            val weakLeaves = snapshot.nodes(StructureKind.CURRENT)
                .filter { snapshot.children(it.id).isEmpty() && !snapshot.isYou(it) && it.personalPv < 100 }
            if (weakLeaves.isNotEmpty()) {
                add(
                    "${weakLeaves.size} people in the current map are under 100 personal PV. " +
                        "Raising personal volume is often the fastest path into the first bonus bracket.",
                )
            }
            if (ideal != null && ideal.estimatedMonthly > current.estimatedMonthly + 1) {
                add(
                    "The ideal structure projects about ${(ideal.estimatedMonthly - current.estimatedMonthly).toInt()} " +
                        "more monthly than the current map under the same formulas.",
                )
            }
            if (isEmpty()) add("Current structure already meets the selected rank and income targets under these estimates.")
        }

        return StructureGap(
            current = current,
            ideal = ideal,
            groupPvGap = max(0.0, (ideal?.group?.pv ?: targetRank.minGroupPv) - current.group.pv),
            incomeGapToGoal = max(0.0, goals.monthlyIncomeTarget - current.estimatedMonthly),
            incomeGapToIdeal = max(0.0, (ideal?.estimatedMonthly ?: 0.0) - current.estimatedMonthly),
            peopleGap = max(0, snapshot.nodeCount(StructureKind.IDEAL) - snapshot.nodeCount(StructureKind.CURRENT)),
            maxPercentLegsGap = need.maxPercentLegsNeeded,
            suggestions = suggestions,
        )
    }
}
