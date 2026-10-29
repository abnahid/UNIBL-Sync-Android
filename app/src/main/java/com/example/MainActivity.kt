package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ClassEntity
import com.example.data.sync.PublicDataSyncScheduler
import com.example.ui.ClassTimeViewModel
import com.example.ui.components.ClassDetailSheet
import com.example.ui.components.TimezoneSelectorDialog
import com.example.ui.screens.AnnouncementsScreen
import com.example.ui.screens.ReminderSettingsScreen
import com.example.ui.screens.StudentDashboardScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.theme.ClassTimeTheme
import com.example.util.TimezoneHelper
import com.example.util.ReleaseUpdateChecker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClassTimeTheme {
                ClassTimeApp()
            }
        }

        // Room content renders first; WorkManager refreshes published API data when connected.
        PublicDataSyncScheduler.schedule(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { ReleaseUpdateChecker.notifyIfNewRelease(applicationContext) }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            ReleaseUpdateChecker.notifyIfNewRelease(applicationContext)
            PublicDataSyncScheduler.schedule(applicationContext)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTimeApp(viewModel: ClassTimeViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()

    // State from ViewModel
    val selectedTimezone by viewModel.selectedTimezone.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeEpochMs.collectAsStateWithLifecycle()

    val classes by viewModel.filteredClasses.collectAsStateWithLifecycle()
    val rawClasses by viewModel.allClasses.collectAsStateWithLifecycle()
    val nextClass by viewModel.nextUpcomingClass.collectAsStateWithLifecycle()
    val ongoingClass by viewModel.ongoingClass.collectAsStateWithLifecycle()
    val announcements by viewModel.allAnnouncements.collectAsStateWithLifecycle()
    val preferences by viewModel.allPreferences.collectAsStateWithLifecycle()

    val selectedTab by viewModel.selectedTimetableTab.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedGroupFilter by viewModel.selectedGroupFilter.collectAsStateWithLifecycle()
    val selectedModeFilter by viewModel.selectedModeFilter.collectAsStateWithLifecycle()

    val globalLeadMinutes by viewModel.globalReminderLeadMinutes.collectAsStateWithLifecycle()
    val selectedSoundTone by viewModel.selectedSoundTone.collectAsStateWithLifecycle()
    val isAlarmEnabled by viewModel.isAlarmEnabled.collectAsStateWithLifecycle()
    val isVibrateEnabled by viewModel.isVibrateEnabled.collectAsStateWithLifecycle()
    val isSoundPlaying by viewModel.isSoundPlaying.collectAsStateWithLifecycle()

    // Navigation and Dialog state
    var studentNavIndex by remember { mutableIntStateOf(0) } // 0: Home, 1: Timetable, 2: Reminders, 3: Notices
    var showTimezoneDialog by remember { mutableStateOf(false) }
    var selectedClassForDetail by remember { mutableStateOf<ClassEntity?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    run {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ClassTime",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // Timezone Selector Chip
                        TextButton(
                            onClick = { showTimezoneDialog = true },
                            modifier = Modifier.testTag("topbar_timezone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Timezone",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = TimezoneHelper.getTimezoneShortLabel(selectedTimezone),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                val unreadCount = announcements.count { !it.isRead }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("student_bottom_nav")
                ) {
                    NavigationBarItem(
                        selected = studentNavIndex == 0,
                        onClick = { studentNavIndex = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = studentNavIndex == 1,
                        onClick = { studentNavIndex = 1 },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Timetable") },
                        label = { Text("Timetable") },
                        modifier = Modifier.testTag("nav_timetable")
                    )
                    NavigationBarItem(
                        selected = studentNavIndex == 2,
                        onClick = { studentNavIndex = 2 },
                        icon = { Icon(Icons.Default.Alarm, contentDescription = "Alarms") },
                        label = { Text("Alarms") },
                        modifier = Modifier.testTag("nav_alarms")
                    )
                    NavigationBarItem(
                        selected = studentNavIndex == 3,
                        onClick = { studentNavIndex = 3 },
                        icon = {
                            if (unreadCount > 0) {
                                BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                    Icon(Icons.Default.Campaign, contentDescription = "Notices")
                                }
                            } else {
                                Icon(Icons.Default.Campaign, contentDescription = "Notices")
                            }
                        },
                        label = { Text("Notices") },
                        modifier = Modifier.testTag("nav_notices")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (studentNavIndex) {
                    0 -> StudentDashboardScreen(
                        classes = rawClasses,
                        nextClass = nextClass,
                        ongoingClass = ongoingClass,
                        announcements = announcements,
                        preferences = preferences,
                        selectedTimezone = selectedTimezone,
                        currentTimeMs = currentTimeMs,
                        onClassClick = { selectedClassForDetail = it },
                        onChangeTimezoneClick = { showTimezoneDialog = true }
                    )
                    1 -> TimetableScreen(
                        classes = classes,
                        selectedTimezone = selectedTimezone,
                        currentTimeMs = currentTimeMs,
                        selectedTab = selectedTab,
                        selectedDate = selectedDate,
                        selectedGroupFilter = selectedGroupFilter,
                        selectedModeFilter = selectedModeFilter,
                        onTabSelected = { viewModel.setTimetableTab(it) },
                        onDateSelected = { viewModel.setSelectedDate(it) },
                        onGroupFilterSelected = { viewModel.setGroupFilter(it) },
                        onModeFilterSelected = { viewModel.setModeFilter(it) },
                        onClassClick = { selectedClassForDetail = it }
                    )
                    2 -> ReminderSettingsScreen(
                        selectedTimezone = selectedTimezone,
                        globalLeadMinutes = globalLeadMinutes,
                        selectedTone = selectedSoundTone,
                        isAlarmEnabled = isAlarmEnabled,
                        isVibrateEnabled = isVibrateEnabled,
                        isSoundPlaying = isSoundPlaying,
                        onLeadMinutesChange = { viewModel.setGlobalReminderLead(it) },
                        onToneChange = { viewModel.setSelectedSoundTone(it) },
                        onAlarmToggle = { viewModel.toggleAlarm(it) },
                        onVibrateToggle = { viewModel.toggleVibrate(it) },
                        onPlayTone = { viewModel.playTonePreview(it) },
                        onStopTone = { viewModel.stopTonePreview() },
                        onSendTestReminder = { viewModel.sendTestReminder() },
                        onChangeTimezoneClick = { showTimezoneDialog = true }
                    )
                    3 -> AnnouncementsScreen(
                        announcements = announcements,
                        onMarkAllRead = { viewModel.markAllAnnouncementsRead() },
                        onMarkRead = { viewModel.markAnnouncementRead(it) }
                    )
                }
            }
        }
    }

    // Timezone Selector Dialog
    if (showTimezoneDialog) {
        TimezoneSelectorDialog(
            selectedTimezone = selectedTimezone,
            onTimezoneSelected = { viewModel.setTimezone(it) },
            onDismiss = { showTimezoneDialog = false }
        )
    }

    // Class Detail Bottom Sheet
    if (selectedClassForDetail != null) {
        val pref = preferences.find { it.classId == selectedClassForDetail!!.id }
        ClassDetailSheet(
            classEntity = selectedClassForDetail!!,
            userTimezone = selectedTimezone,
            currentTimeMs = currentTimeMs,
            preference = pref,
            sheetState = sheetState,
            onSaveReminder = { lead, isAlarm, tone, vibrate ->
                viewModel.saveClassReminder(selectedClassForDetail!!.id, lead, isAlarm, tone, vibrate)
            },
            onPlayTone = { viewModel.playTonePreview(it) },
            onDismiss = {
                coroutineScope.launch {
                    sheetState.hide()
                    selectedClassForDetail = null
                }
            }
        )
    }
}
