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
    val retailMarginPercent: Double,
    val includeRetailMargin: Boolean,
    val customerSalesPercent: Double,
    val vcsPercent: Double,
    val meetsRule413: Boolean,
    val includeLeadershipBonus: Boolean,
    val includeDepthBonus: Boolean,
    val includeRubyBonus: Boolean,
    val includePerformancePlus: Boolean,
    val includeCsi: Boolean,
    val csiEligible: Boolean,
    val bfiEligible: Boolean,
    val bbiEligible: Boolean,
    val isPlatinumOrAbove: Boolean,
    val silverProducerMonthsPy: Int,
    val consecutiveSilverMonths: Int,
    val pqMonthsPy: Int,
    val rubyPvPy: Double,
    val personalPvPy: Double,
    val groupPvPy: Double,
    val totalDownlinePvPy: Double,
    val fqsPy: Int,
    val priorYearPqMonths: Int,
    val newIboBaselineMonths: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
