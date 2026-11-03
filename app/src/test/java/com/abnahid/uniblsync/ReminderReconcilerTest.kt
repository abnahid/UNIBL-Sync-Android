package com.abnahid.uniblsync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.schedule.ReminderReconciler
import com.abnahid.uniblsync.util.TimezoneHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the reminder reconciliation contract:
 * - trigger instant = class start minus the configured lead
 * - expired / already-started classes are skipped
 * - cancelled classes lose their preference and their scheduled row
 * - changing a reminder replaces the persisted row (never duplicates)
 * - `rec:` occurrences are resolved through the occurrence generator
 * - `rec:` preferences survive the missing-class cleanup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderReconcilerTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    private val bosnia = "Europe/Sarajevo"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun storedClass(
        id: String,
        date: String,
        startTime: String,
        endTime: String,
        status: String = ClassEntity.STATUS_SCHEDULED
    ): ClassEntity = ClassEntity(
        id = id,
        subject = "Anatomy",
        classType = RecurringScheduleEntity.TYPE_LECTURE,
        sourceDate = date,
        sourceStartTime = startTime,
        sourceEndTime = endTime,
        sourceTimezone = bosnia,
        utcStartEpochMs = TimezoneHelper.toUtcEpochMs(date, startTime, bosnia),
        utcEndEpochMs = TimezoneHelper.toUtcEpochMs(date, endTime, bosnia),
        status = status
    )

    @Test
    fun `reminder trigger is start time minus leadMinutes`() = runBlocking {
        val start = TimezoneHelper.toUtcEpochMs("2026-12-02", "12:00", bosnia)
        database.classDao().insertClass(storedClass("class-1", "2026-12-02", "12:00", "13:30"))
        val pref = ReminderPreferenceEntity(classId = "class-1", leadMinutes = 15)

        ReminderReconciler.scheduleForPreference(context, database, pref)

        val scheduled = database.scheduledReminderDao().getScheduledForClass("class-1")
        assertEquals(1, scheduled.size)
        assertEquals(start - 15 * 60_000L, scheduled.first().triggerEpochMs)
        assertEquals(ReminderReconciler.reminderKeyFor("class-1", "2026-12-02"), scheduled.first().reminderKey)
    }

    @Test
    fun `already started class is skipped and nothing is scheduled`() = runBlocking {
        database.classDao().insertClass(storedClass("class-2", "2026-10-06", "12:00", "13:30"))

        ReminderReconciler.scheduleForPreference(
            context, database, ReminderPreferenceEntity(classId = "class-2", leadMinutes = 15)
        )

        assertTrue(database.scheduledReminderDao().getScheduledForClass("class-2").isEmpty())
    }

    @Test
    fun `cancelled class loses preference and scheduled reminder`() = runBlocking {
        database.classDao().insertClass(storedClass("class-3", "2026-12-02", "12:00", "13:30"))
        val pref = ReminderPreferenceEntity(classId = "class-3", leadMinutes = 15)
        ReminderReconciler.scheduleForPreference(context, database, pref)
        assertEquals(1, database.scheduledReminderDao().getScheduledForClass("class-3").size)

        // Class gets cancelled afterwards (kept so the fixture matches reality).
        database.classDao().insertClass(
            storedClass("class-3", "2026-12-02", "12:00", "13:30", status = ClassEntity.STATUS_CANCELLED)
        )
        database.reminderDao().savePreference(ReminderPreferenceEntity(classId = "class-3", leadMinutes = 15))
        ReminderReconciler.scheduleForPreference(
            context, database, database.reminderDao().getAllPreferencesSnapshot().first { it.classId == "class-3" }
        )

        val prefs = database.reminderDao().getAllPreferencesSnapshot()
        assertTrue(prefs.none { it.classId == "class-3" })
        assertTrue(database.scheduledReminderDao().getScheduledForClass("class-3").isEmpty())
    }

    @Test
    fun `changing the reminder replaces the persisted row without duplicates`() = runBlocking {
        database.classDao().insertClass(storedClass("class-4", "2026-12-02", "12:00", "13:30"))

        val start = TimezoneHelper.toUtcEpochMs("2026-12-02", "12:00", bosnia)
        ReminderReconciler.scheduleForPreference(
            context, database, ReminderPreferenceEntity(classId = "class-4", leadMinutes = 15)
        )
        assertEquals(
            start - 15 * 60_000L,
            database.scheduledReminderDao().getScheduledForClass("class-4").single().triggerEpochMs
        )

        ReminderReconciler.scheduleForPreference(
            context, database, ReminderPreferenceEntity(classId = "class-4", leadMinutes = 30)
        )
        val rows = database.scheduledReminderDao().getScheduledForClass("class-4")
        assertEquals(1, rows.size)
        assertEquals(start - 30 * 60_000L, rows.single().triggerEpochMs)
    }

    @Test
    fun `rec occurrence id is resolved through the generator`() = runBlocking {
        database.recurringScheduleDao().insertSchedule(
            RecurringScheduleEntity(
                id = "sched-1",
                subject = "Histology",
                classType = RecurringScheduleEntity.TYPE_PRACTICAL,
                weekdayIso = 2, // Tuesday
                sourceStartTime = "12:00",
                sourceEndTime = "13:30",
                effectiveFromIsoDate = "2026-10-05",
                effectiveToIsoDate = "",
                explicitDates = "",
                isActive = true
            )
        )
        val expectedStart = TimezoneHelper.toUtcEpochMs("2026-12-01", "12:00", bosnia)
        assertTrue(expectedStart > System.currentTimeMillis())

        ReminderReconciler.scheduleForPreference(
            context, database,
            ReminderPreferenceEntity(classId = "rec:sched-1:2026-12-01", leadMinutes = 10)
        )

        val rows = database.scheduledReminderDao()
            .getScheduledForClass("rec:sched-1:2026-12-01")
        assertEquals(1, rows.size)
        assertEquals(expectedStart - 10 * 60_000L, rows.first().triggerEpochMs)
        assertEquals("2026-12-01", rows.first().occurrenceDate)
        assertEquals("sched-1", rows.first().scheduleId)
        assertEquals("Histology", rows.first().subject)
    }

    @Test
    fun `rec preferences survive missing-class cleanup and unknown prefs are removed`() = runBlocking {
        database.classDao().insertClass(storedClass("class-5", "2026-12-02", "12:00", "13:30"))
        database.reminderDao().savePreference(ReminderPreferenceEntity(classId = "class-5", leadMinutes = 15))
        database.reminderDao().savePreference(ReminderPreferenceEntity(classId = "rec:sched-9:2026-12-03", leadMinutes = 15))
        database.reminderDao().savePreference(ReminderPreferenceEntity(classId = "deleted-class", leadMinutes = 15))

        database.reminderDao().deletePreferencesForMissingClasses()

        val remaining = database.reminderDao().getAllPreferencesSnapshot().map { it.classId }
        assertTrue("class-5" in remaining)
        assertTrue("rec:sched-9:2026-12-03" in remaining)
        assertTrue(remaining.none { it == "deleted-class" })
    }
}
