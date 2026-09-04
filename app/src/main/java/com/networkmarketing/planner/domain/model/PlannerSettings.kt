package com.networkmarketing.planner.domain.model

data class PlannerSettings(
    val bvPerPv: Double = DEFAULT_BV_PER_PV,
    val retailMarginPercent: Double = DEFAULT_RETAIL_MARGIN,
    val includeRetailMargin: Boolean = true,
    val customerSalesPercent: Double = DEFAULT_CUSTOMER_SALES,
    val vcsPercent: Double = DEFAULT_VCS,
    val meetsRule413: Boolean = true,
    val includeLeadershipBonus: Boolean = true,
    val includeDepthBonus: Boolean = true,
    val includeRubyBonus: Boolean = true,
    val includePerformancePlus: Boolean = true,
    val includeCsi: Boolean = false,
    val csiEligible: Boolean = false,
    val bfiEligible: Boolean = true,
    val bbiEligible: Boolean = true,
    val isPlatinumOrAbove: Boolean = false,
    val silverProducerMonthsPy: Int = 0,
    val consecutiveSilverMonths: Int = 0,
    val pqMonthsPy: Int = 0,
    val rubyPvPy: Double = 0.0,
    val personalPvPy: Double = 0.0,
    val groupPvPy: Double = 0.0,
    val totalDownlinePvPy: Double = 0.0,
    val fqsPy: Int = 0,
    val priorYearPqMonths: Int = 0,
    val newIboBaselineMonths: Int = 0,
) {
    /** @deprecated Use [retailMarginPercent]. Kept so older call sites compile during the swap. */
    val customerProfitPercent: Double get() = retailMarginPercent
    val includeCustomerProfit: Boolean get() = includeRetailMargin

    companion object {
        const val DEFAULT_BV_PER_PV = 3.43
        const val DEFAULT_RETAIL_MARGIN = 0.10
        const val DEFAULT_CUSTOMER_SALES = 0.70
        const val DEFAULT_VCS = 0.60
        const val DEFAULT_CUSTOMER_PROFIT = DEFAULT_RETAIL_MARGIN
    }
}
