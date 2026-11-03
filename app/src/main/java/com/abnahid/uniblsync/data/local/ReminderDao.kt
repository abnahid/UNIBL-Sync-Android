package com.abnahid.uniblsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_preferences")
    fun getAllPreferences(): Flow<List<ReminderPreferenceEntity>>

    @Query("SELECT * FROM reminder_preferences")
    suspend fun getAllPreferencesSnapshot(): List<ReminderPreferenceEntity>

    @Query("SELECT * FROM reminder_preferences WHERE classId = :classId LIMIT 1")
    fun getPreferenceForClass(classId: String): Flow<ReminderPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(preference: ReminderPreferenceEntity)

    @Query("DELETE FROM reminder_preferences WHERE classId = :classId")
    suspend fun deletePreferenceForClass(classId: String)

    @Query("DELETE FROM reminder_preferences WHERE classId NOT IN (SELECT id FROM classes) AND classId NOT LIKE 'rec:%'")
    suspend fun deletePreferencesForMissingClasses()
}
