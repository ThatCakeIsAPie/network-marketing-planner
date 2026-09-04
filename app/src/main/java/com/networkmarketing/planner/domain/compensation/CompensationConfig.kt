package com.networkmarketing.planner.domain.compensation

/**
 * One row of the monthly performance-bonus schedule.
 * [percent] is a fraction (0.25 = 25% of BV).
 */
data class PerformanceBracket(
    val minPv: Double,
    val percent: Double,
)

data class RubyBonusTier(
    val minRubyPv: Double,
    val percent: Double,
)

data class RankDefinition(
    val id: String,
    val title: String,
    val minGroupPv: Double,
    val minMaxPercentLegs: Int = 0,
    val minRubyPv: Double = 0.0,
    val summary: String,
)

/**
 * All knobs for the default compensation engine live here.
 *
 * Defaults are an unofficial, simplified model inspired by common
 * North American PV/BV performance-bonus plans (25% schedule, 6%
 * leadership on breakaway volume, ruby-volume adders). Markets differ.
 * See README for the full assumption list.
 */
data class CompensationConfig(
    val brackets: List<PerformanceBracket>,
    val ranks: List<RankDefinition>,
    val rubyTiers: List<RubyBonusTier>,
    val maxPerformancePercent: Double,
    val leadershipBonusPercent: Double,
    val leadershipMinMaxPercentLegs: Int,
    val assumptions: List<String>,
) {
    fun bracketFor(groupPv: Double): PerformanceBracket =
        brackets.filter { groupPv >= it.minPv }.maxByOrNull { it.minPv }
            ?: brackets.minBy { it.minPv }

    fun rank(id: String): RankDefinition =
        ranks.firstOrNull { it.id == id } ?: ranks.first()

    companion object {
        fun default(): CompensationConfig = DefaultCompensation.US_STYLE
    }
}
```
