package com.networkmarketing.planner.domain.compensation

import org.junit.Assert.assertEquals
import org.junit.Test

class LeadershipBonusTest {

    private val percent = 0.06
    private val lba3 = 1_350.0

    @Test
    fun exampleBSingleGroupKeepsIncomingPlusOwnSixMinusLba() {
        val result = LeadershipBonus.settle(
            ownGroupBv = 7_500.0,
            incomingByLeg = listOf(1_710.0),
            leadershipPercent = percent,
            publishedLba = lba3,
            qualifies = true,
        )
        assertEquals(810.0, result.kept, 0.01)
        assertEquals(1_350.0, result.passedToSponsor, 0.01)
    }

    @Test
    fun exampleCTwoGroupsOneAtPublishedLba() {
        val result = LeadershipBonus.settle(
            ownGroupBv = 350.0,
            incomingByLeg = listOf(1_170.0, 1_350.0),
            leadershipPercent = percent,
            publishedLba = lba3,
            qualifies = true,
        )
        assertEquals(1_191.0, result.kept, 0.01)
        assertEquals(1_350.0, result.passedToSponsor, 0.01)
    }

    @Test
    fun exampleDTwoFullGroupsAt7500KeepsAllIncoming() {
        val result = LeadershipBonus.settle(
            ownGroupBv = 22_500.0,
            incomingByLeg = listOf(1_350.0, 1_350.0),
            leadershipPercent = percent,
            publishedLba = lba3,
            qualifies = true,
        )
        assertEquals(2_700.0, result.kept, 0.01)
    }

    @Test
    fun exampleETwoGroupsUnderLbaUsesAverage() {
        val result = LeadershipBonus.settle(
            ownGroupBv = 7_500.0,
            incomingByLeg = listOf(1_200.0, 1_260.0),
            leadershipPercent = percent,
            publishedLba = lba3,
            qualifies = true,
        )
        assertEquals(1_680.0, result.kept, 0.01)
        assertEquals(1_230.0, result.passedToSponsor, 0.01)
    }

    @Test
    fun publishedLbaAt343MatchesMoneyAndRewards() {
        assertEquals(1_543.50, LeadershipBonus.publishedLba(0.06, 7_500.0, 3.43), 0.01)
    }

    @Test
    fun nonQualifierPassesIncomingPlusOwnSix() {
        val result = LeadershipBonus.settle(
            ownGroupBv = 4_500.0,
            incomingByLeg = listOf(1_440.0),
            leadershipPercent = percent,
            publishedLba = lba3,
            qualifies = false,
        )
        assertEquals(0.0, result.kept, 0.0)
        assertEquals(1_710.0, result.passedToSponsor, 0.01)
    }
}
