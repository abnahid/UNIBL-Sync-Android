package com.example.data.repository

import com.example.data.local.AnnouncementDao
import com.example.data.local.AuditLogDao
import com.example.data.local.ClassDao
import com.example.data.local.ReminderDao
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.local.VisitorDao
import com.example.data.model.ReminderPreferenceEntity
import com.example.data.model.VisitorEntity
import com.example.util.TimezoneHelper
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ClassRepository(
    private val classDao: ClassDao,
    private val reminderDao: ReminderDao,
    private val announcementDao: AnnouncementDao,
    private val auditLogDao: AuditLogDao,
    private val visitorDao: VisitorDao
) {
    val allClasses: Flow<List<ClassEntity>> = classDao.getAllClasses()
    val allPreferences: Flow<List<ReminderPreferenceEntity>> = reminderDao.getAllPreferences()
    val allAnnouncements: Flow<List<AnnouncementEntity>> = announcementDao.getAllAnnouncements()
    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()
    val allVisitors: Flow<List<VisitorEntity>> = visitorDao.getAllVisitors()

    suspend fun registerVisitor(
        name: String,
        email: String,
        purpose: String,
        group: String = "All"
    ): VisitorEntity {
        val entity = VisitorEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            purpose = purpose,
            targetGroup = group
        )
        visitorDao.insertVisitor(entity)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "VISITOR_REGISTERED",
                subject = name,
                details = "Registered visitor for group $group: $purpose ($email)"
            )
        )

        return entity
    }

    suspend fun createClass(
        subject: String,
        classType: String,
        instructor: String,
        sourceDate: String,
        sourceStartTime: String,
        sourceEndTime: String,
        sourceTimezone: String = TimezoneHelper.BOSNIA_TIMEZONE,
        groupName: String = "All",
        attendanceMode: String = ClassEntity.MODE_HYBRID,
        platform: String = ClassEntity.PLATFORM_TEAMS,
        teamsChannel: String = "General",
        locationOrUrl: String = "",
        notes: String = ""
    ): ClassEntity {
        val utcStart = TimezoneHelper.toUtcEpochMs(sourceDate, sourceStartTime, sourceTimezone)
        val utcEnd = TimezoneHelper.toUtcEpochMs(sourceDate, sourceEndTime, sourceTimezone)

        val entity = ClassEntity(
            id = UUID.randomUUID().toString(),
            subject = subject,
            classType = classType,
            instructor = instructor,
            sourceDate = sourceDate,
            sourceStartTime = sourceStartTime,
            sourceEndTime = sourceEndTime,
            sourceTimezone = sourceTimezone,
            utcStartEpochMs = utcStart,
            utcEndEpochMs = utcEnd,
            groupName = groupName,
            attendanceMode = attendanceMode,
            platform = platform,
            teamsChannel = teamsChannel,
            locationOrUrl = locationOrUrl,
            status = ClassEntity.STATUS_SCHEDULED,
            notes = notes
        )

        classDao.insertClass(entity)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "CREATED",
                subject = subject,
                details = "Scheduled $classType for $sourceDate $sourceStartTime ($sourceTimezone)"
            )
        )

        announcementDao.insertAnnouncement(
            AnnouncementEntity(
                id = UUID.randomUUID().toString(),
                title = "New Class Scheduled: $subject",
                message = "$classType has been scheduled on $sourceDate at $sourceStartTime ($sourceTimezone) for $groupName.",
                priority = "INFO",
                targetGroup = groupName
            )
        )

        return entity
    }

    suspend fun updateClass(classEntity: ClassEntity) {
        val utcStart = TimezoneHelper.toUtcEpochMs(
            classEntity.sourceDate,
            classEntity.sourceStartTime,
            classEntity.sourceTimezone
        )
        val utcEnd = TimezoneHelper.toUtcEpochMs(
            classEntity.sourceDate,
            classEntity.sourceEndTime,
            classEntity.sourceTimezone
        )

        val updated = classEntity.copy(
            utcStartEpochMs = utcStart,
            utcEndEpochMs = utcEnd,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        classDao.updateClass(updated)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "UPDATED",
                subject = classEntity.subject,
                details = "Class details updated for ${classEntity.sourceDate}"
            )
        )
    }

    suspend fun rescheduleClass(
        classEntity: ClassEntity,
        newDate: String,
        newStartTime: String,
        newEndTime: String,
        reason: String
    ) {
        val utcStart = TimezoneHelper.toUtcEpochMs(newDate, newStartTime, classEntity.sourceTimezone)
        val utcEnd = TimezoneHelper.toUtcEpochMs(newDate, newEndTime, classEntity.sourceTimezone)

        val updated = classEntity.copy(
            sourceDate = newDate,
            sourceStartTime = newStartTime,
            sourceEndTime = newEndTime,
            utcStartEpochMs = utcStart,
            utcEndEpochMs = utcEnd,
            status = ClassEntity.STATUS_RESCHEDULED,
            notes = if (reason.isNotBlank()) "Rescheduled: $reason" else classEntity.notes,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        classDao.updateClass(updated)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "RESCHEDULED",
                subject = classEntity.subject,
                details = "Rescheduled from ${classEntity.sourceDate} ${classEntity.sourceStartTime} to $newDate $newStartTime. Reason: $reason"
            )
        )

        announcementDao.insertAnnouncement(
            AnnouncementEntity(
                id = UUID.randomUUID().toString(),
                title = "Schedule Update: ${classEntity.subject} Rescheduled",
                message = "The ${classEntity.classType} originally at ${classEntity.sourceStartTime} is moved to $newDate at $newStartTime ($reason).",
                priority = "URGENT",
                targetGroup = classEntity.groupName
            )
        )
    }

    suspend fun cancelClass(classEntity: ClassEntity, reason: String) {
        val updated = classEntity.copy(
            status = ClassEntity.STATUS_CANCELLED,
            notes = if (reason.isNotBlank()) "Cancelled: $reason" else classEntity.notes,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        classDao.updateClass(updated)
        reminderDao.deletePreferenceForClass(classEntity.id)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "CANCELLED",
                subject = classEntity.subject,
                details = "Class on ${classEntity.sourceDate} cancelled. Reason: $reason"
            )
        )

        announcementDao.insertAnnouncement(
            AnnouncementEntity(
                id = UUID.randomUUID().toString(),
                title = "Class Cancelled: ${classEntity.subject}",
                message = "The ${classEntity.classType} scheduled on ${classEntity.sourceDate} has been cancelled ($reason). Reminders removed.",
                priority = "URGENT",
                targetGroup = classEntity.groupName
            )
        )
    }

    suspend fun deleteClass(classEntity: ClassEntity) {
        classDao.deleteClass(classEntity)
        reminderDao.deletePreferenceForClass(classEntity.id)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "DELETED",
                subject = classEntity.subject,
                details = "Class removed from timetable"
            )
        )
    }

    suspend fun clearAll() {
        classDao.clearAllClasses()
        auditLogDao.clearLogs()
    }

    suspend fun saveReminderPreference(
        classId: String,
        leadMinutes: Int,
        isAlarmEnabled: Boolean,
        soundTone: String,
        vibrate: Boolean
    ) {
        reminderDao.savePreference(
            ReminderPreferenceEntity(
                classId = classId,
                leadMinutes = leadMinutes,
                isAlarmEnabled = isAlarmEnabled,
                soundTone = soundTone,
                vibrate = vibrate
            )
        )
    }

    suspend fun postAnnouncement(
        title: String,
        message: String,
        priority: String = "NORMAL",
        targetGroup: String = "All"
    ) {
        announcementDao.insertAnnouncement(
            AnnouncementEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                priority = priority,
                targetGroup = targetGroup
            )
        )
    }

    suspend fun markAnnouncementAsRead(id: String) {
        announcementDao.markAsRead(id)
    }

    suspend fun markAllAnnouncementsRead() {
        announcementDao.markAllAsRead()
    }

    /**
     * Seeds the authentic timetable provided by the user (Bhavya & Vinodha Medical/Science schedule,
     * Europe/Sarajevo local time: 23 Oct to 05 Nov 2026).
     */
    suspend fun seedOfficialUniversitySchedule() {
        val bosniaTz = TimezoneHelper.BOSNIA_TIMEZONE

        val items = listOf(
            Triple("2026-10-23", "09:30 AM", "11:00 AM") to ("Biology of Cell and Human Genetics" to Triple("Make up Lecture", "-", "In person + Online")),
            Triple("2026-10-26", "05:00 PM", "06:30 PM") to ("Anatomy" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-10-26", "06:30 PM", "08:00 PM") to ("Medical Physics" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-10-27", "06:00 PM", "07:30 PM") to ("Medicine and Society" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-10-28", "02:00 PM", "03:30 PM") to ("Histology and Embryology" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-10-29", "09:30 AM", "11:00 AM") to ("Biology of Cell and Human Genetics" to Triple("Make up practical", "Group 1", "In person + Online")),
            Triple("2026-10-29", "11:45 AM", "01:15 PM") to ("Anatomy" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-10-30", "08:00 AM", "09:30 AM") to ("Biology of Cell and Human Genetics" to Triple("Make up practical", "Group 2", "Online")),
            Triple("2026-10-30", "09:30 AM", "11:00 AM") to ("Biology of Cell and Human Genetics" to Triple("Make up practical", "Group 3", "Online")),
            Triple("2026-10-30", "10:30 AM", "12:00 PM") to ("Medicine and Society" to Triple("Practical", "Group 1", "In person + Online")),
            Triple("2026-11-02", "01:00 PM", "02:30 PM") to ("Medicine and Society" to Triple("Practical", "Group 2", "Online")),
            Triple("2026-11-02", "02:30 PM", "04:00 PM") to ("Biology of Cell and Human Genetics" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-11-02", "05:00 PM", "06:30 PM") to ("Anatomy" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-11-03", "01:00 PM", "02:30 PM") to ("Medicine and Society" to Triple("Practical", "Group 3", "Online")),
            Triple("2026-11-03", "06:00 PM", "07:30 PM") to ("Medicine and Society" to Triple("Lecture", "-", "In person + Online")),
            Triple("2026-11-04", "12:00 PM", "01:30 PM") to ("Medicine and Society" to Triple("Practical", "Group 4", "Online")),
            Triple("2026-11-05", "11:45 AM", "01:15 PM") to ("Anatomy" to Triple("Lecture", "-", "In person + Online"))
        )

        val classEntities = items.map { (timeInfo, meta) ->
            val (date, start, end) = timeInfo
            val (subject, details) = meta
            val (type, rawGroup, mode) = details
            val group = if (rawGroup == "-") "All" else rawGroup

            val utcStart = TimezoneHelper.toUtcEpochMs(date, start, bosniaTz)
            val utcEnd = TimezoneHelper.toUtcEpochMs(date, end, bosniaTz)

            val isOnlineOrHybrid = mode.contains("Online")
            val cleanSubject = subject.lowercase().replace(" ", "_")
            val channelName = if (type.contains("practical", ignoreCase = true)) "Practicals" else "Lectures"
            val teamsLink = if (isOnlineOrHybrid) {
                "https://teams.microsoft.com/l/channel/19%3Aclasstime_${cleanSubject}@thread.tacv2/$channelName"
            } else {
                "Lecture Hall 3B"
            }

            ClassEntity(
                id = UUID.randomUUID().toString(),
                subject = subject,
                classType = type,
                instructor = "Department Faculty",
                sourceDate = date,
                sourceStartTime = start,
                sourceEndTime = end,
                sourceTimezone = bosniaTz,
                utcStartEpochMs = utcStart,
                utcEndEpochMs = utcEnd,
                groupName = group,
                attendanceMode = mode,
                platform = if (isOnlineOrHybrid) ClassEntity.PLATFORM_TEAMS else ClassEntity.PLATFORM_CLASSROOM,
                teamsChannel = channelName,
                locationOrUrl = teamsLink,
                status = ClassEntity.STATUS_SCHEDULED,
                notes = "Timings in Bosnia & Herzegovina local time. Microsoft Teams channel: #$channelName"
            )
        }

        classDao.insertClasses(classEntities)

        auditLogDao.insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                action = "IMPORTED",
                subject = "University Schedule",
                details = "Loaded 17 official medical/science lectures and practicals (Oct-Nov 2026)"
            )
        )

        announcementDao.insertAnnouncement(
            AnnouncementEntity(
                id = UUID.randomUUID().toString(),
                title = "Official Timetable Published",
                message = "The university class schedule for Oct 23 - Nov 05, 2026 has been published. Check your local times!",
                priority = "NORMAL",
                targetGroup = "All"
            )
        )
    }
}
