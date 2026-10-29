package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ReminderPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_preferences")
    fun getAllPreferences(): Flow<List<ReminderPreferenceEntity>>

    @Query("SELECT * FROM reminder_preferences WHERE classId = :classId LIMIT 1")
    fun getPreferenceForClass(classId: String): Flow<ReminderPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(preference: ReminderPreferenceEntity)

    @Query("DELETE FROM reminder_preferences WHERE classId = :classId")
    suspend fun deletePreferenceForClass(classId: String)
}
