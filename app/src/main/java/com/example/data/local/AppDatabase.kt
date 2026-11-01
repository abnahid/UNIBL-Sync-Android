package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.model.ReminderPreferenceEntity
import com.example.data.model.VisitorEntity
import com.example.data.model.SyncStateEntity

@Database(
    entities = [
        ClassEntity::class,
        ReminderPreferenceEntity::class,
        AnnouncementEntity::class,
        AuditLogEntity::class,
        VisitorEntity::class,
        SyncStateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun reminderDao(): ReminderDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun visitorDao(): VisitorDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "classtime_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (`resource` TEXT NOT NULL, `lastSuccessfulSyncEpochMs` INTEGER NOT NULL, PRIMARY KEY(`resource`))"
                )
            }
        }
    }
}
