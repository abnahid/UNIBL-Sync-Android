package com.abnahid.uniblsync

import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.data.model.ScheduleOverrideEntity
import com.abnahid.uniblsync.data.schedule.ScheduleOccurrenceGenerator
import com.abnahid.uniblsync.util.TimezoneHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Recurring schedule / date generation tests. Runs on the JVM only — no Android.
 */
class ScheduleOccurrenceGeneratorTest {

    private val zone = ZoneId.of(TimezoneHelper.BOSNIA_TIMEZONE)

    private fun schedule(
        id: String,
        subject: String = "Anatomy",
        classType: String = RecurringScheduleEntity.TYPE_LECTURE,
        weekday: Int,
        start: String,
        end: String,
        groupName: String = "All",
        mode: String = ClassEntity.MODE_IN_PERSON,
        effectiveFrom: String = "2026-10-05",
        effectiveTo: String = "",
        explicitDates: List<String> = emptyList(),
        active: Boolean = true
    ) = RecurringScheduleEntity(
        id = id,
        subject = subject,
        classType = classType,
        weekdayIso = weekday,
        sourceStartTime = start,
        sourceEndTime = end,
        groupName = groupName,
        attendanceMode = mode,
        effectiveFromIsoDate = effectiveFrom,
        effectiveToIsoDate = effectiveTo,
        explicitDates = explicitDates.joinToString(","),
        isActive = active
    )

    @Test
    fun `weekly monday class generates every monday after its effective date`() {
        val mondayLecture = schedule(
            id = "mon-anatomy",
            weekday = 1,
            start = "17:00",
            end = "18:30"
        )

        // Week: Mon 26 Oct .. Sun 1 Nov 2026 (2026-10-26 is a Monday)
        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(mondayLecture),
            emptyList(),
            LocalDate.parse("2026-10-26"),
            LocalDate.parse("2026-11-01")
        )

        assertEquals(listOf("2026-10-26"), occurrences.map { it.sourceDate })
        assertEquals("Anatomy", occurrences.single().subject)
    }

    @Test
    fun `tuesday class is never generated on monday`() {
        val tuesday = schedule(id = "tue-bio-g2", weekday = 2, start = "09:00", end = "10:30")

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(tuesday),
            emptyList(),
            LocalDate.parse("2026-10-26"),
            LocalDate.parse("2026-11-01")
        )

        assertEquals(listOf("2026-10-27"), occurrences.map { it.sourceDate })
    }

    @Test
    fun `weekly class produces one occurrence per matching weekday in a multi week range`() {
        val wednesday = schedule(id = "wed-histology", weekday = 3, start = "14:00", end = "15:30")

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(wednesday),
            emptyList(),
            LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-11-01")
        )

        // Wednesdays: 7, 14, 21, 28 Oct
        assertEquals(listOf("2026-10-07", "2026-10-14", "2026-10-21", "2026-10-28"), occurrences.map { it.sourceDate })
    }

    @Test
    fun `occurrence times are converted with the real timezone offset`() {
        val monday = schedule(id = "mon-physics", weekday = 1, start = "18:30", end = "20:00")

        val occurrence = ScheduleOccurrenceGenerator.generate(
            listOf(monday),
            emptyList(),
            LocalDate.parse("2026-10-26"),
            LocalDate.parse("2026-10-26")
        ).single()

        val startInstant = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(occurrence.utcStartEpochMs), zone
        )
        assertEquals(LocalTime.of(18, 30), startInstant.toLocalTime())
        assertEquals(LocalDate.parse("2026-10-26"), startInstant.toLocalDate())
    }

    @Test
    fun `group filtering data is preserved so groupId chips do not hardcode the UI`() {
        val groupTwo = schedule(
            id = "mon-ms-g2",
            subject = "Medicine and Society",
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "13:00",
            end = "14:30",
            groupName = "Group 2",
            mode = ClassEntity.MODE_ONLINE
        )
        val lecture = schedule(id = "mon-lecture", weekday = 1, start = "14:30", end = "16:00")

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(groupTwo, lecture),
            emptyList(),
            LocalDate.parse("2026-10-26"),
            LocalDate.parse("2026-10-26")
        )

        assertEquals(2, occurrences.size)
        val practical = occurrences.first { it.subject == "Medicine and Society" }
        assertEquals("Group 2", practical.groupName)
        assertEquals(ClassEntity.MODE_ONLINE, practical.attendanceMode)
        // Selecting "All" shows all groups; selecting "Group 2" only the matching rows.
        assertTrue(occurrences.filter { it.groupName == "Group 2" || it.groupName == "All" }
            .containsAll(occurrences))
    }

    @Test
    fun `date specific cancellation hides the class on that date only`() {
        val recurring = schedule(id = "mon-anatomy", weekday = 1, start = "17:00", end = "18:30")
        val cancel = ScheduleOverrideEntity(
            id = "ovr-1",
            scheduleId = "mon-anatomy",
            occurrenceDate = "2026-10-26",
            changeType = RecurringScheduleEntity.ACTION_CANCEL
        )

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(recurring),
            listOf(cancel),
            LocalDate.parse("2026-10-19"),
            LocalDate.parse("2026-11-02")
        )

        val cancelled = occurrences.first { it.sourceDate == "2026-10-26" }
        assertEquals(ClassEntity.STATUS_CANCELLED, cancelled.status)
        // Future occurrences must NOT be affected by a single-date cancel.
        assertTrue(
            occurrences.filter { it.sourceDate != "2026-10-26" }
                .all { it.status == ClassEntity.STATUS_SCHEDULED }
        )
        assertEquals(3, occurrences.size)
    }

    @Test
    fun `date specific reschedule changes one date without touching future occurrences`() {
        val recurring = schedule(id = "tue-bio-g3", subject = "Biology of Cell and Human Genetics", weekday = 2, start = "10:30", end = "12:00")
        val reschedule = ScheduleOverrideEntity(
            id = "ovr-2",
            scheduleId = "tue-bio-g3",
            occurrenceDate = "2026-10-27",
            changeType = RecurringScheduleEntity.ACTION_UPDATE,
            sourceStartTime = "12:00",
            sourceEndTime = "13:30"
        )

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(recurring),
            listOf(reschedule),
            LocalDate.parse("2026-10-20"),
            LocalDate.parse("2026-11-03")
        )

        val moved = occurrences.first { it.sourceDate == "2026-10-27" }
        val later = occurrences.first { it.sourceDate == "2026-11-03" }

        assertEquals("12:00", moved.sourceStartTime)
        assertEquals(ClassEntity.STATUS_SCHEDULED, moved.status)
        assertEquals("10:30", later.sourceStartTime) // future repeat keeps original time
    }

    @Test
    fun `override precedence wins over the recurring definition`() {
        val recurring = schedule(
            id = "fri-ms-g1",
            subject = "Medicine and Society",
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 5,
            start = "12:00",
            end = "13:30",
            mode = ClassEntity.MODE_HYBRID
        )
        val override = ScheduleOverrideEntity(
            id = "ovr-3",
            scheduleId = "fri-ms-g1",
            occurrenceDate = "2026-10-30",
            changeType = RecurringScheduleEntity.ACTION_UPDATE,
            locationOrUrl = "Amphitheatre, Faculty of Medicine"
        )

        val occurrence = ScheduleOccurrenceGenerator.generate(
            listOf(recurring),
            listOf(override),
            LocalDate.parse("2026-10-30"),
            LocalDate.parse("2026-10-30")
        ).single()

        assertEquals("Amphitheatre, Faculty of Medicine", occurrence.locationOrUrl)
        assertEquals(ClassEntity.MODE_HYBRID, occurrence.attendanceMode)
    }

    @Test
    fun `explicit dates generate only the published dates`() {
        val serbian = schedule(
            id = "serbian-slot-a",
            subject = "Serbian 1",
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "11:30",
            end = "12:15",
            groupName = "Group 1",
            explicitDates = listOf(
                "2026-10-12", "2026-10-26", "2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21"
            )
        )

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(serbian),
            emptyList(),
            LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-12-31")
        )

        assertEquals(
            listOf("2026-10-12", "2026-10-26", "2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21"),
            occurrences.map { it.sourceDate }
        )
    }

    @Test
    fun `dates before the effective start produce nothing`() {
        val classSchedule = schedule(id = "late-class", weekday = 1, start = "09:00", end = "10:00", effectiveFrom = "2026-10-12")

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(classSchedule),
            emptyList(),
            LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-10-11")
        )

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `semester end date stops occurrences`() {
        val classSchedule = schedule(
            id = "bounded-class",
            weekday = 2,
            start = "09:00",
            end = "10:00",
            effectiveFrom = "2026-10-01",
            effectiveTo = "2026-10-15"
        )

        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(classSchedule),
            emptyList(),
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-11-30")
        )

        // Tuesdays up to 2026-10-15 inclusive: 6 Oct, 13 Oct.
        assertEquals(listOf("2026-10-06", "2026-10-13"), occurrences.map { it.sourceDate })
    }

    @Test
    fun `duplicate occurrences are prevented by deterministic ids`() {
        val mondayA = schedule(id = "mon-g4", subject = "Biology of Cell and Human Genetics", classType = RecurringScheduleEntity.TYPE_PRACTICAL, weekday = 1, start = "12:30", end = "14:00", groupName = "Group 4", mode = ClassEntity.MODE_ONLINE)

        // Passing the same definition twice must not duplicate the occurrence.
        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(mondayA, mondayA),
            emptyList(),
            LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-10-05")
        )

        assertEquals(1, occurrences.size)
        assertEquals("rec:mon-g4:2026-10-05", occurrences.single().id)
    }

    @Test
    fun `one time override adds an extra class without a recurring definition`() {
        val add = ScheduleOverrideEntity(
            id = "ovr-add-1",
            scheduleId = "",
            occurrenceDate = "2026-11-28",
            changeType = RecurringScheduleEntity.ACTION_ADD,
            subject = "Medicine and Society",
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            sourceStartTime = "18:00",
            sourceEndTime = "19:30",
            groupName = "All",
            attendanceMode = ClassEntity.MODE_IN_PERSON,
            locationOrUrl = "Amphitheatre, Faculty of Medicine"
        )

        val occurrences = ScheduleOccurrenceGenerator.generate(
            emptyList(),
            listOf(add),
            LocalDate.parse("2026-11-23"),
            LocalDate.parse("2026-11-29")
        )

        val added = occurrences.single()
        assertEquals("ovr:ovr-add-1", added.id)
        assertEquals("2026-11-28", added.sourceDate)
        assertEquals("Medicine and Society", added.subject)
        assertEquals(ClassEntity.MODE_IN_PERSON, added.attendanceMode)
    }

    @Test
    fun `unknown semester end stays open ended instead of inventing a date`() {
        val openEnded = schedule(id = "open-class", weekday = 4, start = "11:45", end = "13:15", effectiveTo = "")
        val range = ScheduleOccurrenceGenerator.generate(
            listOf(openEnded),
            emptyList(),
            LocalDate.parse("2026-10-08"),
            LocalDate.parse("2026-11-05")
        )
        // Thursdays: 8, 15, 22, 29 Oct + 5 Nov (Nov 5 2026 is a Thursday)
        assertTrue(range.size >= 4)
        assertTrue(range.all { it.effectiveIsThursday() })
    }

    @Test
    fun `inactive schedule produces no occurrences`() {
        val inactive = schedule(id = "off-class", weekday = 5, start = "12:00", end = "13:30", active = false)
        val occurrences = ScheduleOccurrenceGenerator.generate(
            listOf(inactive),
            emptyList(),
            LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-10-12")
        )
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `seed contains no january classes because the source leaves them to be defined`() {
        val serbian = com.abnahid.uniblsync.data.schedule.FirstSemesterSeed.buildSeed()
            .first { it.id == "seed-serbian1-slot-a" }
        val dates = ScheduleOccurrenceGenerator.parseExplicitDates(serbian.explicitDates)
        assertTrue(dates.none { it.startsWith("2027-01") || it.startsWith("2026-01") })
        assertEquals("2026-12-21", dates.last())
    }

    private fun ClassEntity.effectiveIsThursday(): Boolean {
        val startInstant = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(utcStartEpochMs), zone)
        return startInstant.dayOfWeek.value == 4
    }
}
