package com.abnahid.uniblsync

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Verifies the two-stage reminder reconciliation contract:
 * - Stage 1 (ADVANCE) trigger instant = class start minus configured lead
 * - Stage 2 (FINAL) trigger instant = exact class start time (independent of Stage 1)
 * - Stale past classes beyond grace period are skipped
 * - Cancelled classes lose their preference and both scheduled rows
 * - Changing lead time replaces persisted rows without duplicates
 * - `rec:` occurrences are resolved through the occurrence generator
 * - `rec:` preferences survive missing-class cleanup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderReconcilerTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    private val bosnia = "Europe/Sarajevo"

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
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
    fun `two-stage triggers are advance minus leadMinutes and exact class start`() = runBlocking {
        val start = TimezoneHelper.toUtcEpochMs("2026-12-02", "12:00", bosnia)
        val now = start - 60 * 60_000L
        database.classDao().insertClass(storedClass("class-1", "2026-12-02", "12:00", "13:30"))
        val pref = ReminderPreferenceEntity(classId = "class-1", leadMinutes = 15)

        ReminderReconciler.scheduleForPreference(context, database, pref, nowEpochMs = now)

        val scheduled = database.scheduledReminderDao().getScheduledForClass("class-1")
        assertEquals(2, scheduled.size)

        val advance = scheduled.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }
        val finalRow = scheduled.first { it.stage == ScheduledReminderEntity.STAGE_FINAL }
        assertEquals(start - 15 * 60_000L, advance.triggerEpochMs)
        assertEquals(
            ReminderReconciler.reminderKeyFor("class-1", "2026-12-02", ScheduledReminderEntity.STAGE_ADVANCE),
            advance.reminderKey
        )
        assertEquals(start, finalRow.triggerEpochMs)
        assertEquals(
            ReminderReconciler.reminderKeyFor("class-1", "2026-12-02", ScheduledReminderEntity.STAGE_FINAL),
            finalRow.reminderKey
        )
    }

    @Test
    fun `stale past class beyond grace period is skipped and nothing is scheduled`() = runBlocking {
        val start = TimezoneHelper.toUtcEpochMs("2026-10-06", "12:00", bosnia)
        val now = start + 15 * 60_000L
        database.classDao().insertClass(storedClass("class-2", "2026-10-06", "12:00", "13:30"))

        ReminderReconciler.scheduleForPreference(
            context,
            database,
            ReminderPreferenceEntity(classId = "class-2", leadMinutes = 15),
            nowEpochMs = now
        )

        assertTrue(database.scheduledReminderDao().getScheduledForClass("class-2").isEmpty())
    }

    @Test
    fun `cancelled class loses preference and both scheduled stages`() = runBlocking {
        val start = TimezoneHelper.toUtcEpochMs("2026-12-02", "12:00", bosnia)
        val now = start - 60 * 60_000L
        database.classDao().insertClass(storedClass("class-3", "2026-12-02", "12:00", "13:30"))
        val pref = ReminderPreferenceEntity(classId = "class-3", leadMinutes = 15)
        ReminderReconciler.scheduleForPreference(context, database, pref, nowEpochMs = now)
        assertEquals(2, database.scheduledReminderDao().getScheduledForClass("class-3").size)

        database.classDao().insertClass(
            storedClass("class-3", "2026-12-02", "12:00", "13:30", status = ClassEntity.STATUS_CANCELLED)
        )
        database.reminderDao().savePreference(ReminderPreferenceEntity(classId = "class-3", leadMinutes = 15))
        ReminderReconciler.scheduleForPreference(
            context,
            database,
            database.reminderDao().getAllPreferencesSnapshot().first { it.classId == "class-3" },
            nowEpochMs = now
        )

        val prefs = database.reminderDao().getAllPreferencesSnapshot()
        assertTrue(prefs.none { it.classId == "class-3" })
        assertTrue(database.scheduledReminderDao().getScheduledForClass("class-3").isEmpty())
    }

    @Test
    fun `changing the reminder replaces persisted rows without duplicates`() = runBlocking {
        val start = TimezoneHelper.toUtcEpochMs("2026-12-02", "12:00", bosnia)
        val now = start - 90 * 60_000L
        database.classDao().insertClass(storedClass("class-4", "2026-12-02", "12:00", "13:30"))

        ReminderReconciler.scheduleForPreference(
            context, database, ReminderPreferenceEntity(classId = "class-4", leadMinutes = 15), nowEpochMs = now
        )
        val initialRows = database.scheduledReminderDao().getScheduledForClass("class-4")
        assertEquals(2, initialRows.size)
        assertEquals(
            start - 15 * 60_000L,
            initialRows.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }.triggerEpochMs
        )

        ReminderReconciler.scheduleForPreference(
            context, database, ReminderPreferenceEntity(classId = "class-4", leadMinutes = 30), nowEpochMs = now
        )
        val updatedRows = database.scheduledReminderDao().getScheduledForClass("class-4")
        assertEquals(2, updatedRows.size)
        assertEquals(
            start - 30 * 60_000L,
            updatedRows.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }.triggerEpochMs
        )
        assertEquals(
            start,
            updatedRows.first { it.stage == ScheduledReminderEntity.STAGE_FINAL }.triggerEpochMs
        )
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
        val now = expectedStart - 60 * 60_000L

        ReminderReconciler.scheduleForPreference(
            context,
            database,
            ReminderPreferenceEntity(classId = "rec:sched-1:2026-12-01", leadMinutes = 10),
            nowEpochMs = now
        )

        val rows = database.scheduledReminderDao()
            .getScheduledForClass("rec:sched-1:2026-12-01")
        assertEquals(2, rows.size)
        val advance = rows.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }
        val finalRow = rows.first { it.stage == ScheduledReminderEntity.STAGE_FINAL }
        assertEquals(expectedStart - 10 * 60_000L, advance.triggerEpochMs)
        assertEquals(expectedStart, finalRow.triggerEpochMs)
        assertEquals("2026-12-01", advance.occurrenceDate)
        assertEquals("sched-1", advance.scheduleId)
        assertEquals("Histology", advance.subject)
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
