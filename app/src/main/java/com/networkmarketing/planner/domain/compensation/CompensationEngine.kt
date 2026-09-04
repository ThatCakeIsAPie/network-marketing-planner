package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.Volume
import kotlin.math.max

data class FrontlineVolume(
    val name: String,
    val groupPv: Double,
    val groupBv: Double,
)

data class PayoutBreakdown(
    val group: Volume,
    val personal: Volume,
    val performancePercent: Double,
    val personalPerformance: Double,
    val differential: Double,
    val performanceBonus: Double,
    val customerProfit: Double,
    val leadershipBonus: Double,
    val rubyBonus: Double,
    val rubyPv: Double,
    val rubyBv: Double,
    val estimatedMonthly: Double,
    val currentRank: RankDefinition,
    val nextRank: RankDefinition?,
    val maxPercentLegs: Int,
    val frontlineCount: Int,
) {
    fun progressToRank(target: RankDefinition): Float {
        val pvPart = if (target.minGroupPv <= 0) 1f
        else (group.pv / target.minGroupPv).toFloat().coerceIn(0f, 1f)
        val legsPart = if (target.minMaxPercentLegs <= 0) 1f
        else (maxPercentLegs.toFloat() / target.minMaxPercentLegs).coerceIn(0f, 1f)
        val rubyPart = if (target.minRubyPv <= 0) 1f
        else (rubyPv / target.minRubyPv).toFloat().coerceIn(0f, 1f)
        return minOf(pvPart, legsPart, rubyPart)
    }
}

/**
 * Configurable PV/BV payout and rank engine. Keep formula changes here.
 */
class CompensationEngine(
    private val config: CompensationConfig = CompensationConfig.default(),
) {
    fun config(): CompensationConfig = config

    fun evaluateRoot(
        snapshot: OrgSnapshot,
        kind: StructureKind,
        settings: PlannerSettings,
    ): PayoutBreakdown? {
        val root = snapshot.root(kind) ?: return null
        return evaluateNode(snapshot, root.id, settings)
    }

    fun evaluateNode(
        snapshot: OrgSnapshot,
        nodeId: String,
        settings: PlannerSettings,
    ): PayoutBreakdown {
        val node = snapshot.node(nodeId)
            ?: return evaluateInputs(
                personalPv = 0.0,
                personalBv = 0.0,
                frontline = emptyList(),
                settings = settings,
            )
        val frontline = snapshot.frontline(nodeId).map { child ->
            val gv = snapshot.groupVolume(child.id)
            FrontlineVolume(
                name = snapshot.displayName(child),
                groupPv = gv.pv,
                groupBv = gv.bv,
            )
        }
        return evaluateInputs(
            personalPv = node.personalPv,
            personalBv = node.personalBv,
            frontline = frontline,
            settings = settings,
        )
    }

    fun evaluateInputs(
        personalPv: Double,
        personalBv: Double,
        frontline: List<FrontlineVolume>,
        settings: PlannerSettings,
    ): PayoutBreakdown {
        val frontlineGroup = frontline.fold(Volume.ZERO) { acc, leg ->
            acc + Volume(leg.groupPv, leg.groupBv)
        }
        val group = Volume(personalPv, personalBv) + frontlineGroup
        val yourPercent = config.bracketFor(group.pv).percent

        var differential = 0.0
        var leadershipBv = 0.0
        var maxPercentLegs = 0
        var rubyPv = personalPv
        var rubyBv = personalBv

        for (leg in frontline) {
            val childPercent = config.bracketFor(leg.groupPv).percent
            differential += max(0.0, yourPercent - childPercent) * leg.groupBv
            if (childPercent >= config.maxPerformancePercent - 1e-9) {
                maxPercentLegs += 1
                leadershipBv += leg.groupBv
            } else {
                rubyPv += leg.groupPv
                rubyBv += leg.groupBv
            }
        }

        val personalPerformance = yourPercent * personalBv
        val performanceBonus = personalPerformance + differential

        val qualifiesLeadership = settings.includeLeadershipBonus &&
            yourPercent >= config.maxPerformancePercent - 1e-9 &&
            maxPercentLegs >= config.leadershipMinMaxPercentLegs
        val leadershipBonus = if (qualifiesLeadership) {
            config.leadershipBonusPercent * leadershipBv
        } else {
            0.0
        }

        val rubyPercent = if (settings.includeRubyBonus) {
            config.rubyTiers
                .filter { rubyPv >= it.minRubyPv }
                .maxByOrNull { it.minRubyPv }
                ?.percent ?: 0.0
        } else {
            0.0
        }
        val rubyBonus = rubyPercent * rubyBv

        val customerProfit = if (settings.includeCustomerProfit) {
            settings.customerProfitPercent * personalBv
        } else {
            0.0
        }

        val estimated = performanceBonus + customerProfit + leadershipBonus + rubyBonus
        val currentRank = qualifyRank(group.pv, maxPercentLegs, rubyPv)
        val nextRank = config.ranks
            .dropWhile { it.id != currentRank.id }
            .drop(1)
            .firstOrNull()

        return PayoutBreakdown(
            group = group,
            personal = Volume(personalPv, personalBv),
            performancePercent = yourPercent,
            personalPerformance = personalPerformance,
            differential = differential,
            performanceBonus = performanceBonus,
            customerProfit = customerProfit,
            leadershipBonus = leadershipBonus,
            rubyBonus = rubyBonus,
            rubyPv = rubyPv,
            rubyBv = rubyBv,
            estimatedMonthly = estimated,
            currentRank = currentRank,
            nextRank = nextRank,
            maxPercentLegs = maxPercentLegs,
            frontlineCount = frontline.size,
        )
    }

    fun qualifyRank(groupPv: Double, maxPercentLegs: Int, rubyPv: Double): RankDefinition {
        return config.ranks
            .filter { rank ->
                groupPv + 1e-6 >= rank.minGroupPv &&
                    maxPercentLegs >= rank.minMaxPercentLegs &&
                    rubyPv + 1e-6 >= rank.minRubyPv
            }
            .maxBy { rank ->
                config.ranks.indexOfFirst { it.id == rank.id }
            }
    }

    fun neededForRank(
        current: PayoutBreakdown,
        target: RankDefinition,
    ): RankNeed {
        return RankNeed(
            target = target,
            pvNeeded = max(0.0, target.minGroupPv - current.group.pv),
            maxPercentLegsNeeded = max(0, target.minMaxPercentLegs - current.maxPercentLegs),
            rubyPvNeeded = max(0.0, target.minRubyPv - current.rubyPv),
        )
    }

    fun bvForPv(pv: Double, settings: PlannerSettings): Double = pv * settings.bvPerPv
}

data class RankNeed(
    val target: RankDefinition,
    val pvNeeded: Double,
    val maxPercentLegsNeeded: Int,
    val rubyPvNeeded: Double,
) {
    val met: Boolean
        get() = pvNeeded <= 0.0 && maxPercentLegsNeeded <= 0 && rubyPvNeeded <= 0.0
}
