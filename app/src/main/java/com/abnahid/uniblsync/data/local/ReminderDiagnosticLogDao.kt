package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abnahid.uniblsync.data.model.ReminderDiagnosticLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDiagnosticLogDao {
    @Query("SELECT * FROM reminder_diagnostic_logs ORDER BY timestampEpochMs DESC, id DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 120): Flow<List<ReminderDiagnosticLogEntity>>

    @Query("SELECT * FROM reminder_diagnostic_logs ORDER BY timestampEpochMs DESC, id DESC LIMIT :limit")
    suspend fun getRecentLogsSnapshot(limit: Int = 120): List<ReminderDiagnosticLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReminderDiagnosticLogEntity)

    @Query(
        "DELETE FROM reminder_diagnostic_logs WHERE id NOT IN " +
            "(SELECT id FROM reminder_diagnostic_logs ORDER BY timestampEpochMs DESC, id DESC LIMIT :keepCount)"
    )
    suspend fun trimToLatest(keepCount: Int = 150)

    @Query("DELETE FROM reminder_diagnostic_logs")
    suspend fun clearAll()
}
