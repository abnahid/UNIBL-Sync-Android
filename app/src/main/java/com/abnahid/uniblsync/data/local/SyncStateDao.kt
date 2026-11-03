package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abnahid.uniblsync.data.model.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE resource = :resource LIMIT 1")
    fun observe(resource: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE resource = :resource LIMIT 1")
    suspend fun get(resource: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: SyncStateEntity)
}
