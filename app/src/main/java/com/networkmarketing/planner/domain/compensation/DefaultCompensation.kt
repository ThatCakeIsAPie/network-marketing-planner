package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.domain.model.RankIds

/**
 * Unofficial planning defaults. Not a company's published plan.
 *
 * Performance schedule mirrors a widely published North American-style
 * 3%–25% PV table. Rank names are generic. Pin qualification in real
 * businesses often requires consecutive months; this engine uses a
 * single-month snapshot so the planner can answer "what would it take
 * this month?"
 */
object DefaultCompensation {
    val US_STYLE: CompensationConfig = CompensationConfig(
        brackets = listOf(
            PerformanceBracket(minPv = 0.0, percent = 0.00),
            PerformanceBracket(minPv = 100.0, percent = 0.03),
            PerformanceBracket(minPv = 300.0, percent = 0.06),
            PerformanceBracket(minPv = 600.0, percent = 0.09),
            PerformanceBracket(minPv = 1_000.0, percent = 0.12),
            PerformanceBracket(minPv = 1_500.0, percent = 0.15),
            PerformanceBracket(minPv = 2_500.0, percent = 0.18),
            PerformanceBracket(minPv = 4_000.0, percent = 0.21),
            PerformanceBracket(minPv = 6_000.0, percent = 0.23),
            PerformanceBracket(minPv = 7_500.0, percent = 0.25),
        ),
        ranks = listOf(
            RankDefinition(
                id = RankIds.STARTER,
                title = "Starter",
                minGroupPv = 0.0,
                summary = "Registered, building personal volume.",
            ),
            RankDefinition(
                id = RankIds.QUALIFIED,
                title = "Qualified",
                minGroupPv = 100.0,
                summary = "Hits the first performance-bonus bracket (3%).",
            ),
            RankDefinition(
                id = RankIds.COORDINATOR,
                title = "Coordinator",
                minGroupPv = 1_500.0,
                summary = "15% performance bracket on group volume.",
            ),
            RankDefinition(
                id = RankIds.GOLD,
                title = "Gold",
                minGroupPv = 2_500.0,
                summary = "18% performance bracket on group volume.",
            ),
            RankDefinition(
                id = RankIds.SILVER,
                title = "Silver",
                minGroupPv = 7_500.0,
                summary = "Top performance bracket (25%) on group volume.",
            ),
            RankDefinition(
                id = RankIds.PLATINUM,
                title = "Platinum",
                minGroupPv = 7_500.0,
                minMaxPercentLegs = 1,
                summary = "Silver volume plus at least one max-bracket frontline (leadership track).",
            ),
            RankDefinition(
                id = RankIds.RUBY,
                title = "Ruby",
                minGroupPv = 7_500.0,
                minMaxPercentLegs = 1,
                minRubyPv = 15_000.0,
                summary = "Platinum-track plus 15,000 ruby PV (group PV minus max-bracket frontline PV).",
            ),
            RankDefinition(
                id = RankIds.EMERALD,
                title = "Emerald",
                minGroupPv = 7_500.0,
                minMaxPercentLegs = 3,
                summary = "Three frontline legs in the max performance bracket.",
            ),
            RankDefinition(
                id = RankIds.DIAMOND,
                title = "Diamond",
                minGroupPv = 7_500.0,
                minMaxPercentLegs = 6,
                summary = "Six frontline legs in the max performance bracket.",
            ),
        ),
        rubyTiers = listOf(
            RubyBonusTier(minRubyPv = 10_000.0, percent = 0.02),
            RubyBonusTier(minRubyPv = 12_500.0, percent = 0.04),
            RubyBonusTier(minRubyPv = 15_000.0, percent = 0.06),
        ),
        maxPerformancePercent = 0.25,
        leadershipBonusPercent = 0.06,
        leadershipMinMaxPercentLegs = 1,
        assumptions = listOf(
            "Single-month snapshot. Real pin ranks often need several qualifying months.",
            "Group PV/BV = personal volume + every descendant's personal volume.",
            "Performance % comes from group PV. Payout is % × personal BV plus the differential on each frontline: max(0, your% − their%) × their group BV.",
            "A 'max-bracket leg' is a frontline whose own group PV reaches the top performance bracket (7,500 PV / 25% in this table).",
            "Leadership bonus (default 6%) is estimated on the group BV of max-bracket frontline legs when you are also in the top bracket and have at least one such leg. Real leadership math includes adjustments this planner omits.",
            "Ruby PV = your group PV minus the group PV of max-bracket frontline legs. Ruby bonus uses combined unofficial tiers (2% / 4% / 6% of ruby BV at 10k / 12.5k / 15k ruby PV).",
            "Customer profit is a simple personal-BV × margin estimate (default 10%), not a published retail schedule.",
            "Default BV per PV is 3.43. Change it in Goals if your market uses another ratio.",
            "No annual bonuses, depth bonuses, market-specific rules, or compliance gates are modeled.",
            "Figures are planning estimates only. Income is not guaranteed.",
        ),
    )
}
