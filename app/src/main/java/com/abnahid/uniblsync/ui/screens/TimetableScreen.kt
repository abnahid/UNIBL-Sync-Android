package com.abnahid.uniblsync.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.ui.TimetableTab
import com.abnahid.uniblsync.ui.theme.AmberWarning
import com.abnahid.uniblsync.ui.theme.EmeraldSuccess
import com.abnahid.uniblsync.ui.theme.RoseError
import com.abnahid.uniblsync.ui.theme.SkyInfo
import com.abnahid.uniblsync.util.IcsExporter
import com.abnahid.uniblsync.util.TimezoneHelper
import com.abnahid.uniblsync.util.DisplayFormatter
import java.time.LocalDate
import java.time.ZoneId
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
    onClearFilters: () -> Unit,
    onClassClick: (ClassEntity) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val groups = listOf("All", "Group 1", "Group 2", "Group 3", "Group 4")
    val modes = listOf("All", "Online", "In-Person", "In person + Online")
    val zone = remember(selectedTimezone) { runCatching { ZoneId.of(selectedTimezone) }.getOrNull() }
    val todayInSelectedTimezone = remember(selectedTimezone) {
        runCatching { LocalDate.now(java.time.ZoneId.of(selectedTimezone)).toString() }
            .getOrElse { LocalDate.now().toString() }
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

        // One primary scrolling content area: filters, date navigation and class cards scroll together.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 6.dp
            )
        ) {
            // Compact, tappable filter header stays tiny.
            item(key = "filter_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { filtersExpanded = !filtersExpanded }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("filter_header"),
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
                        if (selectedGroupFilter != "All" || selectedModeFilter != "All") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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
                        TextButton(
                            onClick = { IcsExporter.shareIcsFile(context, classes) },
                            modifier = Modifier.testTag("export_all_ics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .ICS", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Expanded filter panel lives inside the scrollable content.
            item(key = "filter_panel") {
                AnimatedVisibility(visible = filtersExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("filter_panel")
                    ) {
                        Text(
                            "Groups",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(groups) { g ->
                                FilterChip(
                                    selected = selectedGroupFilter == g,
                                    onClick = { onGroupFilterSelected(g) },
                                    label = { Text(g) },
                                    modifier = Modifier.testTag("group_chip_$g")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Delivery mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(modes) { m ->
                                FilterChip(
                                    selected = selectedModeFilter == m,
                                    onClick = { onModeFilterSelected(m) },
                                    label = { Text(m) },
                                    modifier = Modifier.testTag("mode_chip_$m")
                                )
                            }
                        }

                        if (selectedGroupFilter != "All" || selectedModeFilter != "All") {
                            TextButton(onClick = onClearFilters) {
                                Text("Clear filters", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                TimetableTab.DAILY -> {
                    // Date navigation: previous arrow strip then week strip.
                    item(key = "date_nav_header") {
                        val parsed = runCatching { LocalDate.parse(selectedDate) }
                            .getOrElse { LocalDate.now() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDateSelected(parsed.minusDays(1).toString()) },
                                    modifier = Modifier.testTag("date_previous")
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                                }
                                Text(
                                    text = parsed.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { onDateSelected(parsed.plusDays(1).toString()) },
                                modifier = Modifier.testTag("date_next")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                            }
                        }
                    }
                    item(key = "date_strip") {
                        val availableDates = remember(classes, selectedTimezone, todayInSelectedTimezone) {
                            val generated = classes.map {
                                TimezoneHelper.formatToIsoDate(it.utcStartEpochMs, selectedTimezone)
                            }
                            val window = (0..27L).map { day ->
                                LocalDate.parse(todayInSelectedTimezone).plusDays(day).toString()
                            }
                            (generated + window + todayInSelectedTimezone)
                                .filterNotBlank()
                                .distinct()
                                .sorted()
                        }
                        val dateListState = rememberLazyListState()
                        LaunchedEffect(selectedDate, availableDates) {
                            availableDates.indexOf(selectedDate).takeIf { it >= 0 }
                                ?.let { index -> dateListState.animateScrollToItem(index) }
                        }
                        LazyRow(
                            state = dateListState,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                        ) {
                            items(availableDates) { dateStr ->
                                DatePill(
                                    dateStr = dateStr,
                                    isSelected = dateStr == selectedDate,
                                    isToday = dateStr == todayInSelectedTimezone,
                                    onSelect = onDateSelected
                                )
                            }
                        }
                    }

                    val displayedClasses = classes
                        .filter { TimezoneHelper.formatToIsoDate(it.utcStartEpochMs, selectedTimezone) == selectedDate }
                        .sortedBy { it.utcStartEpochMs }

                    if (displayedClasses.isEmpty()) {
                        item(key = "empty_state") {
                            TimetableEmptyState(
                                message = "No classes on $selectedDate match the current filters.",
                                onRefresh = onRefresh
                            )
                        }
                    } else {
                        items(displayedClasses, key = { it.id }) { item ->
                            TimetableClassCard(
                                classEntity = item,
                                userTimezone = selectedTimezone,
                                currentTimeMs = currentTimeMs,
                                onClick = { onClassClick(item) }
                            )
                        }
                    }
                }

                TimetableTab.WEEKLY -> {
                    val weekStart = runCatching {
                        LocalDate.parse(selectedDate).let { it.minusDays(it.dayOfWeek.value - 1L) }
                    }.getOrElse { LocalDate.now() }
                    val weekEnd = weekStart.plusDays(6)

                    item(key = "week_nav_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onDateSelected(weekStart.minusDays(7).toString()) },
                                modifier = Modifier.testTag("week_previous")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week")
                            }
                            Text(
                                text = "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                                    weekEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onDateSelected(weekStart.plusDays(7).toString()) },
                                modifier = Modifier.testTag("week_next")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next week")
                            }
                        }
                    }

                    val weekClasses = classes.filter {
                        val date = runCatching {
                            LocalDate.parse(TimezoneHelper.formatToIsoDate(it.utcStartEpochMs, selectedTimezone))
                        }.getOrNull()
                        date != null && date >= weekStart && date <= weekEnd
                    }.sortedBy { it.utcStartEpochMs }

                    if (weekClasses.isEmpty()) {
                        item(key = "empty_state") {
                            TimetableEmptyState(
                                message = "No classes this week match the current filters.",
                                onRefresh = onRefresh
                            )
                        }
                    } else {
                        val byDay = weekClasses.groupBy {
                            TimezoneHelper.formatToIsoDate(it.utcStartEpochMs, selectedTimezone)
                        }
                        byDay.toSortedMap().forEach { (dayStr, dayClasses) ->
                            item(key = "day_header_$dayStr") {
                                Text(
                                    text = runCatching {
                                        LocalDate.parse(dayStr).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
                                    }.getOrDefault(dayStr),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(dayClasses, key = { it.id }) { item ->
                                TimetableClassCard(
                                    classEntity = item,
                                    userTimezone = selectedTimezone,
                                    currentTimeMs = currentTimeMs,
                                    compactDateHeader = true,
                                    onClick = { onClassClick(item) }
                                )
                            }
                        }
                    }
                }

                TimetableTab.AGENDA -> {
                    // Chronological upcoming classes, independent of the selected week.
                    val now = currentTimeMs
                    val upcoming = classes
                        .filter {
                            it.utcEndEpochMs >= now &&
                                it.status != ClassEntity.STATUS_CANCELLED
                        }
                        .sortedBy { it.utcStartEpochMs }

                    if (upcoming.isEmpty()) {
                        item(key = "empty_state") {
                            TimetableEmptyState(
                                message = "No upcoming classes. Sync to check for schedule updates.",
                                onRefresh = onRefresh
                            )
                        }
                    } else {
                        var lastDate: String? = null
                        upcoming.forEach { classItem ->
                            val dateStr = TimezoneHelper.formatToIsoDate(classItem.utcStartEpochMs, selectedTimezone)
                            if (dateStr != lastDate) {
                                item(key = "agenda_header_$dateStr") {
                                    Text(
                                        text = runCatching {
                                            LocalDate.parse(dateStr).format(
                                                DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
                                            )
                                        }.getOrDefault(dateStr),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                lastDate = dateStr
                            }
                            item(key = "agenda_${classItem.id}") {
                                TimetableClassCard(
                                    classEntity = classItem,
                                    userTimezone = selectedTimezone,
                                    currentTimeMs = now,
                                    compactDateHeader = true,
                                    onClick = { onClassClick(classItem) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun List<String>.filterNotBlank(): List<String> = filter { it.isNotBlank() }

@Composable
private fun DatePill(
    dateStr: String,
    isSelected: Boolean,
    isToday: Boolean,
    onSelect: (String) -> Unit
) {
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
            .clickable { onSelect(dateStr) }
            .padding(vertical = 2.dp)
            .testTag("date_pill_$dateStr")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isToday) "Today" else dayOfWeek.uppercase(),
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

@Composable
private fun TimetableEmptyState(message: String, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
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
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRefresh, modifier = Modifier.testTag("timetable_empty_refresh")) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Timetable")
            }
        }
    }
}

@Composable
fun TimetableClassCard(
    classEntity: ClassEntity,
    userTimezone: String,
    currentTimeMs: Long,
    onClick: () -> Unit,
    compactDateHeader: Boolean = false
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
                    val hasRealLink = classEntity.locationOrUrl.startsWith("http://") ||
                        classEntity.locationOrUrl.startsWith("https://")
                    val isTeams = hasRealLink && (
                        classEntity.platform.contains("Teams", ignoreCase = true) ||
                            classEntity.locationOrUrl.contains("teams.microsoft.com", ignoreCase = true)
                        )
                    val teamsPurple = androidx.compose.ui.graphics.Color(0xFF5B5FC7)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isTeams -> teamsPurple.copy(alpha = 0.15f)
                                    classEntity.attendanceMode.contains("Online", ignoreCase = true) ->
                                        EmeraldSuccess.copy(alpha = 0.15f)
                                    else -> SkyInfo.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when {
                                isTeams -> "Teams • Join link ready"
                                classEntity.attendanceMode.contains("Online", ignoreCase = true) -> "Online"
                                else -> classEntity.attendanceMode.ifBlank { "In person" }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTeams) teamsPurple else if (classEntity.attendanceMode.contains("Online", true)) EmeraldSuccess else SkyInfo
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = DisplayFormatter.group(classEntity.groupName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when {
                        isCancelled -> "CANCELLED"
                        isRescheduled -> "RESCHEDULED"
                        isOngoing -> "LIVE NOW"
                        isCompleted -> "COMPLETED"
                        else -> "SCHEDULED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCancelled -> RoseError
                        isRescheduled -> AmberWarning
                        isOngoing -> EmeraldSuccess
                        isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = classEntity.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${DisplayFormatter.label(classEntity.classType)} • $localDate",
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
                        text = if (compactDateHeader) "University Time" else "University Time (Bosnia)",
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
