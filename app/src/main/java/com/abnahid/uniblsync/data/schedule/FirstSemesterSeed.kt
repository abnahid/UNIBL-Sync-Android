package com.abnahid.uniblsync.data.schedule

import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.RecurringScheduleEntity
import com.abnahid.uniblsync.util.TimezoneHelper

/**
 * Initial first semester routine (Faculty of Medicine, 1st semester, academic
 * year 2026/2027, Medicine in English), transcribed from the official
 * "Class Schedule I semester 2026-2027" document.
 *
 * Facts from the source document:
 *  - Every practical startTime/slot below is announced in Bosnia time (Europe/Sarajevo).
 *  - Serbian 1 alternating fortnight practicals have explicit published dates.
 *  - January dates are marked "to be defined" in the source and are therefore not seeded.
 *  - Instructors and meeting links are not published in the source and are left empty.
 */
object FirstSemesterSeed {

    /** First published study day of the semester (Monday, 5 October 2026). */
    const val SEMESTER_START = "2026-10-05"

    /** Serbian 1 slot A dates (11:30-12.15, published list; January TBD in the source). */
    private val SERBIAN_SLOT_A_DATES =
        listOf("2026-10-12", "2026-10-26", "2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21")

    /** Serbian 1 slot B dates (13.00-13.45, published list; January TBD in the source). */
    private val SERBIAN_SLOT_B_DATES =
        listOf("2026-10-05", "2026-10-19", "2026-11-02", "2026-11-16", "2026-11-30", "2026-12-14", "2026-12-28")

    private const val BIO = "Biology of Cell and Human Genetics"
    private const val MS = "Medicine and Society"
    private const val SERBIAN = "Serbian 1"
    private const val ANATOMY = "Anatomy"
    private const val PHYSICS = "Medical Physics"
    private const val HISTOLOGY = "Histology and Embryology"

    private const val MALA_SALA = "Mala sala, 3rd floor, Faculty of Medicine"
    private const val SEMINARSKA_SALA = "Seminarska sala 1, 1st floor, Faculty of Medicine"
    private const val PLAVA_SALA = "Plava sala, Faculty of Medicine"
    private const val AMPHITHEATRE = "Amphitheatre, Faculty of Medicine"
    private const val ONLINE = "Online"

    fun buildSeed(): List<RecurringScheduleEntity> = listOf(
        // ---------------------------------------------------------------- Monday
        schedule(
            id = "seed-serbian1-slot-a",
            subject = SERBIAN,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "11:30",
            end = "12:15",
            groupName = "Group 1",
            mode = ClassEntity.MODE_IN_PERSON,
            location = MALA_SALA,
            explicitDates = SERBIAN_SLOT_A_DATES
        ),
        schedule(
            id = "seed-serbian1-slot-b",
            subject = SERBIAN,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "13:00",
            end = "13:45",
            groupName = "Group 1",
            mode = ClassEntity.MODE_IN_PERSON,
            location = MALA_SALA,
            explicitDates = SERBIAN_SLOT_B_DATES
        ),
        schedule(
            id = "seed-mon-biogenetics-g4",
            subject = BIO,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "12:30",
            end = "14:00",
            groupName = "Group 4",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-mon-ms-g2",
            subject = MS,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 1,
            start = "13:00",
            end = "14:30",
            groupName = "Group 2",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-mon-biogenetics-lecture",
            subject = BIO,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 1,
            start = "14:30",
            end = "16:00",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = PLAVA_SALA
        ),
        schedule(
            id = "seed-mon-anatomy-lecture",
            subject = ANATOMY,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 1,
            start = "17:00",
            end = "18:30",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = AMPHITHEATRE
        ),
        schedule(
            id = "seed-mon-physics-lecture",
            subject = PHYSICS,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 1,
            start = "18:30",
            end = "20:00",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = AMPHITHEATRE
        ),

        // ---------------------------------------------------------------- Tuesday
        schedule(
            id = "seed-tue-biogenetics-g2",
            subject = BIO,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 2,
            start = "09:00",
            end = "10:30",
            groupName = "Group 2",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-tue-biogenetics-g3",
            subject = BIO,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 2,
            start = "10:30",
            end = "12:00",
            groupName = "Group 3",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-tue-ms-g3",
            subject = MS,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 2,
            start = "13:00",
            end = "14:30",
            groupName = "Group 3",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-tue-ms-lecture",
            subject = MS,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 2,
            start = "18:00",
            end = "19:30",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = AMPHITHEATRE
        ),

        // ---------------------------------------------------------------- Wednesday
        schedule(
            id = "seed-wed-biogenetics-g1",
            subject = BIO,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 3,
            start = "12:00",
            end = "13:30",
            groupName = "Group 1",
            mode = ClassEntity.MODE_HYBRID,
            location = SEMINARSKA_SALA,
            notes = "Runs while online teaching applies (samo dok ide online nastava)"
        ),
        schedule(
            id = "seed-wed-ms-g4",
            subject = MS,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 3,
            start = "12:00",
            end = "13:30",
            groupName = "Group 4",
            mode = ClassEntity.MODE_ONLINE,
            location = ONLINE
        ),
        schedule(
            id = "seed-wed-histology-lecture",
            subject = HISTOLOGY,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 3,
            start = "14:00",
            end = "15:30",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = AMPHITHEATRE
        ),

        // ---------------------------------------------------------------- Thursday
        schedule(
            id = "seed-thu-anatomy-lecture",
            subject = ANATOMY,
            classType = RecurringScheduleEntity.TYPE_LECTURE,
            weekday = 4,
            start = "11:45",
            end = "13:15",
            groupName = "All",
            mode = ClassEntity.MODE_IN_PERSON,
            location = AMPHITHEATRE
        ),

        // ---------------------------------------------------------------- Friday
        schedule(
            id = "seed-fri-ms-g1",
            subject = MS,
            classType = RecurringScheduleEntity.TYPE_PRACTICAL,
            weekday = 5,
            start = "12:00",
            end = "13:30",
            groupName = "Group 1",
            mode = ClassEntity.MODE_HYBRID,
            location = MALA_SALA,
            notes = "Online + in person"
        )
    )

    private fun schedule(
        id: String,
        subject: String,
        classType: String,
        weekday: Int,
        start: String,
        end: String,
        groupName: String,
        mode: String,
        location: String,
        notes: String = "",
        explicitDates: List<String> = emptyList()
    ) = RecurringScheduleEntity(
        id = id,
        subject = subject,
        classType = classType,
        weekdayIso = weekday,
        sourceStartTime = start,
        sourceEndTime = end,
        sourceTimezone = TimezoneHelper.BOSNIA_TIMEZONE,
        groupName = groupName,
        attendanceMode = mode,
        platform = "",
        locationOrUrl = location,
        instructor = "",
        notes = notes,
        effectiveFromIsoDate = SEMESTER_START,
        effectiveToIsoDate = "", // open ended until the faculty publishes the semester end
        explicitDates = explicitDates.joinToString(","),
        isActive = true
    )

    /**
     * Seeds the recurring definitions once. Safe to call on every launch:
     * existing rows and user overrides are never overwritten by the seed.
     */
    suspend fun seedIfNeeded(database: AppDatabase): Boolean {
        val dao = database.recurringScheduleDao()
        if (dao.count() > 0) return false
        dao.insertAll(buildSeed())
        return true
    }
}
