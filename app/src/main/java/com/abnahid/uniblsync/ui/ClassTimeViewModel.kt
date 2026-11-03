package com.abnahid.uniblsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.AnnouncementEntity
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.data.remote.NetworkClientProvider
import com.abnahid.uniblsync.data.remote.PublicApiClient
import com.abnahid.uniblsync.data.repository.ClassRepository
import com.abnahid.uniblsync.data.schedule.FirstSemesterSeed
import com.abnahid.uniblsync.data.schedule.ScheduleOccurrenceGenerator
import com.abnahid.uniblsync.data.schedule.ScheduleMerger
import com.abnahid.uniblsync.data.sync.PublicDataSyncScheduler
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderGlobalStore
import com.abnahid.uniblsync.util.SoundPreviewPlayer
import com.abnahid.uniblsync.util.TimezoneHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

class ClassTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val publicApiClient = PublicApiClient(
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
        appContext = application
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

    // Sync State
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

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

    private val _selectedDate = MutableStateFlow(getTodayDateIso(_selectedTimezone.value))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Global Reminder Settings (persisted, not only Compose state)
    private val reminderGlobalStore = ReminderGlobalStore.getInstance(application)

    private val _globalReminderLeadMinutes =
        MutableStateFlow(ReminderGlobalStore.getInstance(application).leadMinutes)
    val globalReminderLeadMinutes: StateFlow<Int> = _globalReminderLeadMinutes.asStateFlow()

    private val _selectedSoundTone =
        MutableStateFlow(reminderGlobalStore.soundTone)
    val selectedSoundTone: StateFlow<String> = _selectedSoundTone.asStateFlow()

    private val _isAlarmEnabled = MutableStateFlow(reminderGlobalStore.isAlarmEnabled)
    val isAlarmEnabled: StateFlow<Boolean> = _isAlarmEnabled.asStateFlow()

    private val _isVibrateEnabled = MutableStateFlow(reminderGlobalStore.isVibrateEnabled)
    val isVibrateEnabled: StateFlow<Boolean> = _isVibrateEnabled.asStateFlow()

    private val _isSoundPlaying = MutableStateFlow(false)
    val isSoundPlaying: StateFlow<Boolean> = _isSoundPlaying.asStateFlow()

    private val lastSemesterSeedRangeDays: Int = 21
    private val nextSemesterSeedRangeDays: Int = 90

    // Repository Flows
    val allClasses: StateFlow<List<ClassEntity>> = repository.allClasses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Local schedule (recurring definitions + overrides) as generated occurrences. */
    private val scheduleOccurrences: StateFlow<List<ClassEntity>> = combine(
        database.recurringScheduleDao().getAllSchedules(),
        database.scheduleOverrideDao().getAllOverrides()
    ) { schedules, overrides ->
        val today = LocalDate.now()
        val effectiveStart = today.minusDays(lastSemesterSeedRangeDays.toLong())
        val effectiveEnd = today.plusDays(nextSemesterSeedRangeDays.toLong())
        withContext(Dispatchers.Default) {
            ScheduleOccurrenceGenerator.generate(schedules, overrides, effectiveStart, effectiveEnd)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The single timetable source shown everywhere: API-synced classes merged
     * with locally generated recurring occurrences, sorted chronologically.
     */
    val mergedClasses: StateFlow<List<ClassEntity>> = combine(
        repository.allClasses,
        scheduleOccurrences
    ) { synced, generated ->
        ScheduleMerger.merge(synced, generated)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAnnouncements: StateFlow<List<AnnouncementEntity>> = repository.allAnnouncements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPreferences: StateFlow<List<ReminderPreferenceEntity>> = repository.allPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSuccessfulSync = repository.lastSuccessfulSync

    // Filtered Classes
    val filteredClasses: StateFlow<List<ClassEntity>> = combine(
        mergedClasses,
        selectedGroupFilter,
        selectedSubjectFilter,
        selectedModeFilter
    ) { classes, group, subject, mode ->
        classes.filter { item ->
            val matchGroup = group == "All" || item.groupName.equals("All", ignoreCase = true) || item.groupName.equals(group, ignoreCase = true)
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

        // Seed the first-semester routine and re-arm saved reminders.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.initializeLocalData() }
        }

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

        // Perform immediate foreground API sync on launch
        refreshData(force = false)

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

    fun setTimezone(tzId: String) {
        _selectedTimezone.value = tzId
    }

    fun resetTimetableFilters() {
        _selectedGroupFilter.value = "All"
        _selectedSubjectFilter.value = "All"
        _selectedModeFilter.value = "All"
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
        reminderGlobalStore.leadMinutes = minutes
    }

    fun setSelectedSoundTone(tone: String) {
        _selectedSoundTone.value = tone
        reminderGlobalStore.soundTone = tone
    }

    fun toggleAlarm(enabled: Boolean) {
        _isAlarmEnabled.value = enabled
        reminderGlobalStore.isAlarmEnabled = enabled
    }

    fun toggleVibrate(enabled: Boolean) {
        _isVibrateEnabled.value = enabled
        reminderGlobalStore.isVibrateEnabled = enabled
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

    fun saveClassReminder(
        classId: String,
        leadMinutes: Int,
        isAlarmEnabled: Boolean,
        soundTone: String,
        vibrate: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
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

    private fun getTodayDateIso(timezoneId: String): String {
        return try {
            val zone = ZoneId.of(timezoneId)
            LocalDate.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (_: Exception) {
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
}
