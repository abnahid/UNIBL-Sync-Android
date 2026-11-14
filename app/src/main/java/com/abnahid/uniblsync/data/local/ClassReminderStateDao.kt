package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassReminderStateDao {
    @Query("SELECT * FROM class_reminder_states ORDER BY classStartEpochMs ASC")
    fun observeAllStates(): Flow<List<ClassReminderStateEntity>>

    @Query("SELECT * FROM class_reminder_states ORDER BY classStartEpochMs ASC")
    suspend fun getAllStatesSnapshot(): List<ClassReminderStateEntity>

    @Query("SELECT * FROM class_reminder_states WHERE occurrenceKey = :occurrenceKey LIMIT 1")
    suspend fun getByOccurrenceKey(occurrenceKey: String): ClassReminderStateEntity?

    @Query("SELECT * FROM class_reminder_states WHERE classId = :classId")
    suspend fun getByClassId(classId: String): List<ClassReminderStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ClassReminderStateEntity)

    @Query(
        "UPDATE class_reminder_states " +
            "SET advanceDelivered = 1, advanceDeliveredAtEpochMs = :deliveredAtMs, " +
            "advanceScheduledStatus = :status, updatedAtEpochMs = :deliveredAtMs " +
            "WHERE occurrenceKey = :occurrenceKey"
    )
    suspend fun markAdvanceDelivered(
        occurrenceKey: String,
        deliveredAtMs: Long,
        status: String = ClassReminderStateEntity.STATUS_DELIVERED
    )

    @Query(
        "UPDATE class_reminder_states " +
            "SET finalDelivered = 1, finalDeliveredAtEpochMs = :deliveredAtMs, " +
            "finalScheduledStatus = :status, updatedAtEpochMs = :deliveredAtMs " +
            "WHERE occurrenceKey = :occurrenceKey"
    )
    suspend fun markFinalDelivered(
        occurrenceKey: String,
        deliveredAtMs: Long,
        status: String = ClassReminderStateEntity.STATUS_DELIVERED
    )

    @Query("UPDATE class_reminder_states SET dismissed = 1, updatedAtEpochMs = :updatedAtMs WHERE occurrenceKey = :occurrenceKey")
    suspend fun markDismissed(occurrenceKey: String, updatedAtMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM class_reminder_states WHERE classId = :classId")
    suspend fun deleteByClassId(classId: String)

    @Query("DELETE FROM class_reminder_states WHERE classStartEpochMs < :cutoffEpochMs")
    suspend fun pruneOlderThan(cutoffEpochMs: Long)
}
