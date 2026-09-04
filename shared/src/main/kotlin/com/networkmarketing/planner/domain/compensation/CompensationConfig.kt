package com.networkmarketing.planner.domain.compensation

import kotlinx.serialization.Serializable

/**
 * One row of the monthly performance-bonus schedule.
 * [percent] is a fraction (0.25 = 25% of BV).
 * [maxPvExclusive] is the exclusive upper bound; null means no upper bound.
 */
@Serializable
data class PerformanceBracket(
    val minPv: Double,
    val maxPvExclusive: Double?,
    val percent: Double,
)

@Serializable
data class RankDefinition(
    val id: String,
    val title: String,
    val minGroupPv: Double = 0.0,
    val minMaxPercentLegs: Int = 0,
    val minRubyPv: Double = 0.0,
    val minSilverProducerMonths: Int = 0,
    val summary: String,
)

@Serializable
data class CorePlusPqTier(
    val minPqMonths: Int,
    val minAnnualRubyPv: Double = 0.0,
    val annualAmount: Double,
    val label: String,
)

@Serializable
data class TwoTimeCashIncentive(
    val rankId: String,
    val firstYearAmount: Double,
    val secondYearAmount: Double,
    val summary: String,
)

/**
 * All rates and tables for a named compensation profile.
 * Keep payout math in [CompensationEngine] / [LeadershipBonus]; keep numbers here.
 */
@Serializable
data class CompensationConfig(
    val profileId: String,
    val profileTitle: String,
    val sourceNote: String,
    val bvPerPv: Double,
    val brackets: List<PerformanceBracket>,
    val ranks: List<RankDefinition>,
    val maxPerformancePercent: Double,
    val leadershipBonusPercent: Double,
    val depthBonusPercent: Double,
    val rubyBonusPercent: Double,
    val rubyBonusMinPv: Double,
    val performancePlusMinPv: Double,
    val performancePlusPercent: Double,
    val performanceEliteMinPv: Double,
    val performanceElitePercent: Double,
    val silverProducerGroupPv: Double,
    val silverProducerGroupPvWithOneLeg: Double,
    val rule412CustomerSalesMin: Double,
    val rule412VcsMin: Double,
    val baselinePersonalPv: Double,
    val baselineAnnualPersonalPv: Double,
    val retailMargins: List<Double>,
    val pqIncentiveTiers: List<CorePlusPqTier>,
    val twoTimeCash: List<TwoTimeCashIncentive>,
    val bfiPerformanceMultiplier: Double,
    val bbiPerformanceMultiplier: Double,
    val csiTargetPercent: Double,
    val csiMonthlyCap: Double,
    val assumptions: List<String>,
) {
    fun bracketFor(groupPv: Double): PerformanceBracket =
        brackets.filter { groupPv + 1e-9 >= it.minPv }.maxByOrNull { it.minPv }
            ?: brackets.minBy { it.minPv }

    fun rank(id: String): RankDefinition =
        ranks.firstOrNull { it.id == id } ?: ranks.first()

    fun publishedLba(bvPerPv: Double = this.bvPerPv): Double =
        leadershipBonusPercent * silverProducerGroupPv * bvPerPv

    fun publishedMda(bvPerPv: Double = this.bvPerPv): Double =
        publishedLba(bvPerPv) / 6.0

    fun bvForPv(pv: Double, bvPerPv: Double = this.bvPerPv): Double = pv * bvPerPv

    companion object {
        fun default(): CompensationConfig = AmwayNaPy2027.PROFILE
    }
}
