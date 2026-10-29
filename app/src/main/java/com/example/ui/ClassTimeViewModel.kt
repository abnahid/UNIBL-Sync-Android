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
import com.example.data.remote.AdminApiClient
import com.example.data.remote.AuthApiClient
import com.example.data.remote.NetworkClientProvider
import com.example.data.remote.PublicApiClient
import com.example.data.remote.SessionManager
import com.example.data.remote.UserSession
import com.example.data.repository.ClassRepository
import com.example.data.sync.PublicDataSyncScheduler
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class UserRole {
    STUDENT,
    ADMIN
}

enum class TimetableTab {
    DAILY,
    WEEKLY,
    AGENDA
}

data class SyncUiState(
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncedEpochMs: Long? = null
)

data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val user: UserSession? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ClassTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val sessionManager = SessionManager.getInstance(application)
    private val publicApiClient = PublicApiClient(
        client = NetworkClientProvider.getClient(application)
    )
    private val adminApiClient = AdminApiClient(
        context = application,
        client = NetworkClientProvider.getClient(application)
    )
    private val authApiClient = AuthApiClient(
        context = application,
        client = NetworkClientProvider.getClient(application)
    )

    private val repository = ClassRepository(
        database = database,
        classDao = database.classDao(),
        reminderDao = database.reminderDao(),
        announcementDao = database.announcementDao(),
        auditLogDao = database.auditLogDao(),
        visitorDao = database.visitorDao(),
        apiClient = publicApiClient,
        adminApiClient = adminApiClient,
        sessionManager = sessionManager
    )

    // Current Time Ticker (updates every second)
    private val _currentTimeEpochMs = MutableStateFlow(System.currentTimeMillis())
    val currentTimeEpochMs: StateFlow<Long> = _currentTimeEpochMs.asStateFlow()

    // Student Selected Timezone
    private val _selectedTimezone = MutableStateFlow(
        try {
            TimezoneHelper.BANGLADESH_TIMEZONE
        } catch (_: Exception) {
            TimezoneHelper.BOSNIA_TIMEZONE
        }
    )
    val selectedTimezone: StateFlow<String> = _selectedTimezone.asStateFlow()

    // Navigation Role
    private val _currentRole = MutableStateFlow(UserRole.STUDENT)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    // Sync State
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    // Auth State
    private val _authState = MutableStateFlow(
        AuthUiState(
            isAuthenticated = sessionManager.isLoggedIn(),
            user = sessionManager.loadSession()
        )
    )
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

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

    private val _selectedDate = MutableStateFlow(getTodayDateIso())
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

    val lastSuccessfulSync = repository.lastSuccessfulSync

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
        // Start 1-second ticker for live student clock
        viewModelScope.launch {
            while (isActive) {
                _currentTimeEpochMs.value = System.currentTimeMillis()
                delay(1000)
            }
        }

        // Initialize notification channels
        NotificationHelper.createNotificationChannels(application)

        // Observe Room sync timestamp
        viewModelScope.launch {
            repository.lastSuccessfulSync.collect { syncEntity ->
                if (syncEntity != null) {
                    _syncState.value = _syncState.value.copy(
                        lastSyncedEpochMs = syncEntity.lastSuccessfulSyncEpochMs
                    )
                }
            }
        }

        // Dynamically adjust selected date when classes are loaded
        viewModelScope.launch {
            allClasses.collect { classes ->
                if (classes.isNotEmpty()) {
                    val availableDates = classes.map { it.sourceDate }.distinct().sorted()
                    val today = getTodayDateIso()
                    if (!_selectedDate.value.contains(today) && availableDates.contains(today)) {
                        _selectedDate.value = today
                    } else if (!availableDates.contains(_selectedDate.value)) {
                        _selectedDate.value = availableDates.first()
                    }
                }
            }
        }

        // Perform immediate foreground API sync on launch
        refreshData(force = false)

        // Check backend session status
        checkSession()
    }

    /**
     * Performs a foreground network sync against the official public endpoints.
     * Transactionally updates Room without wiping cache on network failure.
     */
    fun refreshData(force: Boolean = true) {
        viewModelScope.launch {
            _syncState.value = _syncState.value.copy(isSyncing = true, errorMessage = null)
            try {
                repository.refreshPublicData()
                if (sessionManager.isLoggedIn()) {
                    try {
                        repository.refreshAdminData()
                    } catch (_: Exception) { }
                }
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    errorMessage = null,
                    lastSyncedEpochMs = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Failed to synchronize timetable"
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    errorMessage = errorMsg
                )
            }
        }
    }

    /**
     * Authenticates an administrator or faculty member using Better Auth / API v1.
     */
    fun loginStaff(
        email: String,
        passcode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
            val result = authApiClient.login(email, passcode)
            result.onSuccess { user ->
                _authState.value = AuthUiState(
                    isAuthenticated = true,
                    user = user,
                    isLoading = false,
                    errorMessage = null
                )
                _currentRole.value = UserRole.ADMIN
                refreshData(force = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Invalid email or password"
                _authState.value = _authState.value.copy(isLoading = false, errorMessage = msg)
                onError(msg)
            }
        }
    }

    fun logoutStaff() {
        viewModelScope.launch {
            authApiClient.logout()
            _authState.value = AuthUiState(isAuthenticated = false, user = null)
            _currentRole.value = UserRole.STUDENT
        }
    }

    fun switchRole(role: UserRole) {
        _currentRole.value = role
    }

    private fun checkSession() {
        viewModelScope.launch {
            val result = authApiClient.checkSession()
            result.onSuccess { user ->
                if (user != null) {
                    _authState.value = AuthUiState(isAuthenticated = true, user = user)
                } else {
                    _authState.value = AuthUiState(isAuthenticated = false, user = null)
                    if (_currentRole.value == UserRole.ADMIN) {
                        _currentRole.value = UserRole.STUDENT
                    }
                }
            }
        }
    }

    fun setTimezone(tzId: String) {
        _selectedTimezone.value = tzId
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

    // Admin & Staff Mutations
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
        notes: String = "",
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
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
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun updateClass(classEntity: ClassEntity, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                repository.updateClass(classEntity)
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun rescheduleClass(
        classEntity: ClassEntity,
        newDate: String,
        newStartTime: String,
        newEndTime: String,
        reason: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                repository.rescheduleClass(
                    classEntity = classEntity,
                    newDate = newDate,
                    newStartTime = newStartTime,
                    newEndTime = newEndTime,
                    reason = reason
                )
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun cancelClass(
        classEntity: ClassEntity,
        reason: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                repository.cancelClass(classEntity, reason)
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun deleteClass(classEntity: ClassEntity, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                repository.deleteClass(classEntity)
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun postAnnouncement(
        title: String,
        message: String,
        priority: String = "NORMAL",
        targetGroup: String = "All",
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                repository.postAnnouncement(
                    title = title,
                    message = message,
                    priority = priority,
                    targetGroup = targetGroup
                )
                refreshData(force = true)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage)
            }
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

    override fun onCleared() {
        super.onCleared()
        SoundPreviewPlayer.stopTone()
    }

    private fun getTodayDateIso(): String {
        return try {
            val zone = ZoneId.of(TimezoneHelper.BOSNIA_TIMEZONE)
            LocalDate.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (_: Exception) {
            "2026-10-26"
        }
    }
}
