package com.networkmarketing.planner.domain.model

data class PlannerSettings(
    val bvPerPv: Double = DEFAULT_BV_PER_PV,
    val customerProfitPercent: Double = DEFAULT_CUSTOMER_PROFIT,
    val includeCustomerProfit: Boolean = true,
    val includeLeadershipBonus: Boolean = true,
    val includeRubyBonus: Boolean = true,
) {
    companion object {
        /** Documented default from a common North American PV/BV ratio (~3.43 BV per 1 PV). */
        const val DEFAULT_BV_PER_PV = 3.43
        const val DEFAULT_CUSTOMER_PROFIT = 0.10
    }
}
