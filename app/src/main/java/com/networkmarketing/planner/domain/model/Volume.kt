package com.networkmarketing.planner.domain.model

data class Volume(
    val pv: Double = 0.0,
    val bv: Double = 0.0,
) {
    operator fun plus(other: Volume): Volume = Volume(pv + other.pv, bv + other.bv)

    companion object {
        val ZERO = Volume()
    }
}
```
