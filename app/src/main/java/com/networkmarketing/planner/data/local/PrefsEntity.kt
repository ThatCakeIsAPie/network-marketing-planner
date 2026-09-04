package com.networkmarketing.planner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planner_prefs")
data class PrefsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val onboardingComplete: Boolean,
    val disclaimerAccepted: Boolean,
    val monthlyIncomeTarget: Double,
    val targetRankId: String,
    val bvPerPv: Double,
    val customerProfitPercent: Double,
    val includeCustomerProfit: Boolean,
    val includeLeadershipBonus: Boolean,
    val includeRubyBonus: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
