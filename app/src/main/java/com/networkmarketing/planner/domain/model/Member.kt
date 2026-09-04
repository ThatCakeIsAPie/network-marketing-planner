package com.networkmarketing.planner.domain.model

/**
 * A person (or couple) in the organization. The same member can appear in both
 * the current and ideal structures.
 */
data class Member(
    val id: String,
    val name: String,
    val notes: String = "",
    val isYou: Boolean = false,
    val partnerName: String = "",
    val isCouple: Boolean = false,
) {
    fun displayName(): String =
        if (isCouple && partnerName.isNotBlank()) "$name & $partnerName" else name.ifBlank { "Unnamed" }
}
