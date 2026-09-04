package com.networkmarketing.planner.domain.model

enum class StructureKind {
    CURRENT,
    IDEAL,
}

/**
 * One placement of a [Member] in either the current or ideal organization tree.
 *
 * Volume is monthly personal PV/BV (own + registered customer volume).
 * Group volume is derived by rolling up descendants — see [OrgSnapshot].
 */
data class OrgNode(
    val id: String,
    val memberId: String,
    val parentId: String?,
    val kind: StructureKind,
    val personalPv: Double,
    val personalBv: Double,
)
```
