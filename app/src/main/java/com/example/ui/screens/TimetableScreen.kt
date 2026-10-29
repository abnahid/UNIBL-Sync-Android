package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassEntity
import com.example.ui.TimetableTab
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SkyInfo
import com.example.util.IcsExporter
import com.example.util.TimezoneHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(
    classes: List<ClassEntity>,
    selectedTimezone: String,
    currentTimeMs: Long,
    selectedTab: TimetableTab,
    selectedDate: String,
    selectedGroupFilter: String,
    selectedModeFilter: String,
    onTabSelected: (TimetableTab) -> Unit,
    onDateSelected: (String) -> Unit,
    onGroupFilterSelected: (String) -> Unit,
    onModeFilterSelected: (String) -> Unit,
    onClassClick: (ClassEntity) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current

    val groups = listOf("All", "Group 1", "Group 2", "Group 3", "Group 4")
    val modes = listOf("All", "Online", "In-Person", "In person + Online")

    // Distinct Dates in schedule
    val availableDates = remember(classes) {
        val dates = classes.map { it.sourceDate }.distinct().sorted()
        if (dates.isEmpty()) listOf("2026-10-26") else dates
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("timetable_screen")
    ) {
        // Tab Row (Daily, Weekly, Agenda)
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == TimetableTab.DAILY,
                onClick = { onTabSelected(TimetableTab.DAILY) },
                text = { Text("Daily") },
                modifier = Modifier.testTag("tab_daily")
            )
            Tab(
                selected = selectedTab == TimetableTab.WEEKLY,
                onClick = { onTabSelected(TimetableTab.WEEKLY) },
                text = { Text("Weekly") },
                modifier = Modifier.testTag("tab_weekly")
            )
            Tab(
                selected = selectedTab == TimetableTab.AGENDA,
                onClick = { onTabSelected(TimetableTab.AGENDA) },
                text = { Text("Agenda") },
                modifier = Modifier.testTag("tab_agenda")
            )
        }

        // Filter Bar (Groups & Modes & ICS Export)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Filters & Group", style = MaterialTheme.typography.labelMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("timetable_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = {
                            IcsExporter.shareIcsFile(context, classes)
                        },
                        modifier = Modifier.testTag("export_all_ics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .ICS", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Filter Chips Flow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { g ->
                    FilterChip(
                        selected = selectedGroupFilter == g,
                        onClick = { onGroupFilterSelected(g) },
                        label = { Text(g) }
                    )
                }
                modes.forEach { m ->
                    FilterChip(
                        selected = selectedModeFilter == m,
                        onClick = { onModeFilterSelected(m) },
                        label = { Text(m) }
                    )
                }
            }
        }

        // Daily View Date Selector Strip
        if (selectedTab == TimetableTab.DAILY) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableDates) { dateStr ->
                    val isSelected = dateStr == selectedDate
                    val parsedDate = try {
                        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } catch (_: Exception) {
                        null
                    }
                    val dayOfWeek = parsedDate?.format(DateTimeFormatter.ofPattern("EEE")) ?: ""
                    val dayNum = parsedDate?.dayOfMonth?.toString() ?: dateStr

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { onDateSelected(dateStr) }
                            .padding(vertical = 4.dp)
                            .testTag("date_pill_$dateStr")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dayOfWeek.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dayNum,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Classes List
        val displayedClasses = when (selectedTab) {
            TimetableTab.DAILY -> classes.filter { it.sourceDate == selectedDate }
            TimetableTab.WEEKLY, TimetableTab.AGENDA -> classes
        }

        if (displayedClasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Classes Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No classes match the selected filter or date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRefresh
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh Timetable")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(displayedClasses, key = { it.id }) { item ->
                    TimetableClassCard(
                        classEntity = item,
                        userTimezone = selectedTimezone,
                        currentTimeMs = currentTimeMs,
                        onClick = { onClassClick(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun TimetableClassCard(
    classEntity: ClassEntity,
    userTimezone: String,
    currentTimeMs: Long,
    onClick: () -> Unit
) {
    val localStart = TimezoneHelper.formatToLocalTime(classEntity.utcStartEpochMs, userTimezone)
    val localEnd = TimezoneHelper.formatToLocalTime(classEntity.utcEndEpochMs, userTimezone)
    val localDate = TimezoneHelper.formatToLocalDate(classEntity.utcStartEpochMs, userTimezone)
    val shortTz = TimezoneHelper.getTimezoneShortLabel(userTimezone)

    val isOngoing = classEntity.utcStartEpochMs <= currentTimeMs && classEntity.utcEndEpochMs >= currentTimeMs
    val isCompleted = classEntity.utcEndEpochMs < currentTimeMs
    val isCancelled = classEntity.status == ClassEntity.STATUS_CANCELLED
    val isRescheduled = classEntity.status == ClassEntity.STATUS_RESCHEDULED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
            .testTag("class_card_${classEntity.id.take(6)}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCancelled -> RoseError.copy(alpha = 0.08f)
                isOngoing -> EmeraldSuccess.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isTeams = classEntity.platform == ClassEntity.PLATFORM_TEAMS || classEntity.locationOrUrl.contains("teams.microsoft.com")
                    val teamsPurple = androidx.compose.ui.graphics.Color(0xFF5B5FC7)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isTeams -> teamsPurple.copy(alpha = 0.15f)
                                    classEntity.attendanceMode.contains("Online") -> EmeraldSuccess.copy(alpha = 0.15f)
                                    else -> SkyInfo.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isTeams) "Teams • #${classEntity.teamsChannel}" else classEntity.attendanceMode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTeams) teamsPurple else if (classEntity.attendanceMode.contains("Online")) EmeraldSuccess else SkyInfo
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Group: ${classEntity.groupName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                when {
                    isCancelled -> {
                        Text(
                            text = "CANCELLED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoseError
                        )
                    }
                    isRescheduled -> {
                        Text(
                            text = "RESCHEDULED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                    isOngoing -> {
                        Text(
                            text = "LIVE NOW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }
                    isCompleted -> {
                        Text(
                            text = "COMPLETED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = "SCHEDULED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = classEntity.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${classEntity.classType} • $localDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Timezone Split Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Local Time ($shortTz)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$localStart - $localEnd",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "University Time (Bosnia)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${classEntity.sourceStartTime} - ${classEntity.sourceEndTime}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
