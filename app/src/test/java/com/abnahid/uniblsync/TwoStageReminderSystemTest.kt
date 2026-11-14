package com.abnahid.uniblsync

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ScheduledReminderEntity
import com.abnahid.uniblsync.data.schedule.ReminderReconciler
import com.abnahid.uniblsync.util.BatteryOptimizationHelper
import com.abnahid.uniblsync.util.ClassReminderReceiver
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderBootReceiver
import com.abnahid.uniblsync.util.ReminderGlobalStore
import com.abnahid.uniblsync.util.ReminderScheduler
import com.abnahid.uniblsync.util.ScheduleOutcome
import com.abnahid.uniblsync.util.TimezoneHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPowerManager

/**
 * End-to-end verification suite for the Two-Stage Class Reminder System
 * covering Scenarios A–O plus non-destructive DB MIGRATION_5_6.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TwoStageReminderSystemTest {

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    private val bosnia = TimezoneHelper.BOSNIA_TIMEZONE

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowAlarmManager = Shadows.shadowOf(alarmManager)
        shadowNotificationManager = Shadows.shadowOf(notificationManager)
        notificationManager.cancelAll()

        // Reset global store defaults for clean test isolation
        val store = ReminderGlobalStore.getInstance(context)
        store.leadMinutes = 20
        store.gracePeriodMinutes = 5
        store.isAlarmEnabled = true
        store.isVibrateEnabled = true
        store.soundTone = "Gentle Chime"
        store.selectedTimezone = TimezoneHelper.BANGLADESH_TIMEZONE

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        database.close()
    }

    private fun testClass(
        id: String = "anatomy-1",
        subject: String = "Anatomy",
        date: String = "2026-12-10",
        startTime: String = "16:45",
        endTime: String = "18:15",
        status: String = ClassEntity.STATUS_SCHEDULED,
        locationOrUrl: String = "https://teams.microsoft.com/l/meetup-join/anatomy"
    ): ClassEntity = ClassEntity(
        id = id,
        subject = subject,
        classType = RecurringScheduleEntity.TYPE_LECTURE,
        instructor = "Dr. Kovac",
        sourceDate = date,
        sourceStartTime = startTime,
        sourceEndTime = endTime,
        sourceTimezone = bosnia,
        utcStartEpochMs = TimezoneHelper.toUtcEpochMs(date, startTime, bosnia),
        utcEndEpochMs = TimezoneHelper.toUtcEpochMs(date, endTime, bosnia),
        groupName = "Group 1",
        attendanceMode = ClassEntity.MODE_ONLINE,
        platform = ClassEntity.PLATFORM_TEAMS,
        locationOrUrl = locationOrUrl,
        status = status
    )

    private fun notificationTitle(n: Notification): String =
        n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    private fun notificationText(n: Notification): String =
        n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    @Test
    fun `Scenario A - app opens 30m before class schedules both advance and final alarms with zero immediate notifications`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 30 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        assertTrue(shadowNotificationManager.allNotifications.isEmpty())
        val rows = database.scheduledReminderDao().getScheduledForClass(cls.id)
        assertEquals(2, rows.size)
        val advance = rows.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }
        val finalRow = rows.first { it.stage == ScheduledReminderEntity.STAGE_FINAL }
        assertEquals(cls.utcStartEpochMs - 20 * 60_000L, advance.triggerEpochMs)
        assertEquals(cls.utcStartEpochMs, finalRow.triggerEpochMs)
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `Scenario B - app opens 10m before class fires immediate catch-up advance and arms final alarm`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 10 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        assertTrue(notificationText(notifications.first()).contains("starts in 10 minutes"))

        val rows = database.scheduledReminderDao().getScheduledForClass(cls.id)
        assertEquals(1, rows.size)
        assertEquals(ScheduledReminderEntity.STAGE_FINAL, rows.single().stage)
        assertEquals(cls.utcStartEpochMs, rows.single().triggerEpochMs)
    }

    @Test
    fun `Scenario C - app opens 2m before 4-45 PM class sends immediate catch-up and does not duplicate on repeated launch or sync`() = runBlocking {
        val cls = testClass(startTime = "16:45", endTime = "18:15")
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 2 * 60_000L // 4:43 PM Sarajevo

        // First launch
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        // Repeated launch & background sync
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now + 5_000L)
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now + 15_000L)

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val body = notificationText(notifications.single())
        assertTrue("Expected catch-up body for 2 minutes, got: $body", body.contains("Your Anatomy class starts in 2 minutes!"))

        // Final reminder must still be scheduled for 4:45 PM
        val rows = database.scheduledReminderDao().getScheduledForClass(cls.id)
        assertEquals(1, rows.size)
        assertEquals(ScheduledReminderEntity.STAGE_FINAL, rows.single().stage)
        assertEquals(cls.utcStartEpochMs, rows.single().triggerEpochMs)

        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )
        assertNotNull(state)
        assertTrue(state!!.advanceDelivered)
        assertFalse(state.finalDelivered)
        assertEquals(0, state.repeatCount)
    }

    @Test
    fun `Scenario D - at class start and within 5m grace period skips advance and triggers final notification once`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs + 2 * 60_000L // 2 minutes past start (within 5m grace)

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        // Re-running reconciliation must NOT duplicate the final alert
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now + 10_000L)

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        assertEquals("Your class is starting now!", notificationTitle(notifications.single()))
        assertTrue(notificationText(notifications.single()).contains("Anatomy is starting. Join your class now."))

        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertEquals(ClassReminderStateEntity.STATUS_SKIPPED_PAST, state.advanceScheduledStatus)
        assertTrue(state.finalDelivered)
        assertFalse(state.advanceDelivered)
    }

    @Test
    fun `Scenario E - 10m after class start exceeds 5m grace period and sends zero stale notifications`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs + 10 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        assertTrue(shadowNotificationManager.allNotifications.isEmpty())
        assertTrue(database.scheduledReminderDao().getScheduledForClass(cls.id).isEmpty())
        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )
        // Far past class with no prior state produces no notification and no stale alarms
        assertTrue(state == null || state.finalScheduledStatus == ClassReminderStateEntity.STATUS_SKIPPED_STALE)

        // Also test late broadcast receiver delivery guard
        val lateIntent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_CLASS_REMINDER
            putExtra(ClassReminderReceiver.EXTRA_CLASS_ID, cls.id)
            putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE_DATE, cls.sourceDate)
            putExtra(ClassReminderReceiver.EXTRA_STAGE, ScheduledReminderEntity.STAGE_FINAL)
            putExtra(ClassReminderReceiver.EXTRA_CLASS_START_EPOCH_MS, cls.utcStartEpochMs)
            putExtra(ClassReminderReceiver.EXTRA_SUBJECT, cls.subject)
        }
        ClassReminderReceiver().handleClassReminder(context, lateIntent, databaseOverride = database, nowEpochMs = now)
        assertTrue(shadowNotificationManager.allNotifications.isEmpty())
    }

    @Test
    fun `Scenario F - device reboot re-arms future alarms via ReminderBootReceiver contract`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 60 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        // Simulate system clearing AlarmManager state across reboot
        shadowAlarmManager.scheduledAlarms.forEach { it.operation?.let { op -> alarmManager.cancel(op) } }
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        // Boot restore calls reconcileArmedAlarms
        ReminderReconciler.reconcileArmedAlarms(context, database, nowEpochMs = now + 60_000L)

        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)
        // Also ensure ReminderBootReceiver accepts BOOT_COMPLETED without throwing
        ReminderBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `Scenario G - timetable update cancels old alarms resets delivery flags and arms new times`() = runBlocking {
        val original = testClass(startTime = "10:00", endTime = "11:30")
        database.classDao().insertClass(original)
        val now = original.utcStartEpochMs - 10 * 60_000L

        // Catch-up advance fires for original 10:00 time
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        notificationManager.cancelAll()

        // Reschedule class to 15:00 same day
        val rescheduled = original.copy(
            sourceStartTime = "15:00",
            sourceEndTime = "16:30",
            utcStartEpochMs = TimezoneHelper.toUtcEpochMs(original.sourceDate, "15:00", bosnia),
            utcEndEpochMs = TimezoneHelper.toUtcEpochMs(original.sourceDate, "16:30", bosnia),
            status = ClassEntity.STATUS_RESCHEDULED
        )
        database.classDao().insertClass(rescheduled)
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        val rows = database.scheduledReminderDao().getScheduledForClass(original.id)
        assertEquals(2, rows.size)
        assertEquals(rescheduled.utcStartEpochMs - 20 * 60_000L, rows.first { it.stage == ScheduledReminderEntity.STAGE_ADVANCE }.triggerEpochMs)
        assertEquals(rescheduled.utcStartEpochMs, rows.first { it.stage == ScheduledReminderEntity.STAGE_FINAL }.triggerEpochMs)

        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(original.id, original.sourceDate)
        )!!
        assertFalse(state.advanceDelivered)
        assertFalse(state.finalDelivered)
    }

    @Test
    fun `Scenario H - cancelled class cancels both alarms and receiver refuses to notify`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 60 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

        val cancelled = cls.copy(status = ClassEntity.STATUS_CANCELLED)
        database.classDao().insertClass(cancelled)
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now + 10_000L)

        assertTrue(database.scheduledReminderDao().getScheduledForClass(cls.id).isEmpty())
        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertEquals(ClassReminderStateEntity.STATUS_CANCELLED, state.advanceScheduledStatus)
        assertEquals(ClassReminderStateEntity.STATUS_CANCELLED, state.finalScheduledStatus)

        // Even if a stray broadcast arrives, receiver skips cancelled classes
        val strayIntent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_CLASS_REMINDER
            putExtra(ClassReminderReceiver.EXTRA_CLASS_ID, cls.id)
            putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE_DATE, cls.sourceDate)
            putExtra(ClassReminderReceiver.EXTRA_STAGE, ScheduledReminderEntity.STAGE_FINAL)
        }
        ClassReminderReceiver().handleClassReminder(context, strayIntent, databaseOverride = database, nowEpochMs = cls.utcStartEpochMs)
        assertTrue(shadowNotificationManager.allNotifications.isEmpty())
    }

    @Test
    fun `Scenario I - timezone changes and DST keep canonical UTC trigger instant intact without double conversion`() = runBlocking {
        // March 29, 2026: Europe/Sarajevo transitions CET (+01:00) -> CEST (+02:00)
        // Class at 10:00 Europe/Sarajevo = 08:00 UTC (CEST)
        val cls = testClass(id = "dst-class", date = "2026-03-30", startTime = "10:00", endTime = "11:30")
        database.classDao().insertClass(cls)
        val expectedStartUtc = TimezoneHelper.toUtcEpochMs("2026-03-30", "10:00", bosnia)
        val now = expectedStartUtc - 60 * 60_000L

        ReminderGlobalStore.getInstance(context).selectedTimezone = "Asia/Dhaka"
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        val dhakaFinal = database.scheduledReminderDao().getScheduledForClass(cls.id)
            .first { it.stage == ScheduledReminderEntity.STAGE_FINAL }.triggerEpochMs

        // Student changes timezone to Europe/London
        ReminderGlobalStore.getInstance(context).selectedTimezone = "Europe/London"
        ReminderReconciler.reconcileArmedAlarms(context, database, nowEpochMs = now + 5_000L)
        val londonFinal = database.scheduledReminderDao().getScheduledForClass(cls.id)
            .first { it.stage == ScheduledReminderEntity.STAGE_FINAL }.triggerEpochMs

        assertEquals(expectedStartUtc, dhakaFinal)
        assertEquals(expectedStartUtc, londonFinal)
        assertEquals("09:00 AM", TimezoneHelper.formatToLocalTime(expectedStartUtc, "Europe/London"))
        assertEquals("02:00 PM", TimezoneHelper.formatToLocalTime(expectedStartUtc, "Asia/Dhaka"))
    }

    @Test
    fun `Scenario J - denied POST_NOTIFICATIONS blocks safely and recovers once granted`() = runBlocking {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 5 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)
        assertTrue(shadowNotificationManager.allNotifications.isEmpty())

        val blockedState = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertEquals(ClassReminderStateEntity.STATUS_BLOCKED_NO_PERMISSION, blockedState.advanceScheduledStatus)
        assertFalse(blockedState.advanceDelivered)

        // User grants permission and app reconciles
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now + 1_000L)

        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val recoveredState = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertTrue(recoveredState.advanceDelivered)
    }

    @Test
    fun `Scenario K - denied SCHEDULE_EXACT_ALARM uses explicit inexact fallback and reports InexactFallback`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ReminderScheduler.canScheduleExactAlarms(context))

        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 60 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertEquals(ClassReminderStateEntity.STATUS_SCHEDULED_INEXACT, state.advanceScheduledStatus)
        assertEquals(ClassReminderStateEntity.STATUS_SCHEDULED_INEXACT, state.finalScheduledStatus)
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `Scenario L - battery optimization check works and ExactAndAllowWhileIdle arms RTC_WAKEUP alarms`() = runBlocking {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val shadowPower: ShadowPowerManager = Shadows.shadowOf(powerManager)
        shadowPower.setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))

        shadowPower.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))

        val cls = testClass()
        database.classDao().insertClass(cls)
        val now = cls.utcStartEpochMs - 60 * 60_000L
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)
        assertTrue(shadowAlarmManager.scheduledAlarms.all { it.type == AlarmManager.RTC_WAKEUP })
    }

    @Test
    fun `Scenario M - process restart restores persisted state prevents duplicate delivery and arms remaining stage`() = runBlocking {
        val cls = testClass()
        database.classDao().insertClass(cls)
        val start = cls.utcStartEpochMs

        // Run catch-up advance 5 minutes before start
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = start - 5 * 60_000L)
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        // Simulate app process kill & restart: clear in-memory AlarmManager & reconcile again
        shadowAlarmManager.scheduledAlarms.forEach { it.operation?.let { op -> alarmManager.cancel(op) } }
        ReminderReconciler.reconcileAll(context, database, nowEpochMs = start - 4 * 60_000L)

        // Still only 1 advance notification (no duplicate), and FINAL alarm re-armed cleanly
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val rows = database.scheduledReminderDao().getScheduledForClass(cls.id)
        assertEquals(1, rows.size)
        assertEquals(ScheduledReminderEntity.STAGE_FINAL, rows.single().stage)
    }

    @Test
    fun `Scenario N - overlapping classes have distinct PendingIntents and independent notifications`() = runBlocking {
        val c1 = testClass(id = "c-overlap-1", subject = "Anatomy", startTime = "14:00", endTime = "15:30")
        val c2 = testClass(id = "c-overlap-2", subject = "Biochemistry", startTime = "14:00", endTime = "15:30")
        database.classDao().insertClass(c1)
        database.classDao().insertClass(c2)
        val now = c1.utcStartEpochMs - 60 * 60_000L

        ReminderReconciler.reconcileAll(context, database, nowEpochMs = now)

        assertEquals(4, shadowAlarmManager.scheduledAlarms.size)
        val key1Adv = ReminderReconciler.reminderKeyFor(c1.id, c1.sourceDate, ScheduledReminderEntity.STAGE_ADVANCE)
        val key2Adv = ReminderReconciler.reminderKeyFor(c2.id, c2.sourceDate, ScheduledReminderEntity.STAGE_ADVANCE)
        val key1Fin = ReminderReconciler.reminderKeyFor(c1.id, c1.sourceDate, ScheduledReminderEntity.STAGE_FINAL)
        assertNotEquals(
            ReminderScheduler.requestCodeFor(key1Adv, ScheduledReminderEntity.STAGE_ADVANCE),
            ReminderScheduler.requestCodeFor(key2Adv, ScheduledReminderEntity.STAGE_ADVANCE)
        )
        assertNotEquals(
            ReminderScheduler.requestCodeFor(key1Adv, ScheduledReminderEntity.STAGE_ADVANCE),
            ReminderScheduler.requestCodeFor(key1Fin, ScheduledReminderEntity.STAGE_FINAL)
        )

        // Fire both FINAL broadcasts at class start
        val receiver = ClassReminderReceiver()
        for (c in listOf(c1, c2)) {
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                action = ClassReminderReceiver.ACTION_CLASS_REMINDER
                putExtra(ClassReminderReceiver.EXTRA_REMINDER_KEY, ReminderReconciler.reminderKeyFor(c.id, c.sourceDate, ScheduledReminderEntity.STAGE_FINAL))
                putExtra(ClassReminderReceiver.EXTRA_CLASS_ID, c.id)
                putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE_DATE, c.sourceDate)
                putExtra(ClassReminderReceiver.EXTRA_STAGE, ScheduledReminderEntity.STAGE_FINAL)
                putExtra(ClassReminderReceiver.EXTRA_CLASS_START_EPOCH_MS, c.utcStartEpochMs)
                putExtra(ClassReminderReceiver.EXTRA_SUBJECT, c.subject)
                putExtra(ClassReminderReceiver.EXTRA_LOCATION_OR_URL, c.locationOrUrl)
            }
            receiver.handleClassReminder(context, intent, databaseOverride = database, nowEpochMs = c.utcStartEpochMs)
        }

        assertEquals(2, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `Scenario O - final UX uses one-time non-repeating notification with Join Class action and no Dismiss button`() = runBlocking {
        val cls = testClass(locationOrUrl = "https://teams.microsoft.com/l/meetup-join/final-ux")
        database.classDao().insertClass(cls)

        val finalIntent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_CLASS_REMINDER
            putExtra(ClassReminderReceiver.EXTRA_REMINDER_KEY, ReminderReconciler.reminderKeyFor(cls.id, cls.sourceDate, ScheduledReminderEntity.STAGE_FINAL))
            putExtra(ClassReminderReceiver.EXTRA_CLASS_ID, cls.id)
            putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE_DATE, cls.sourceDate)
            putExtra(ClassReminderReceiver.EXTRA_STAGE, ScheduledReminderEntity.STAGE_FINAL)
            putExtra(ClassReminderReceiver.EXTRA_CLASS_START_EPOCH_MS, cls.utcStartEpochMs)
            putExtra(ClassReminderReceiver.EXTRA_SUBJECT, cls.subject)
            putExtra(ClassReminderReceiver.EXTRA_LOCATION_OR_URL, cls.locationOrUrl)
        }

        ClassReminderReceiver().handleClassReminder(context, finalIntent, databaseOverride = database, nowEpochMs = cls.utcStartEpochMs)

        // Only 1 notification, zero repeat alarms scheduled
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        val notification = shadowNotificationManager.allNotifications.single()
        assertEquals(NotificationHelper.CHANNEL_CLASS_START_ID, notification.channelId)
        assertEquals("Your class is starting now!", notificationTitle(notification))
        assertTrue(notificationText(notification).contains("Anatomy is starting. Join your class now."))
        assertEquals(0, notification.flags and Notification.FLAG_INSISTENT)

        val actions = notification.actions?.map { it.title.toString() }.orEmpty()
        assertTrue("Expected Join Class action", "Join Class" in actions)
        assertFalse("Dismiss action must NOT be added", actions.any { it.contains("Dismiss", ignoreCase = true) })

        val state = database.classReminderStateDao().getByOccurrenceKey(
            ClassReminderStateEntity.buildOccurrenceKey(cls.id, cls.sourceDate)
        )!!
        assertEquals(0, state.repeatCount)
        assertFalse(state.dismissed)
        assertTrue(state.finalDelivered)

        // Second broadcast for same class must be rejected cleanly
        ClassReminderReceiver().handleClassReminder(context, finalIntent, databaseOverride = database, nowEpochMs = cls.utcStartEpochMs + 60_000L)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `non-destructive MIGRATION_5_6 preserves existing v5 data and creates state and log tables`() {
        val dbName = "migration_5_6_test.db"
        context.deleteDatabase(dbName)

        // Create a real v5 schema SQLite database and insert v5 rows
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `classes` (`id` TEXT NOT NULL, `subject` TEXT NOT NULL, `classType` TEXT NOT NULL, " +
                                "`instructor` TEXT NOT NULL, `sourceDate` TEXT NOT NULL, `sourceStartTime` TEXT NOT NULL, " +
                                "`sourceEndTime` TEXT NOT NULL, `sourceTimezone` TEXT NOT NULL, `utcStartEpochMs` INTEGER NOT NULL, " +
                                "`utcEndEpochMs` INTEGER NOT NULL, `groupName` TEXT NOT NULL, `attendanceMode` TEXT NOT NULL, " +
                                "`platform` TEXT NOT NULL, `teamsChannel` TEXT NOT NULL, `locationOrUrl` TEXT NOT NULL, " +
                                "`status` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE `scheduled_reminders` (`reminderKey` TEXT NOT NULL, `classId` TEXT NOT NULL, " +
                                "`occurrenceDate` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `triggerEpochMs` INTEGER NOT NULL, " +
                                "`leadMinutes` INTEGER NOT NULL, `isAlarmEnabled` INTEGER NOT NULL, `soundTone` TEXT NOT NULL, " +
                                "`vibrate` INTEGER NOT NULL, `subject` TEXT NOT NULL, `classType` TEXT NOT NULL, " +
                                "`groupName` TEXT NOT NULL, `attendanceMode` TEXT NOT NULL, `sourceStartTime` TEXT NOT NULL, " +
                                "`sourceEndTime` TEXT NOT NULL, `locationOrUrl` TEXT NOT NULL, PRIMARY KEY(`reminderKey`))"
                        )
                        db.execSQL(
                            "INSERT INTO `classes` VALUES ('v5-c1','Biochemistry','Lecture','Dr. Mark','2026-12-10','09:00','10:30'," +
                                "'Europe/Sarajevo',1796889600000,1796895000000,'All','In person','Classroom','General','Room A','SCHEDULED','',1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val v5Db = helper.writableDatabase
        AppDatabase.MIGRATION_5_6.migrate(v5Db)

        // Verify v5 row survived and new v6 columns/tables exist
        val cursor = v5Db.query("SELECT subject FROM classes WHERE id = 'v5-c1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Biochemistry", cursor.getString(0))
        cursor.close()

        val stateCursor = v5Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='class_reminder_states'")
        assertTrue(stateCursor.moveToFirst())
        stateCursor.close()

        val logCursor = v5Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='reminder_diagnostic_logs'")
        assertTrue(logCursor.moveToFirst())
        logCursor.close()

        v5Db.close()
        context.deleteDatabase(dbName)
    }
}
