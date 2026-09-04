package com.networkmarketing.planner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MemberEntity::class, OrgNodeEntity::class, PrefsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun plannerDao(): PlannerDao

    companion object {
        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
```
