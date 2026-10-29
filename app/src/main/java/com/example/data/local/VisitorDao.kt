package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.VisitorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitorDao {
    @Query("SELECT * FROM visitors ORDER BY registeredAtEpochMs DESC")
    fun getAllVisitors(): Flow<List<VisitorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitor(visitor: VisitorEntity)

    @Query("DELETE FROM visitors WHERE id = :id")
    suspend fun deleteVisitor(id: String)

    @Query("DELETE FROM visitors")
    suspend fun clearVisitors()
}
