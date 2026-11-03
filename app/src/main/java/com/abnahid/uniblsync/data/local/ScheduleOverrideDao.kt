package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleOverrideDao {
    @Query("SELECT * FROM schedule_overrides")
    fun getAllOverrides(): Flow<List<ScheduleOverrideEntity>>

    @Query("SELECT * FROM schedule_overrides")
    suspend fun getAllOverridesSnapshot(): List<ScheduleOverrideEntity>

    @Query("SELECT * FROM schedule_overrides WHERE scheduleId = :scheduleId AND occurrenceDate = :date LIMIT 1")
    suspend fun getOverrideFor(scheduleId: String, date: String): ScheduleOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: ScheduleOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(overrides: List<ScheduleOverrideEntity>)

    @Query("DELETE FROM schedule_overrides")
    suspend fun clearAll()

    @Update
    suspend fun updateOverride(override: ScheduleOverrideEntity)

    @Query("DELETE FROM schedule_overrides WHERE id = :id")
    suspend fun deleteOverrideById(id: String)

    @Query("DELETE FROM schedule_overrides WHERE scheduleId = :scheduleId")
    suspend fun deleteOverridesForSchedule(scheduleId: String)

    @Query("SELECT COUNT(*) FROM schedule_overrides")
    suspend fun count(): Int
}
