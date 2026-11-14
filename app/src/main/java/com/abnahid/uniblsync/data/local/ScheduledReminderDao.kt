package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledReminderDao {
    @Query("SELECT * FROM scheduled_reminders")
    fun getAllScheduled(): Flow<List<ScheduledReminderEntity>>

    @Query("SELECT * FROM scheduled_reminders")
    suspend fun getAllScheduledSnapshot(): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE triggerEpochMs > :nowEpochMs")
    suspend fun getFutureScheduled(nowEpochMs: Long): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE classId = :classId")
    suspend fun getScheduledForClass(classId: String): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE reminderKey = :reminderKey LIMIT 1")
    suspend fun getByReminderKey(reminderKey: String): ScheduledReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ScheduledReminderEntity)

    @Query("DELETE FROM scheduled_reminders WHERE reminderKey = :reminderKey")
    suspend fun deleteByReminderKey(reminderKey: String)

    @Query("DELETE FROM scheduled_reminders WHERE classId = :classId")
    suspend fun deleteByClassId(classId: String)

    @Query("DELETE FROM scheduled_reminders")
    suspend fun clearAll()
}
