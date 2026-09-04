package com.networkmarketing.planner.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserGoals(
    val monthlyIncomeTarget: Double = 2_000.0,
    val targetRankId: String = RankIds.SILVER,
    val onboardingComplete: Boolean = false,
    val disclaimerAccepted: Boolean = false,
)

object RankIds {
    const val STARTER = "starter"
    const val QUALIFIED = "qualified"
    const val BRONZE = "bronze"
    const val SILVER = "silver"
    const val GOLD = "gold"
    const val PLATINUM = "platinum"
    const val FOUNDERS_PLATINUM = "founders_platinum"
    const val RUBY = "ruby"
    const val EMERALD = "emerald"
    const val DIAMOND = "diamond"

    const val COORDINATOR = BRONZE
}
