package com.networkmarketing.planner.domain.compensation

import kotlin.math.max
import kotlin.math.min

/**
 * Bottom-up monthly Leadership Bonus with Published LBA roll-up.
 *
 * Matches Business Reference Guide examples A–E (those illustrations used
 * BV:PV = 3.00 so LBA = \$1,350). Live PY2027 ratio 3.43 yields LBA = \$1,543.50.
 *
 * Simplification: each 25% frontline is treated as a Starter whose 6% of
 * Group BV rolls to you. Intermediates that are not at 25% contribute
 * 6% of their Group BV to the roll-up and keep none (BRG examples A1/Frontline A).
 */
object LeadershipBonus {

    fun publishedLba(percent: Double, silverProducerPv: Double, bvPerPv: Double): Double =
        percent * silverProducerPv * bvPerPv

    data class Settlement(
        val kept: Double,
        val passedToSponsor: Double,
    )

    /**
     * @param ownGroupBv Group BV excluding 25% legs (Ruby/Side volume BV)
     * @param incomingByLeg 6% roll-up arriving from each qualifying 25% line,
     *        already including 6% contributions from non-qualifying intermediates
     */
    fun settle(
        ownGroupBv: Double,
        incomingByLeg: List<Double>,
        leadershipPercent: Double,
        publishedLba: Double,
        qualifies: Boolean,
    ): Settlement {
        if (incomingByLeg.isEmpty()) return Settlement(0.0, 0.0)
        val ownSix = leadershipPercent * ownGroupBv
        val incoming = incomingByLeg.sum()
        if (!qualifies) {
            return Settlement(kept = 0.0, passedToSponsor = incoming + ownSix)
        }
        val multiple = incomingByLeg.size >= 2
        val anyAtLba = incomingByLeg.any { it + 1e-9 >= publishedLba }

        return if (!multiple) {
            val cap = min(incoming, publishedLba)
            if (ownSix + 1e-9 >= cap) {
                Settlement(kept = incoming, passedToSponsor = ownSix)
            } else {
                Settlement(kept = incoming + ownSix - cap, passedToSponsor = cap)
            }
        } else {
            val average = incoming / incomingByLeg.size
            val sponsorSlice = if (anyAtLba) publishedLba else average
            if (ownSix + 1e-9 >= sponsorSlice) {
                Settlement(kept = incoming, passedToSponsor = ownSix)
            } else {
                Settlement(kept = incoming + ownSix - sponsorSlice, passedToSponsor = sponsorSlice)
            }
        }.let { Settlement(max(0.0, it.kept), max(0.0, it.passedToSponsor)) }
    }

    fun qualifies(
        silverProducer: Boolean,
        maxPercentLegs: Int,
        rubyPv: Double,
        minRubyPvForSingleLeg: Double = 2_500.0,
    ): Boolean {
        if (maxPercentLegs >= 2) return true
        return silverProducer && maxPercentLegs >= 1 && rubyPv + 1e-9 >= minRubyPvForSingleLeg
    }
}
