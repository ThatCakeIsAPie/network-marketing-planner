package com.networkmarketing.planner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT * FROM members ORDER BY isYou DESC, name ASC")
    fun observeMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM org_nodes")
    fun observeNodes(): Flow<List<OrgNodeEntity>>

    @Query("SELECT * FROM planner_prefs WHERE id = 1")
    fun observePrefs(): Flow<PrefsEntity?>

    @Query("SELECT * FROM planner_prefs WHERE id = 1")
    suspend fun getPrefs(): PrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<OrgNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: OrgNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrefs(prefs: PrefsEntity)

    @Update
    suspend fun updateNode(node: OrgNodeEntity)

    @Query("DELETE FROM org_nodes WHERE id = :id")
    suspend fun deleteNode(id: String)

    @Query("DELETE FROM org_nodes WHERE id IN (:ids)")
    suspend fun deleteNodes(ids: List<String>)

    @Query("DELETE FROM members WHERE id = :id AND isYou = 0")
    suspend fun deleteMember(id: String)

    @Query("SELECT COUNT(*) FROM org_nodes")
    suspend fun nodeCount(): Int

    @Query("DELETE FROM org_nodes")
    suspend fun clearNodes()

    @Query("DELETE FROM members")
    suspend fun clearMembers()

    @Transaction
    suspend fun replaceOrganization(members: List<MemberEntity>, nodes: List<OrgNodeEntity>) {
        clearNodes()
        clearMembers()
        upsertMembers(members)
        upsertNodes(nodes)
    }
}
