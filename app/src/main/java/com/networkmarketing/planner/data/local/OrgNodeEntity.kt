package com.networkmarketing.planner.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "org_nodes",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memberId"), Index("parentId"), Index("kind")],
)
data class OrgNodeEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val parentId: String?,
    val kind: String,
    val personalPv: Double,
    val personalBv: Double,
)
```
