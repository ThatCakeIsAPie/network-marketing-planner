package com.networkmarketing.planner.domain.compensation

import com.networkmarketing.planner.data.seed.SampleData
import com.networkmarketing.planner.domain.model.Member
import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.PlannerSettings
import com.networkmarketing.planner.domain.model.RankIds
import com.networkmarketing.planner.domain.model.StructureKind
import com.networkmarketing.planner.domain.model.UserGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompensationEngineTest {

    private val engine = CompensationEngine()
    private val ratio3 = PlannerSettings(
        bvPerPv = 3.0,
        includeRetailMargin = false,
        includeLeadershipBonus = true,
        includeDepthBonus = false,
        includeRubyBonus = true,
        includePerformancePlus = false,
        customerSalesPercent = 0.70,
        vcsPercent = 0.60,
    )

    @Test
    fun profileIsAmwayNaPy2027() {
        assertEquals("AmwayNA_PY2027", engine.config().profileId)
        assertEquals(3.43, engine.config().bvPerPv, 0.0)
        assertEquals(1_543.50, engine.config().publishedLba(3.43), 0.01)
    }

    @Test
    fun bvPvHelperUsesConfiguredRatio() {
        assertEquals(3.43, engine.bvForPv(1.0, PlannerSettings()), 0.0)
        assertEquals(343.0, engine.config().bvForPv(100.0), 0.01)
    }

    @Test
    fun bracketTableMatchesPy2027Schedule() {
        val c = engine.config()
        assertEquals(0.00, c.bracketFor(0.0).percent, 0.0)
        assertEquals(0.03, c.bracketFor(100.0).percent, 0.0)
        assertEquals(0.03, c.bracketFor(299.99).percent, 0.0)
        assertEquals(0.06, c.bracketFor(300.0).percent, 0.0)
        assertEquals(0.12, c.bracketFor(1_200.0).percent, 0.0)
        assertEquals(0.23, c.bracketFor(7_499.99).percent, 0.0)
        assertEquals(0.25, c.bracketFor(7_500.0).percent, 0.0)
        assertEquals(0.25, c.bracketFor(20_000.0).percent, 0.0)
    }

    @Test
    fun performanceBonusIsPersonalSharePlusDifferential() {
        val childBv = 200.0 * 3.0
        val personalBv = 100.0 * 3.0
        val result = engine.evaluateInputs(
            personalPv = 100.0,
            personalBv = personalBv,
            frontline = listOf(FrontlineVolume("A", 200.0, childBv)),
            settings = ratio3.copy(includeLeadershipBonus = false, includeRubyBonus = false),
        )
        assertEquals(0.06, result.performancePercent, 0.0)
        assertEquals(0.06 * personalBv, result.personalPerformance, 0.01)
        assertEquals(0.03 * childBv, result.differential, 0.01)
        assertEquals(result.personalPerformance + result.differential, result.performanceBonus, 0.01)
        assertEquals(300.0, result.group.pv, 0.0)
        assertEquals(200.0, result.passUp.pv, 0.0)
    }

    @Test
    fun groupPvExcludesTwentyFivePercentLegs() {
        val result = engine.evaluateInputs(
            personalPv = 400.0,
            personalBv = 1_200.0,
            frontline = listOf(FrontlineVolume("Breakaway", 7_500.0, 22_500.0)),
            settings = ratio3,
        )
        assertEquals(400.0, result.group.pv, 0.01)
        assertEquals(400.0, result.rubyPv, 0.01)
        assertEquals(7_900.0, result.totalDownline.pv, 0.01)
        assertEquals(0.25, result.performancePercent, 0.0)
        assertFalse(result.silverProducerMonth)
    }

    @Test
    fun silverProducerViaGroupPv() {
        assertTrue(engine.isSilverProducer(7_500.0, 0))
        assertFalse(engine.isSilverProducer(7_499.0, 0))
    }

    @Test
    fun silverProducerVia2500AndOneLeg() {
        assertTrue(engine.isSilverProducer(2_500.0, 1))
        assertFalse(engine.isSilverProducer(2_499.0, 1))
        val result = engine.evaluateInputs(
            personalPv = 2_500.0,
            personalBv = 7_500.0,
            frontline = listOf(FrontlineVolume("Silver", 7_500.0, 22_500.0)),
            settings = ratio3,
        )
        assertTrue(result.silverProducerMonth)
        assertEquals(RankIds.SILVER, result.currentRank.id)
    }

    @Test
    fun silverProducerViaTwoTwentyFivePercentLegs() {
        assertTrue(engine.isSilverProducer(100.0, 2))
        val result = engine.evaluateInputs(
            personalPv = 200.0,
            personalBv = 600.0,
            frontline = listOf(
                FrontlineVolume("A", 7_500.0, 22_500.0),
                FrontlineVolume("B", 7_500.0, 22_500.0),
            ),
            settings = ratio3,
        )
        assertTrue(result.silverProducerMonth)
        assertEquals(200.0, result.group.pv, 0.01)
    }

    @Test
    fun rubyBonusOnlyAt15000RubyPv() {
        val under = engine.evaluateInputs(
            personalPv = 10_500.0,
            personalBv = 31_500.0,
            frontline = listOf(FrontlineVolume("Breakaway", 7_500.0, 22_500.0)),
            settings = ratio3.copy(includePerformancePlus = false),
        )
        assertEquals(10_500.0, under.rubyPv, 0.01)
        assertEquals(0.0, under.rubyBonus, 0.01)

        val over = engine.evaluateInputs(
            personalPv = 15_000.0,
            personalBv = 45_000.0,
            frontline = emptyList(),
            settings = ratio3.copy(includeLeadershipBonus = false),
        )
        assertEquals(15_000.0, over.rubyPv, 0.01)
        assertEquals(0.02 * 45_000.0, over.rubyBonus, 0.01)
    }

    @Test
    fun performancePlusAndEliteHighestWins() {
        val plus = engine.evaluateInputs(
            personalPv = 8_000.0,
            personalBv = 24_000.0,
            frontline = emptyList(),
            settings = ratio3.copy(includeRubyBonus = false, includePerformancePlus = true),
        )
        assertEquals(0.02, plus.corePlus.performancePlusPercent, 0.0)
        assertEquals(0.02 * 24_000.0, plus.corePlus.performancePlusAmount, 0.01)

        val elite = engine.evaluateInputs(
            personalPv = 13_000.0,
            personalBv = 39_000.0,
            frontline = emptyList(),
            settings = ratio3.copy(includeRubyBonus = false, includePerformancePlus = true),
        )
        assertEquals(0.04, elite.corePlus.performancePlusPercent, 0.0)
    }

    @Test
    fun rule412ProratesPersonalBonus() {
        val full = engine.evaluateInputs(
            personalPv = 1_000.0,
            personalBv = 3_000.0,
            frontline = emptyList(),
            settings = ratio3.copy(includeLeadershipBonus = false, includeRubyBonus = false),
        )
        val half = engine.evaluateInputs(
            personalPv = 1_000.0,
            personalBv = 3_000.0,
            frontline = emptyList(),
            settings = ratio3.copy(
                includeLeadershipBonus = false,
                includeRubyBonus = false,
                customerSalesPercent = 0.35,
                vcsPercent = 0.60,
            ),
        )
        assertEquals(1.0, full.rule412Factor, 0.0)
        assertEquals(0.5, half.rule412Factor, 0.01)
        assertEquals(full.personalPerformance * 0.5, half.personalPerformance, 0.05)
    }

    @Test
    fun leadershipSingleGroupMatchesPublishedPattern() {
        val result = engine.evaluateInputs(
            personalPv = 2_500.0,
            personalBv = 7_500.0,
            frontline = listOf(FrontlineVolume("Silver", 7_500.0, 22_500.0)),
            settings = ratio3.copy(includeRubyBonus = false, includePerformancePlus = false),
        )
        assertTrue(result.silverProducerMonth)
        assertEquals(450.0, result.leadershipBonus, 0.5)
        assertEquals(1_350.0, result.leadershipPassedToSponsor, 0.5)
    }

    @Test
    fun diamondPinNeedsSixLegsAndSixQMonths() {
        val legs = (1..6).map { FrontlineVolume("Leg $it", 7_500.0, 22_500.0) }
        val early = engine.evaluateInputs(
            personalPv = 200.0,
            personalBv = 600.0,
            frontline = legs,
            settings = ratio3.copy(silverProducerMonthsPy = 0),
        )
        assertEquals(RankIds.SILVER, early.currentRank.id)
        val pin = engine.evaluateInputs(
            personalPv = 200.0,
            personalBv = 600.0,
            frontline = legs,
            settings = ratio3.copy(silverProducerMonthsPy = 5),
        )
        assertEquals(RankIds.DIAMOND, pin.currentRank.id)
    }

    @Test
    fun groupVolumeRollsUpDescendants() {
        val snapshot = OrgSnapshot(
            members = listOf(
                Member("you", "You", isYou = true),
                Member("a", "A"),
                Member("b", "B"),
            ),
            nodes = listOf(
                OrgNode("n0", "you", null, StructureKind.CURRENT, 10.0, 30.0),
                OrgNode("n1", "a", "n0", StructureKind.CURRENT, 20.0, 60.0),
                OrgNode("n2", "b", "n1", StructureKind.CURRENT, 5.0, 15.0),
            ),
        )
        val group = snapshot.groupVolume("n0")
        assertEquals(35.0, group.pv, 0.0)
        assertEquals(105.0, group.bv, 0.0)
        val payout = engine.evaluateRoot(snapshot, StructureKind.CURRENT, ratio3)
        requireNotNull(payout)
        assertEquals(35.0, payout.group.pv, 0.0)
    }

    @Test
    fun sampleDataHasCurrentAndIdealRoots() {
        val snapshot = SampleData.snapshot(3.43)
        assertTrue(snapshot.root(StructureKind.CURRENT) != null)
        assertTrue(snapshot.root(StructureKind.IDEAL) != null)
        val payout = engine.evaluateRoot(snapshot, StructureKind.CURRENT, PlannerSettings())
        requireNotNull(payout)
        assertTrue(payout.group.pv > 1_000)
        assertTrue(payout.estimatedMonthly > 0)
        val ideal = engine.evaluateRoot(snapshot, StructureKind.IDEAL, PlannerSettings())
        requireNotNull(ideal)
        assertEquals(6, ideal.maxPercentLegs)
        assertTrue(ideal.silverProducerMonth)
    }

    @Test
    fun pqAnnualUsesDocumentedTiersWithoutFoldingIntoMonthly() {
        val result = engine.evaluateInputs(
            personalPv = 7_500.0,
            personalBv = 22_500.0,
            frontline = emptyList(),
            settings = ratio3.copy(
                includeLeadershipBonus = false,
                includeRubyBonus = false,
                includePerformancePlus = false,
                isPlatinumOrAbove = true,
                pqMonthsPy = 5,
            ),
        )
        assertTrue(result.corePlus.pqMonth)
        assertEquals(6_000.0, result.corePlus.pqAnnualEstimate, 0.01)
        assertTrue(result.estimatedMonthly < 6_000.0)
    }

    @Test
    fun gapAnalyzerReportsIncomeShortfallAgainstSilverGoal() {
        val snapshot = SampleData.snapshot(3.43)
        val gap = GapAnalyzer(engine).compare(
            snapshot,
            PlannerSettings(),
            UserGoals(monthlyIncomeTarget = 50_000.0, targetRankId = RankIds.DIAMOND),
        )
        requireNotNull(gap)
        assertTrue(gap.incomeGapToGoal > 0)
        assertTrue(gap.suggestions.isNotEmpty())
    }
}
