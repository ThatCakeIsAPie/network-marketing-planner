package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.Volume
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

data class FrontlineVolume(
    val name: String,
    val groupPv: Double,
    val groupBv: Double,
    val hasQualified25PercentDownline: Boolean = false,
    val isQualifiedPlatinum: Boolean = false,
    val performancePercent: Double? = null,
    val leadershipRollUp: Double? = null,
)

@Serializable
data class CorePlusStatus(
    val baselineMet: Boolean,
    val silverProducerMonth: Boolean,
    val pqMonth: Boolean,
    val fqCount: Int,
    val width: Int,
    val depth: Int,
    val bfiMonth: Boolean,
    val bbiMonth: Boolean,
    val bronzePin: Boolean,
    val ssiMonth: Boolean,
    val performancePlusPercent: Double,
    val performancePlusAmount: Double,
    val csiAmount: Double,
    val bfiAmount: Double,
    val bbiAmount: Double,
    val pqAnnualEstimate: Double,
    val pqAnnualLabel: String,
    val ttciNotes: List<String>,
    val progressNotes: List<String>,
)

@Serializable
data class PayoutBreakdown(
    val group: Volume,
    val personal: Volume,
    val passUp: Volume,
    val ruby: Volume,
    val totalDownline: Volume,
    val performancePercent: Double,
    val rule412Factor: Double,
    val personalPerformance: Double,
    val differential: Double,
    val performanceBonus: Double,
    val retailMargin: Double,
    val leadershipBonus: Double,
    val leadershipPassedToSponsor: Double,
    val depthBonus: Double,
    val rubyBonus: Double,
    val rubyPv: Double,
    val rubyBv: Double,
    val estimatedMonthly: Double,
    val currentRank: RankDefinition,
    val nextRank: RankDefinition?,
    val maxPercentLegs: Int,
    val frontlineCount: Int,
    val silverProducerMonth: Boolean,
    val corePlus: CorePlusStatus,
) {
    val customerProfit: Double get() = retailMargin

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
 * North America PY2027 Core Plan engine. Keep formula changes here;
 * keep rates in [AmwayNaPy2027] / [CompensationConfig].
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
        val node = snapshot.node(nodeId) ?: return evaluateInputs(0.0, 0.0, emptyList(), settings)
        val frontline = snapshot.frontline(nodeId).map { child ->
            val childPayout = evaluateNode(snapshot, child.id, settings)
            val at25 = childPayout.silverProducerMonth ||
                childPayout.performancePercent + 1e-9 >= config.maxPerformancePercent
            val rollUp = when {
                childPayout.leadershipPassedToSponsor > 1e-9 -> childPayout.leadershipPassedToSponsor
                at25 -> config.leadershipBonusPercent * childPayout.group.bv
                childPayout.maxPercentLegs > 0 -> config.leadershipBonusPercent * childPayout.group.bv
                else -> null
            }
            FrontlineVolume(
                name = snapshot.displayName(child),
                groupPv = childPayout.group.pv,
                groupBv = childPayout.group.bv,
                hasQualified25PercentDownline = childPayout.maxPercentLegs > 0,
                isQualifiedPlatinum = false,
                performancePercent = childPayout.performancePercent,
                leadershipRollUp = rollUp,
            )
        }
        return evaluateInputs(node.personalPv, node.personalBv, frontline, settings)
    }

    fun evaluateInputs(
        personalPv: Double,
        personalBv: Double,
        frontline: List<FrontlineVolume>,
        settings: PlannerSettings,
    ): PayoutBreakdown {
        val analyzed = frontline.map { leg ->
            val rawPercent = leg.performancePercent ?: config.bracketFor(leg.groupPv).percent
            val at25 = rawPercent + 1e-9 >= config.maxPerformancePercent ||
                isSilverProducer(leg.groupPv, if (leg.hasQualified25PercentDownline) 1 else 0)
            Triple(leg, if (at25) config.maxPerformancePercent else rawPercent, at25)
        }

        var passUp = Volume.ZERO
        var ruby = Volume(personalPv, personalBv)
        var totalDownline = Volume(personalPv, personalBv)
        var maxPercentLegs = 0
        var depthLegs = 0
        val incomingByLeg = mutableListOf<Double>()
        var nonQualifyingContribution = 0.0

        for ((leg, childPercent, at25) in analyzed) {
            totalDownline += Volume(leg.groupPv, leg.groupBv)
            val excludeFromGroup = at25 || leg.isQualifiedPlatinum
            if (at25) {
                maxPercentLegs += 1
                incomingByLeg += leg.leadershipRollUp ?: (config.leadershipBonusPercent * leg.groupBv)
                if (leg.hasQualified25PercentDownline) depthLegs += 1
            } else if (leg.hasQualified25PercentDownline) {
                incomingByLeg += leg.leadershipRollUp ?: (config.leadershipBonusPercent * leg.groupBv)
            } else {
                nonQualifyingContribution += config.leadershipBonusPercent * leg.groupBv
            }
            if (!excludeFromGroup) {
                passUp += Volume(leg.groupPv, leg.groupBv)
                ruby += Volume(leg.groupPv, leg.groupBv)
            }
        }

        val group = Volume(personalPv, personalBv) + passUp
        val bracketPercent = config.bracketFor(group.pv).percent
        val highestFrontline = analyzed.maxOfOrNull { it.second } ?: 0.0
        val silverProducer = isSilverProducer(group.pv, maxPercentLegs)
        val performancePercent = max(
            bracketPercent,
            if (silverProducer || highestFrontline >= config.maxPerformancePercent) {
                max(highestFrontline, if (silverProducer) config.maxPerformancePercent else 0.0)
            } else {
                highestFrontline
            },
        )

        val rule412 = rule412Factor(settings)
        val bonusablePersonalBv = personalBv * rule412
        val personalPerformance = performancePercent * bonusablePersonalBv

        var differential = 0.0
        if (settings.meetsRule413) {
            for ((leg, childPercent, _) in analyzed) {
                differential += max(0.0, performancePercent - childPercent) * leg.groupBv
            }
        }
        val performanceBonus = personalPerformance + differential

        val customerSalesBv = personalBv * settings.customerSalesPercent.coerceIn(0.0, 1.0)
        val retailMargin = if (settings.includeRetailMargin) {
            settings.retailMarginPercent * customerSalesBv
        } else {
            0.0
        }

        val leadershipQualifies = LeadershipBonus.qualifies(
            silverProducer = silverProducer,
            maxPercentLegs = max(maxPercentLegs, incomingByLeg.size),
            rubyPv = ruby.pv,
            minRubyPvForSingleLeg = config.silverProducerGroupPvWithOneLeg,
        )
        val leadershipIncoming = if (incomingByLeg.isEmpty()) {
            emptyList()
        } else {
            // Non-25% frontline 6% contributions attach to the roll-up pool (BRG intermediates).
            incomingByLeg.mapIndexed { index, value ->
                if (index == 0) value + nonQualifyingContribution else value
            }
        }
        val leadership = if (settings.includeLeadershipBonus) {
            LeadershipBonus.settle(
                ownGroupBv = group.bv,
                incomingByLeg = leadershipIncoming,
                leadershipPercent = config.leadershipBonusPercent,
                publishedLba = config.publishedLba(settings.bvPerPv),
                qualifies = leadershipQualifies,
            )
        } else {
            LeadershipBonus.Settlement(0.0, 0.0)
        }

        val depthBonus = if (settings.includeDepthBonus && maxPercentLegs >= 3 && depthLegs >= 1) {
            config.depthBonusPercent * analyzed
                .filter { it.third && it.first.hasQualified25PercentDownline }
                .sumOf { it.first.groupBv }
        } else {
            0.0
        }

        val rubyBonus = if (settings.includeRubyBonus && ruby.pv + 1e-9 >= config.rubyBonusMinPv) {
            config.rubyBonusPercent * ruby.bv
        } else {
            0.0
        }

        val plusPercent = if (settings.includePerformancePlus && silverProducer && meetsBaseline(personalPv, settings)) {
            when {
                ruby.pv + 1e-9 >= config.performanceEliteMinPv -> config.performanceElitePercent
                ruby.pv + 1e-9 >= config.performancePlusMinPv -> config.performancePlusPercent
                else -> 0.0
            }
        } else {
            0.0
        }
        val plusAmount = plusPercent * ruby.bv

        val vcsBv = personalBv * settings.vcsPercent.coerceIn(0.0, 1.0)
        val csiAmount = if (settings.includeCsi && settings.csiEligible && performancePercent <= 0.09 + 1e-9) {
            min(config.csiMonthlyCap, max(0.0, config.csiTargetPercent - performancePercent) * vcsBv)
        } else {
            0.0
        }

        val legsAtLeast = { minPercent: Double ->
            analyzed.count { it.second + 1e-9 >= minPercent }
        }
        val bfiMonth = settings.bfiEligible && meetsBaseline(personalPv, settings) &&
            performancePercent + 1e-9 >= 0.09 && legsAtLeast(0.03) >= 3
        val bbiMonth = settings.bbiEligible && meetsBaseline(personalPv, settings) &&
            performancePercent + 1e-9 >= 0.18 && legsAtLeast(0.06) >= 3
        val bfiAmount = if (bfiMonth) config.bfiPerformanceMultiplier * performanceBonus else 0.0
        val bbiAmount = if (bbiMonth) config.bbiPerformanceMultiplier * performanceBonus else 0.0

        val pqMonth = isPqMonth(ruby.pv, maxPercentLegs, settings.isPlatinumOrAbove)
        val fqCount = maxPercentLegs
        val corePlus = buildCorePlus(
            personalPv = personalPv,
            settings = settings,
            silverProducer = silverProducer,
            pqMonth = pqMonth,
            fqCount = fqCount,
            width = frontline.size,
            depth = if (depthLegs > 0) 2 else 1,
            bfiMonth = bfiMonth,
            bbiMonth = bbiMonth,
            plusPercent = plusPercent,
            plusAmount = plusAmount,
            csiAmount = csiAmount,
            bfiAmount = bfiAmount,
            bbiAmount = bbiAmount,
            rubyPv = ruby.pv,
        )

        val estimated = performanceBonus + retailMargin + leadership.kept + depthBonus +
            rubyBonus + plusAmount + csiAmount + bfiAmount + bbiAmount

        val ytdSilver = settings.silverProducerMonthsPy + if (silverProducer) 1 else 0
        val ytdGroup = settings.groupPvPy + group.pv
        val ytdDownline = settings.totalDownlinePvPy + totalDownline.pv
        val currentRank = qualifyRank(
            groupPv = group.pv,
            maxPercentLegs = maxPercentLegs,
            rubyPv = ruby.pv,
            silverProducer = silverProducer,
            silverProducerMonths = ytdSilver,
            bbiMonth = bbiMonth,
            ytdGroupPv = ytdGroup,
            ytdDownlinePv = ytdDownline,
        )
        val nextRank = config.ranks
            .dropWhile { it.id != currentRank.id }
            .drop(1)
            .firstOrNull()

        return PayoutBreakdown(
            group = group,
            personal = Volume(personalPv, personalBv),
            passUp = passUp,
            ruby = ruby,
            totalDownline = totalDownline,
            performancePercent = performancePercent,
            rule412Factor = rule412,
            personalPerformance = personalPerformance,
            differential = differential,
            performanceBonus = performanceBonus,
            retailMargin = retailMargin,
            leadershipBonus = leadership.kept,
            leadershipPassedToSponsor = leadership.passedToSponsor,
            depthBonus = depthBonus,
            rubyBonus = rubyBonus,
            rubyPv = ruby.pv,
            rubyBv = ruby.bv,
            estimatedMonthly = estimated,
            currentRank = currentRank,
            nextRank = nextRank,
            maxPercentLegs = maxPercentLegs,
            frontlineCount = frontline.size,
            silverProducerMonth = silverProducer,
            corePlus = corePlus,
        )
    }

    fun isSilverProducer(groupPv: Double, maxPercentLegs: Int): Boolean =
        groupPv + 1e-9 >= config.silverProducerGroupPv ||
            (groupPv + 1e-9 >= config.silverProducerGroupPvWithOneLeg && maxPercentLegs >= 1) ||
            maxPercentLegs >= 2

    fun isPqMonth(rubyPv: Double, maxPercentLegs: Int, platinumOrAbove: Boolean): Boolean {
        if (!platinumOrAbove) return false
        return rubyPv + 1e-9 >= 7_500.0 || (rubyPv + 1e-9 >= 4_000.0 && maxPercentLegs >= 1)
    }

    fun rule412Factor(settings: PlannerSettings): Double {
        val customer = settings.customerSalesPercent.coerceIn(0.0, 1.0)
        val vcs = settings.vcsPercent.coerceIn(0.0, 1.0)
        if (customer + 1e-9 >= config.rule412CustomerSalesMin && vcs + 1e-9 >= config.rule412VcsMin) {
            return 1.0
        }
        return min(
            customer / config.rule412CustomerSalesMin,
            vcs / config.rule412VcsMin,
        ).coerceIn(0.0, 1.0)
    }

    fun qualifyRank(
        groupPv: Double,
        maxPercentLegs: Int,
        rubyPv: Double,
        silverProducer: Boolean,
        silverProducerMonths: Int,
        bbiMonth: Boolean,
        ytdGroupPv: Double = 0.0,
        ytdDownlinePv: Double = 0.0,
    ): RankDefinition {
        val eligible = config.ranks.filter { rank ->
            when (rank.id) {
                RankIds.STARTER -> true
                RankIds.QUALIFIED -> groupPv + 1e-9 >= 100.0
                RankIds.BRONZE -> bbiMonth || groupPv + 1e-9 >= 2_500.0
                RankIds.SILVER -> silverProducer
                RankIds.GOLD -> silverProducer && silverProducerMonths >= 3
                RankIds.PLATINUM -> silverProducer && silverProducerMonths >= 6
                RankIds.FOUNDERS_PLATINUM -> silverProducerMonths >= 12 ||
                    (silverProducerMonths >= 10 && (ytdGroupPv + 1e-9 >= 90_000.0 || ytdDownlinePv + 1e-9 >= 108_000.0))
                RankIds.RUBY -> silverProducer && rubyPv + 1e-9 >= 15_000.0 &&
                    silverProducerMonths >= rank.minSilverProducerMonths
                RankIds.EMERALD -> silverProducer && maxPercentLegs >= 3 && silverProducerMonths >= 6
                RankIds.DIAMOND -> silverProducer && maxPercentLegs >= 6 && silverProducerMonths >= 6
                else -> groupPv + 1e-9 >= rank.minGroupPv &&
                    maxPercentLegs >= rank.minMaxPercentLegs &&
                    rubyPv + 1e-9 >= rank.minRubyPv
            }
        }
        return eligible.maxBy { rank -> config.ranks.indexOfFirst { it.id == rank.id } }
    }

    fun neededForRank(current: PayoutBreakdown, target: RankDefinition): RankNeed {
        val silverNeeded = if (target.id == RankIds.SILVER || target.minGroupPv >= 7_500) {
            if (current.silverProducerMonth) 0.0
            else max(0.0, config.silverProducerGroupPv - current.group.pv)
        } else {
            max(0.0, target.minGroupPv - current.group.pv)
        }
        return RankNeed(
            target = target,
            pvNeeded = silverNeeded,
            maxPercentLegsNeeded = max(0, target.minMaxPercentLegs - current.maxPercentLegs),
            rubyPvNeeded = max(0.0, target.minRubyPv - current.rubyPv),
            silverProducerMonthsNeeded = max(0, target.minSilverProducerMonths - 0),
            hint = silverProducerHint(current),
        )
    }

    fun silverProducerHint(current: PayoutBreakdown): String {
        if (current.silverProducerMonth) return "This month already qualifies as a Silver Producer (Q) month."
        val to7500 = max(0.0, config.silverProducerGroupPv - current.group.pv)
        val to2500 = max(0.0, config.silverProducerGroupPvWithOneLeg - current.group.pv)
        val legs = current.maxPercentLegs
        return "Q-month paths: ${to7500.toInt()} more Group PV to 7,500; " +
            "or ${to2500.toInt()} more Group PV plus one 25% leg (have $legs); " +
            "or ${max(0, 2 - legs)} more 25% legs."
    }

    fun bvForPv(pv: Double, settings: PlannerSettings): Double = pv * settings.bvPerPv

    fun meetsBaseline(personalPv: Double, settings: PlannerSettings): Boolean =
        personalPv + 1e-9 >= config.baselinePersonalPv &&
            settings.vcsPercent + 1e-9 >= config.rule412VcsMin

    private fun buildCorePlus(
        personalPv: Double,
        settings: PlannerSettings,
        silverProducer: Boolean,
        pqMonth: Boolean,
        fqCount: Int,
        width: Int,
        depth: Int,
        bfiMonth: Boolean,
        bbiMonth: Boolean,
        plusPercent: Double,
        plusAmount: Double,
        csiAmount: Double,
        bfiAmount: Double,
        bbiAmount: Double,
        rubyPv: Double,
    ): CorePlusStatus {
        val baseline = meetsBaseline(personalPv, settings)
        val ytdPq = settings.pqMonthsPy + if (pqMonth) 1 else 0
        val ytdRuby = settings.rubyPvPy + rubyPv
        val pqTier = config.pqIncentiveTiers
            .filter { ytdPq >= it.minPqMonths && ytdRuby + 1e-9 >= it.minAnnualRubyPv }
            .maxByOrNull { it.annualAmount }
        val ytdSilver = settings.silverProducerMonthsPy + if (silverProducer) 1 else 0
        val consecutive = if (silverProducer) settings.consecutiveSilverMonths + 1 else 0
        val notes = buildList {
            add("Width (frontline): $width. Depth (25% below a 25% leg): ${if (depth >= 2) "yes" else "not this month"}.")
            add("YTD Silver Producer months including this snapshot: $ytdSilver. Consecutive Q months: $consecutive.")
            if (ytdSilver >= 6 && consecutive >= 3) add("Platinum first-time path: 6 Q months with 3 consecutive is met on these counters.")
            else add("Platinum first-time needs 6 Q months in 12 rolling with 3 consecutive (YTD $ytdSilver, consecutive $consecutive).")
            if (ytdSilver >= 12) add("Founders Platinum: 12 Q months met on these counters.")
            else if (ytdSilver >= 10) add("Founders Platinum VE: 10–11 Q months plus 90,000 Group PV or 108,000 Total Downline PV.")
            add("FQ this month: $fqCount (max 12 per leg per PY). PQ this month: ${if (pqMonth) "yes" else "no"} (Platinum+; 7,500 Ruby PV or 4,000 Ruby PV + 25% leg).")
            add("SSI: new-IBO baseline months stored as ${settings.newIboBaselineMonths}. Dollar table is not encoded — track 150 PPV + 60% VCS without a missed month after month two.")
            add("FSI (Founders Sales Incentive): progress follows Founders Platinum / VE; payout table not encoded.")
            add("Emerald/Diamond profit-sharing schedules are not encoded; pins use 3 / 6 legs at Silver Producer for six months.")
        }
        val ttci = config.twoTimeCash.map {
            "${it.summary}: first year ${it.firstYearAmount.toInt()}, requal ${it.secondYearAmount.toInt()}."
        }
        return CorePlusStatus(
            baselineMet = baseline,
            silverProducerMonth = silverProducer,
            pqMonth = pqMonth,
            fqCount = fqCount,
            width = width,
            depth = depth,
            bfiMonth = bfiMonth,
            bbiMonth = bbiMonth,
            bronzePin = bbiMonth,
            ssiMonth = settings.newIboBaselineMonths > 0 && baseline,
            performancePlusPercent = plusPercent,
            performancePlusAmount = plusAmount,
            csiAmount = csiAmount,
            bfiAmount = bfiAmount,
            bbiAmount = bbiAmount,
            pqAnnualEstimate = pqTier?.annualAmount ?: 0.0,
            pqAnnualLabel = pqTier?.label ?: "PQ incentive needs 6+ PQ months (Platinum+)",
            ttciNotes = ttci,
            progressNotes = notes,
        )
    }
}

data class RankNeed(
    val target: RankDefinition,
    val pvNeeded: Double,
    val maxPercentLegsNeeded: Int,
    val rubyPvNeeded: Double,
    val silverProducerMonthsNeeded: Int = 0,
    val hint: String = "",
) {
    val met: Boolean
        get() = pvNeeded <= 0.0 && maxPercentLegsNeeded <= 0 && rubyPvNeeded <= 0.0
}
