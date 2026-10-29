package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.model.ReminderPreferenceEntity
import com.example.data.model.VisitorEntity
import com.example.data.repository.ClassRepository
import com.example.data.sync.FirestoreSyncManager
import com.example.util.NotificationHelper
import com.example.util.SoundPreviewPlayer
import com.example.util.TimezoneHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class UserRole {
    STUDENT,
    ADMIN
}

enum class TimetableTab {
    DAILY,
    WEEKLY,
    AGENDA
}

class ClassTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ClassRepository(
        classDao = database.classDao(),
        reminderDao = database.reminderDao(),
        announcementDao = database.announcementDao(),
        auditLogDao = database.auditLogDao(),
        visitorDao = database.visitorDao()
    )

    // Current Time Ticker (updates every second)
    private val _currentTimeEpochMs = MutableStateFlow(System.currentTimeMillis())
    val currentTimeEpochMs: StateFlow<Long> = _currentTimeEpochMs.asStateFlow()

    // Student Selected Timezone
    private val _selectedTimezone = MutableStateFlow(
        try {
            // Default to Bangladesh Asia/Dhaka or auto-detected local
            TimezoneHelper.BANGLADESH_TIMEZONE
        } catch (_: Exception) {
            TimezoneHelper.BOSNIA_TIMEZONE
        }
    )
    val selectedTimezone: StateFlow<String> = _selectedTimezone.asStateFlow()

    // Role & Admin Authentication
    private val _currentRole = MutableStateFlow(UserRole.STUDENT)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    // Filters
    private val _selectedGroupFilter = MutableStateFlow("All")
    val selectedGroupFilter: StateFlow<String> = _selectedGroupFilter.asStateFlow()

    private val _selectedSubjectFilter = MutableStateFlow("All")
    val selectedSubjectFilter: StateFlow<String> = _selectedSubjectFilter.asStateFlow()

    private val _selectedModeFilter = MutableStateFlow("All")
    val selectedModeFilter: StateFlow<String> = _selectedModeFilter.asStateFlow()

    // Timetable Tab & Selected Date
    private val _selectedTimetableTab = MutableStateFlow(TimetableTab.DAILY)
    val selectedTimetableTab: StateFlow<TimetableTab> = _selectedTimetableTab.asStateFlow()

    private val _selectedDate = MutableStateFlow("2026-10-26")
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Global Reminder Settings
    private val _globalReminderLeadMinutes = MutableStateFlow(20)
    val globalReminderLeadMinutes: StateFlow<Int> = _globalReminderLeadMinutes.asStateFlow()

    private val _selectedSoundTone = MutableStateFlow("Gentle Chime")
    val selectedSoundTone: StateFlow<String> = _selectedSoundTone.asStateFlow()

    private val _isAlarmEnabled = MutableStateFlow(true)
    val isAlarmEnabled: StateFlow<Boolean> = _isAlarmEnabled.asStateFlow()

    private val _isVibrateEnabled = MutableStateFlow(true)
    val isVibrateEnabled: StateFlow<Boolean> = _isVibrateEnabled.asStateFlow()

    private val _isSoundPlaying = MutableStateFlow(false)
    val isSoundPlaying: StateFlow<Boolean> = _isSoundPlaying.asStateFlow()

    // Repository Flows
    val allClasses: StateFlow<List<ClassEntity>> = repository.allClasses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAnnouncements: StateFlow<List<AnnouncementEntity>> = repository.allAnnouncements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLogEntity>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPreferences: StateFlow<List<ReminderPreferenceEntity>> = repository.allPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVisitors: StateFlow<List<VisitorEntity>> = repository.allVisitors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Classes
    val filteredClasses: StateFlow<List<ClassEntity>> = combine(
        allClasses,
        selectedGroupFilter,
        selectedSubjectFilter,
        selectedModeFilter
    ) { classes, group, subject, mode ->
        classes.filter { item ->
            val matchGroup = group == "All" || item.groupName == "All" || item.groupName.equals(group, ignoreCase = true)
            val matchSubject = subject == "All" || item.subject.equals(subject, ignoreCase = true)
            val matchMode = mode == "All" || item.attendanceMode.contains(mode, ignoreCase = true)
            matchGroup && matchSubject && matchMode
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Next upcoming class & ongoing class
    val nextUpcomingClass: StateFlow<ClassEntity?> = combine(
        filteredClasses,
        _currentTimeEpochMs
    ) { classes, now ->
        classes
            .filter { it.utcStartEpochMs > now && it.status != ClassEntity.STATUS_CANCELLED }
            .minByOrNull { it.utcStartEpochMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ongoingClass: StateFlow<ClassEntity?> = combine(
        filteredClasses,
        _currentTimeEpochMs
    ) { classes, now ->
        classes.firstOrNull {
            it.utcStartEpochMs <= now && it.utcEndEpochMs >= now && it.status != ClassEntity.STATUS_CANCELLED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Start 1-second ticker for live clock and countdown
        viewModelScope.launch {
            while (isActive) {
                _currentTimeEpochMs.value = System.currentTimeMillis()
                delay(1000)
            }
        }

        // Initialize notification channels
        NotificationHelper.createNotificationChannels(application)
    }

    fun setTimezone(tzId: String) {
        _selectedTimezone.value = tzId
    }

    fun switchRole(role: UserRole) {
        _currentRole.value = role
    }

    fun loginAdmin(passcode: String): Boolean {
        // Admin password check: 'admin123'
        return if (passcode == "admin123" || passcode == "admin") {
            _isAdminAuthenticated.value = true
            _currentRole.value = UserRole.ADMIN
            true
        } else {
            false
        }
    }

    fun logoutAdmin() {
        _isAdminAuthenticated.value = false
        _currentRole.value = UserRole.STUDENT
    }

    fun setGroupFilter(group: String) {
        _selectedGroupFilter.value = group
    }

    fun setSubjectFilter(subject: String) {
        _selectedSubjectFilter.value = subject
    }

    fun setModeFilter(mode: String) {
        _selectedModeFilter.value = mode
    }

    fun setTimetableTab(tab: TimetableTab) {
        _selectedTimetableTab.value = tab
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun setGlobalReminderLead(minutes: Int) {
        _globalReminderLeadMinutes.value = minutes
    }

    fun setSelectedSoundTone(tone: String) {
        _selectedSoundTone.value = tone
    }

    fun toggleAlarm(enabled: Boolean) {
        _isAlarmEnabled.value = enabled
    }

    fun toggleVibrate(enabled: Boolean) {
        _isVibrateEnabled.value = enabled
    }

    fun playTonePreview(tone: String) {
        _isSoundPlaying.value = true
        SoundPreviewPlayer.playTone(tone, viewModelScope)
    }

    fun stopTonePreview() {
        _isSoundPlaying.value = false
        SoundPreviewPlayer.stopTone()
    }

    fun sendTestReminder() {
        NotificationHelper.sendClassReminderNotification(
            context = getApplication(),
            notificationId = 101,
            title = "Class Reminder: Anatomy",
            message = "Your Anatomy lecture starts in ${_globalReminderLeadMinutes.value} minutes (5:00 PM Bosnia time / 9:30 PM IST).",
            vibrate = _isVibrateEnabled.value
        )
        if (_isAlarmEnabled.value) {
            playTonePreview(_selectedSoundTone.value)
        }
    }

    // Repository mutation delegates
    fun createClass(
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
    ) {
        viewModelScope.launch {
            repository.createClass(
                subject = subject,
                classType = classType,
                instructor = instructor,
                sourceDate = sourceDate,
                sourceStartTime = sourceStartTime,
                sourceEndTime = sourceEndTime,
                sourceTimezone = sourceTimezone,
                groupName = groupName,
                attendanceMode = attendanceMode,
                platform = platform,
                teamsChannel = teamsChannel,
                locationOrUrl = locationOrUrl,
                notes = notes
            )
        }
    }

    fun updateClass(classEntity: ClassEntity) {
        viewModelScope.launch {
            repository.updateClass(classEntity)
        }
    }

    fun rescheduleClass(
        classEntity: ClassEntity,
        newDate: String,
        newStartTime: String,
        newEndTime: String,
        reason: String
    ) {
        viewModelScope.launch {
            repository.rescheduleClass(
                classEntity = classEntity,
                newDate = newDate,
                newStartTime = newStartTime,
                newEndTime = newEndTime,
                reason = reason
            )
        }
    }

    fun cancelClass(classEntity: ClassEntity, reason: String) {
        viewModelScope.launch {
            repository.cancelClass(classEntity, reason)
        }
    }

    fun deleteClass(classEntity: ClassEntity) {
        viewModelScope.launch {
            repository.deleteClass(classEntity)
        }
    }

    /**
     * Pulls the latest schedule + notices from the PUBLIC FIREBASE DATABASE
     * (managed from the web admin dashboard) and stores them in the local
     * Room database so the app works offline. Replaces the old hardcoded seed.
     */
    fun seedUniversitySchedule() {
        viewModelScope.launch {
            FirestoreSyncManager.sync(getApplication(), force = true)
        }
    }

    /** Live status of the public-database sync (Syncing / Success / Error). */
    val cloudSyncState: StateFlow<FirestoreSyncManager.SyncUiState> = FirestoreSyncManager.state

    fun clearAllClasses() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun saveClassReminder(
        classId: String,
        leadMinutes: Int,
        isAlarmEnabled: Boolean,
        soundTone: String,
        vibrate: Boolean
    ) {
        viewModelScope.launch {
            repository.saveReminderPreference(
                classId = classId,
                leadMinutes = leadMinutes,
                isAlarmEnabled = isAlarmEnabled,
                soundTone = soundTone,
                vibrate = vibrate
            )
        }
    }

    fun postAnnouncement(
        title: String,
        message: String,
        priority: String = "NORMAL",
        targetGroup: String = "All"
    ) {
        viewModelScope.launch {
            repository.postAnnouncement(
                title = title,
                message = message,
                priority = priority,
                targetGroup = targetGroup
            )
        }
    }

    fun markAnnouncementRead(id: String) {
        viewModelScope.launch {
            repository.markAnnouncementAsRead(id)
        }
    }

    fun markAllAnnouncementsRead() {
        viewModelScope.launch {
            repository.markAllAnnouncementsRead()
        }
    }

    fun registerVisitor(name: String, email: String, purpose: String, group: String = "All") {
        viewModelScope.launch {
            repository.registerVisitor(name, email, purpose, group)
        }
    }

    override fun onCleared() {
        super.onCleared()
        SoundPreviewPlayer.stopTone()
    }
}
