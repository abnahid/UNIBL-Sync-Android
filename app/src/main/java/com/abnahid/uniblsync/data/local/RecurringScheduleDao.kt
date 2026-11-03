package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringScheduleDao {
    @Query("SELECT * FROM recurring_schedule ORDER BY subject ASC, sourceStartTime ASC")
    fun getAllSchedules(): Flow<List<RecurringScheduleEntity>>

    @Query("SELECT * FROM recurring_schedule ORDER BY subject ASC, sourceStartTime ASC")
    suspend fun getAllSchedulesSnapshot(): List<RecurringScheduleEntity>

    @Query("SELECT * FROM recurring_schedule WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): RecurringScheduleEntity?

    @Query("SELECT COUNT(*) FROM recurring_schedule")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<RecurringScheduleEntity>)

    @Query("DELETE FROM recurring_schedule")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: RecurringScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: RecurringScheduleEntity)

    @Query("DELETE FROM recurring_schedule WHERE id = :id")
    suspend fun deleteScheduleById(id: String)
}
