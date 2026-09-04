package com.networkmarketing.planner.domain.model

/**
 * A person in the organization. Members can appear in both the current and
 * ideal structures (same identity, different volume/parenting).
 */
data class Member(
    val id: String,
    val name: String,
    val notes: String = "",
    val isYou: Boolean = false,
)
```
