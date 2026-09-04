package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.domain.model.RankIds

/**
 * Unofficial planning profile matching North America Core Plan / Core Plus
 * tables for Performance Year 2027 (IBO year 1 Sep 2026 – 31 Aug 2027).
 *
 * This app is **not affiliated with, endorsed by, or sponsored by** any
 * company. Tables are encoded for education and planning only.
 */
object AmwayNaPy2027 {
    const val PROFILE_ID = "AmwayNA_PY2027"

    val ASSUMPTIONS: List<String> = listOf(
        "Profile $PROFILE_ID. Unofficial planning tool — not affiliated with, endorsed by, or sponsored by any company. Income is not guaranteed.",
        "BV:PV = 3.43 as of 1 September 2026. Change the ratio in Goals if your statement uses another figure.",
        "Performance schedule uses Group PV (Personal + pass-up from in-market legs that are not at 25% this month). 25% / qualified-Platinum volume is excluded from Group and Ruby/Side volume.",
        "You are paid at least the highest frontline performance %. Silver Producer (Q) month = 7,500 Group PV, OR 2,500 Group PV + one 25% leg, OR two 25% legs the same month.",
        "Personal Performance Bonus = schedule% × Personal BV after Rule 4.12 proration. Full BV requires ≥70% of Personal Volume from customer sales and ≥60% VCS; otherwise Personal BV is prorated (min of those two ratios).",
        "Differential Bonus = (your% − frontline%) × that frontline’s Group BV when yours is higher. Rule 4.13 (50 VCS PV or 10 customers below Platinum) is a Goals toggle.",
        "Ruby/Side Volume = Personal + pass-up from legs not at 25% (and not a qualified Platinum). Core Plan Ruby Bonus = 2% of Ruby BV at ≥15,000 Ruby PV.",
        "Performance Plus (Core Plus): 7,500–12,499.99 Ruby PV → 2% of Ruby BV. Performance Elite: ≥12,500 Ruby PV → 4% of Ruby BV. Highest of Plus/Elite applies; Ruby Bonus stacks on top of that multiplier.",
        "Leadership Bonus: qualify with one 25% leg plus ≥2,500 PV outside that leg, OR two 25% legs. Bottom-up 6% of Group BV with Published LBA = 6% × 7,500 × BV:PV ($1,543.50 at 3.43). Intermediate IBOs under 25% contribute 6% of their BV to the roll-up without keeping it. See LeadershipBonus for the documented examples.",
        "Depth Bonus: 3+ in-market 25% legs and at least one of those has a 25% downline. Planner pays 1% of those depth legs’ Group BV. MDA ($257.25 at 3.43) and top-down splits to further upline are omitted.",
        "Retail margin is selectable 10% / 15% / 20% of customer-sales BV (Personal BV × customer-sales %). Official retail markup is typically ~10% of IBO cost vs retail; 15/20 are planner what-ifs.",
        "Discretionary Core Plus (SSI, FSI, BFI, BBI, CSI, TTCI, PQ, FQ) uses documented monthly rules plus year-to-date inputs on Goals. Annual profit-sharing (Emerald/Diamond) is progress-only.",
        "Baseline for discretionary incentives and events: 150 Personal PV/month (1,800/year) and 60% VCS.",
        "Single-month snapshot plus optional YTD counters. Real pin ranks often need consecutive months, in-market vs international splits, and compliance reviews this planner does not run.",
    )

    val PROFILE: CompensationConfig = CompensationConfig(
        profileId = PROFILE_ID,
        profileTitle = "North America PY2027 (unofficial)",
        sourceNote = "Planning profile aligned to North America Core Plan / Core Plus " +
            "tables for Performance Year 2027 (updated September 2026). " +
            "Unofficial encoding — not a company publication and not an endorsement.",
        bvPerPv = 3.43,
        brackets = listOf(
            PerformanceBracket(minPv = 0.0, maxPvExclusive = 100.0, percent = 0.00),
            PerformanceBracket(minPv = 100.0, maxPvExclusive = 300.0, percent = 0.03),
            PerformanceBracket(minPv = 300.0, maxPvExclusive = 600.0, percent = 0.06),
            PerformanceBracket(minPv = 600.0, maxPvExclusive = 1_000.0, percent = 0.09),
            PerformanceBracket(minPv = 1_000.0, maxPvExclusive = 1_500.0, percent = 0.12),
            PerformanceBracket(minPv = 1_500.0, maxPvExclusive = 2_500.0, percent = 0.15),
            PerformanceBracket(minPv = 2_500.0, maxPvExclusive = 4_000.0, percent = 0.18),
            PerformanceBracket(minPv = 4_000.0, maxPvExclusive = 6_000.0, percent = 0.21),
            PerformanceBracket(minPv = 6_000.0, maxPvExclusive = 7_500.0, percent = 0.23),
            PerformanceBracket(minPv = 7_500.0, maxPvExclusive = null, percent = 0.25),
        ),
        ranks = listOf(
            RankDefinition(RankIds.STARTER, "Starter", summary = "Registered. Building personal and customer volume."),
            RankDefinition(RankIds.QUALIFIED, "Qualified (3%)", minGroupPv = 100.0, summary = "First performance-bonus bracket: 100–299.99 Group PV."),
            RankDefinition(RankIds.BRONZE, "Bronze", minGroupPv = 2_500.0, summary = "Bronze pin: first Bronze Builder month (18%+, three 6% legs, baseline). Tracked as 18% Group PV for the monthly snapshot."),
            RankDefinition(RankIds.SILVER, "Silver Producer", minGroupPv = 7_500.0, summary = "Qualified month (Q): 7,500 Group PV, or 2,500 Group PV + one 25% leg, or two 25% legs in the same month."),
            RankDefinition(RankIds.GOLD, "Gold Producer", minGroupPv = 7_500.0, minSilverProducerMonths = 3, summary = "Three Silver Producer months in the Performance Year (pin). Monthly snapshot still uses Q-month math."),
            RankDefinition(RankIds.PLATINUM, "Platinum", minGroupPv = 7_500.0, minSilverProducerMonths = 6, summary = "First-time: ≥6 Silver Producer months in a 12-month rolling period with 3 consecutive. Requal: ≥6 Q months in the PY."),
            RankDefinition(RankIds.RUBY, "Ruby", minGroupPv = 7_500.0, minRubyPv = 15_000.0, minSilverProducerMonths = 6, summary = "Platinum-track plus 15,000 Ruby PV in a month (Core Plan Ruby Bonus)."),
            RankDefinition(RankIds.FOUNDERS_PLATINUM, "Founders Platinum", minGroupPv = 7_500.0, minSilverProducerMonths = 12, summary = "12 Silver Producer months in the PY. Volume equivalency: 10–11 Q months with 90,000 Group PV or 108,000 Total Downline PV."),
            RankDefinition(RankIds.EMERALD, "Emerald", minGroupPv = 7_500.0, minMaxPercentLegs = 3, minSilverProducerMonths = 6, summary = "Placeholder pin: Platinum plus three legs at Silver Producer for six months of the PY. Profit-sharing tables are not encoded."),
            RankDefinition(RankIds.DIAMOND, "Diamond", minGroupPv = 7_500.0, minMaxPercentLegs = 6, minSilverProducerMonths = 6, summary = "Placeholder pin: six legs at Silver Producer for six months of the PY. Profit-sharing tables are not encoded."),
        ),
        maxPerformancePercent = 0.25,
        leadershipBonusPercent = 0.06,
        depthBonusPercent = 0.01,
        rubyBonusPercent = 0.02,
        rubyBonusMinPv = 15_000.0,
        performancePlusMinPv = 7_500.0,
        performancePlusPercent = 0.02,
        performanceEliteMinPv = 12_500.0,
        performanceElitePercent = 0.04,
        silverProducerGroupPv = 7_500.0,
        silverProducerGroupPvWithOneLeg = 2_500.0,
        rule412CustomerSalesMin = 0.70,
        rule412VcsMin = 0.60,
        baselinePersonalPv = 150.0,
        baselineAnnualPersonalPv = 1_800.0,
        retailMargins = listOf(0.10, 0.15, 0.20),
        pqIncentiveTiers = listOf(
            CorePlusPqTier(6, 0.0, 6_000.0, "6–11 PQ months"),
            CorePlusPqTier(12, 0.0, 18_000.0, "12 PQ months"),
            CorePlusPqTier(12, 90_000.0, 20_000.0, "12 PQ + 90,000 Ruby PV"),
        ),
        twoTimeCash = listOf(
            TwoTimeCashIncentive(RankIds.PLATINUM, 1_500.0, 3_500.0, "TTCI Platinum (documented Core Plus amounts; confirm current PY table)."),
            TwoTimeCashIncentive(RankIds.FOUNDERS_PLATINUM, 2_500.0, 7_500.0, "TTCI Founders Platinum (documented Core Plus amounts; confirm current PY table)."),
        ),
        bfiPerformanceMultiplier = 0.30,
        bbiPerformanceMultiplier = 0.40,
        csiTargetPercent = 0.10,
        csiMonthlyCap = 75.0,
        assumptions = ASSUMPTIONS,
    )
}

