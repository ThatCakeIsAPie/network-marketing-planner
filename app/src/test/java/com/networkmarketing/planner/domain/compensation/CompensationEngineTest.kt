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
import org.junit.Assert.assertTrue
import org.junit.Test

class CompensationEngineTest {

    private val engine = CompensationEngine()
    private val settings = PlannerSettings(
        bvPerPv = 3.0,
        customerProfitPercent = 0.10,
        includeCustomerProfit = true,
        includeLeadershipBonus = true,
        includeRubyBonus = true,
    )

    @Test
    fun bracketTableMatchesPublishedStyleSchedule() {
        assertEquals(0.00, engine.config().bracketFor(0.0).percent, 0.0)
        assertEquals(0.03, engine.config().bracketFor(100.0).percent, 0.0)
        assertEquals(0.12, engine.config().bracketFor(1_200.0).percent, 0.0)
        assertEquals(0.25, engine.config().bracketFor(7_500.0).percent, 0.0)
        assertEquals(0.25, engine.config().bracketFor(20_000.0).percent, 0.0)
    }

    @Test
    fun performanceBonusIsPersonalSharePlusDifferential() {
        val childBv = 200.0 * 3.0
        val personalBv = 100.0 * 3.0
        val result = engine.evaluateInputs(
            personalPv = 100.0,
            personalBv = personalBv,
            frontline = listOf(FrontlineVolume("A", 200.0, childBv)),
            settings = settings.copy(includeCustomerProfit = false, includeLeadershipBonus = false, includeRubyBonus = false),
        )
        // Group PV = 300 → 6%. Child is 200 PV → 3%.
        assertEquals(0.06, result.performancePercent, 0.0)
        assertEquals(0.06 * personalBv, result.personalPerformance, 0.01)
        assertEquals(0.03 * childBv, result.differential, 0.01)
        assertEquals(result.personalPerformance + result.differential, result.performanceBonus, 0.01)
    }

    @Test
    fun leadershipBonusAppliesOnMaxBracketFrontline() {
        val maxPv = 7_500.0
        val result = engine.evaluateInputs(
            personalPv = 250.0,
            personalBv = 250.0 * 3.0,
            frontline = listOf(FrontlineVolume("Silver leg", maxPv, maxPv * 3.0)),
            settings = settings.copy(includeCustomerProfit = false, includeRubyBonus = false),
        )
        assertEquals(0.25, result.performancePercent, 0.0)
        assertEquals(1, result.maxPercentLegs)
        assertEquals(0.06 * maxPv * 3.0, result.leadershipBonus, 0.01)
        assertEquals(RankIds.PLATINUM, result.currentRank.id)
    }

    @Test
    fun rubyVolumeExcludesMaxBracketFrontline() {
        val result = engine.evaluateInputs(
            personalPv = 8_000.0,
            personalBv = 8_000.0 * 3.0,
            frontline = listOf(FrontlineVolume("Breakaway", 7_500.0, 7_500.0 * 3.0)),
            settings = settings.copy(includeCustomerProfit = false, includeLeadershipBonus = false),
        )
        assertEquals(8_000.0, result.rubyPv, 0.01)
        assertTrue(result.rubyBonus > 0.0)
    }

    @Test
    fun diamondRequiresSixMaxBracketLegs() {
        val legs = (1..6).map {
            FrontlineVolume("Leg $it", 7_500.0, 7_500.0 * 3.0)
        }
        val result = engine.evaluateInputs(
            personalPv = 200.0,
            personalBv = 600.0,
            frontline = legs,
            settings = settings.copy(includeCustomerProfit = false),
        )
        assertEquals(RankIds.DIAMOND, result.currentRank.id)
        val need = engine.neededForRank(result, engine.config().rank(RankIds.DIAMOND))
        assertTrue(need.met)
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
        assertEquals(2, snapshot.frontline("n0").size.coerceAtLeast(snapshot.children("n0").size))
        assertEquals(1, snapshot.frontline("n0").size)
    }

    @Test
    fun sampleDataHasCurrentAndIdealRoots() {
        val snapshot = SampleData.snapshot(3.43)
        assertTrue(snapshot.root(StructureKind.CURRENT) != null)
        assertTrue(snapshot.root(StructureKind.IDEAL) != null)
        assertTrue(snapshot.nodeCount(StructureKind.CURRENT) > 5)
        val payout = engine.evaluateRoot(snapshot, StructureKind.CURRENT, PlannerSettings())
        requireNotNull(payout)
        assertTrue(payout.group.pv > 1_000)
        assertTrue(payout.estimatedMonthly > 0)
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
        assertTrue(gap.maxPercentLegsGap > 0)
        assertTrue(gap.suggestions.isNotEmpty())
    }
}
```
