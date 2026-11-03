package com.abnahid.uniblsync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.abnahid.uniblsync.data.model.AnnouncementEntity
import com.abnahid.uniblsync.data.model.AuditLogEntity
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import com.abnahid.uniblsync.data.model.SyncStateEntity
import com.abnahid.uniblsync.data.model.VisitorEntity

@Database(
    entities = [
        ClassEntity::class,
        ReminderPreferenceEntity::class,
        ScheduledReminderEntity::class,
        RecurringScheduleEntity::class,
        ScheduleOverrideEntity::class,
        AnnouncementEntity::class,
        AuditLogEntity::class,
        VisitorEntity::class,
        SyncStateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun reminderDao(): ReminderDao
    abstract fun scheduledReminderDao(): ScheduledReminderDao
    abstract fun recurringScheduleDao(): RecurringScheduleDao
    abstract fun scheduleOverrideDao(): ScheduleOverrideDao
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
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

        /**
         * Adds local schedule management and persistent reminder storage.
         * Existing user data (classes, preferences, announcements, ...) is preserved.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_schedule` (" +
                        "`id` TEXT NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`classType` TEXT NOT NULL, " +
                        "`weekdayIso` INTEGER NOT NULL, " +
                        "`sourceStartTime` TEXT NOT NULL, " +
                        "`sourceEndTime` TEXT NOT NULL, " +
                        "`sourceTimezone` TEXT NOT NULL, " +
                        "`groupName` TEXT NOT NULL, " +
                        "`attendanceMode` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`locationOrUrl` TEXT NOT NULL, " +
                        "`instructor` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`effectiveFromIsoDate` TEXT NOT NULL, " +
                        "`effectiveToIsoDate` TEXT NOT NULL, " +
                        "`explicitDates` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedule_weekdayIso` ON `recurring_schedule` (`weekdayIso`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedule_subject` ON `recurring_schedule` (`subject`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_overrides` (" +
                        "`id` TEXT NOT NULL, " +
                        "`scheduleId` TEXT NOT NULL, " +
                        "`occurrenceDate` TEXT NOT NULL, " +
                        "`changeType` TEXT NOT NULL, " +
                        "`subject` TEXT, " +
                        "`classType` TEXT, " +
                        "`sourceStartTime` TEXT, " +
                        "`sourceEndTime` TEXT, " +
                        "`groupName` TEXT, " +
                        "`attendanceMode` TEXT, " +
                        "`locationOrUrl` TEXT, " +
                        "`instructor` TEXT, " +
                        "`platform` TEXT, " +
                        "`notes` TEXT, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_scheduleId` ON `schedule_overrides` (`scheduleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_occurrenceDate` ON `schedule_overrides` (`occurrenceDate`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_reminders` (" +
                        "`reminderKey` TEXT NOT NULL, " +
                        "`classId` TEXT NOT NULL, " +
                        "`occurrenceDate` TEXT NOT NULL, " +
                        "`scheduleId` TEXT NOT NULL, " +
                        "`triggerEpochMs` INTEGER NOT NULL, " +
                        "`leadMinutes` INTEGER NOT NULL, " +
                        "`isAlarmEnabled` INTEGER NOT NULL, " +
                        "`soundTone` TEXT NOT NULL, " +
                        "`vibrate` INTEGER NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`classType` TEXT NOT NULL, " +
                        "`groupName` TEXT NOT NULL, " +
                        "`attendanceMode` TEXT NOT NULL, " +
                        "`sourceStartTime` TEXT NOT NULL, " +
                        "`sourceEndTime` TEXT NOT NULL, " +
                        "`locationOrUrl` TEXT NOT NULL, " +
                        "PRIMARY KEY(`reminderKey`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_reminders_classId` ON `scheduled_reminders` (`classId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_reminders_triggerEpochMs` ON `scheduled_reminders` (`triggerEpochMs`)")
            }
        }
    }
}
