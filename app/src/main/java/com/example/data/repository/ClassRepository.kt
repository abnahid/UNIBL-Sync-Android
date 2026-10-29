package com.example.data.repository

import com.example.data.local.AnnouncementDao
import com.example.data.local.AuditLogDao
import com.example.data.local.ClassDao
import com.example.data.local.ReminderDao
import com.example.data.local.AppDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.local.VisitorDao
import com.example.data.model.ReminderPreferenceEntity
import com.example.data.model.VisitorEntity
import com.example.data.model.SyncStateEntity
import com.example.data.remote.PublicApiClient
import com.example.data.remote.PublicDataSource
import com.example.util.TimezoneHelper
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ClassRepository(
    private val database: AppDatabase,
    private val classDao: ClassDao,
    private val reminderDao: ReminderDao,
    private val announcementDao: AnnouncementDao,
    private val auditLogDao: AuditLogDao,
    private val visitorDao: VisitorDao,
    private val apiClient: PublicDataSource = PublicApiClient()
) {
    val allClasses: Flow<List<ClassEntity>> = classDao.getAllClasses()
    val allPreferences: Flow<List<ReminderPreferenceEntity>> = reminderDao.getAllPreferences()
    val allAnnouncements: Flow<List<AnnouncementEntity>> = announcementDao.getAllAnnouncements()
    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()
    val allVisitors: Flow<List<VisitorEntity>> = visitorDao.getAllVisitors()
    val lastSuccessfulSync = database.syncStateDao().observe("public-data")

    /**
     * Downloads complete public resources before replacing their cache in one Room transaction.
     * A network or parsing failure leaves the prior offline cache unchanged.
     */
    suspend fun refreshPublicData() {
        val classes = apiClient.fetchClasses()
        val announcements = apiClient.fetchAnnouncements()
        val syncedAt = System.currentTimeMillis()

        database.withTransaction {
            val announcementsWithReadState = announcements.map { remote ->
                remote.copy(isRead = announcementDao.getAnnouncementById(remote.id)?.isRead == true)
            }
            classDao.clearAllClasses()
            classDao.insertClasses(classes)
            reminderDao.deletePreferencesForMissingClasses()
            announcementDao.clearAllAnnouncements()
            announcementDao.insertAnnouncements(announcementsWithReadState)
            database.syncStateDao().save(SyncStateEntity("public-data", syncedAt))
        }
    }

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

}
