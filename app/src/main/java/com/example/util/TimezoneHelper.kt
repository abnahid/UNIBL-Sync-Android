package com.example.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class TimezoneOption(
    val id: String,
    val displayName: String,
    val shortLabel: String
)

object TimezoneHelper {
    const val BOSNIA_TIMEZONE = "Europe/Sarajevo"
    const val SERBIA_TIMEZONE = "Europe/Belgrade"
    const val CROATIA_TIMEZONE = "Europe/Zagreb"
    const val BANGLADESH_TIMEZONE = "Asia/Dhaka"
    const val INDIA_TIMEZONE = "Asia/Kolkata"
    const val PAKISTAN_TIMEZONE = "Asia/Karachi"

    val POPULAR_TIMEZONES = listOf(
        TimezoneOption(BOSNIA_TIMEZONE, "Bosnia & Herzegovina (Europe/Sarajevo)", "Sarajevo"),
        TimezoneOption(SERBIA_TIMEZONE, "Serbia (Europe/Belgrade)", "Belgrade"),
        TimezoneOption(CROATIA_TIMEZONE, "Croatia (Europe/Zagreb)", "Zagreb"),
        TimezoneOption(BANGLADESH_TIMEZONE, "Bangladesh (Asia/Dhaka)", "Dhaka"),
        TimezoneOption(INDIA_TIMEZONE, "India (Asia/Kolkata / IST)", "IST"),
        TimezoneOption(PAKISTAN_TIMEZONE, "Pakistan (Asia/Karachi / PKT)", "Karachi"),
        TimezoneOption("Asia/Dubai", "United Arab Emirates (Asia/Dubai)", "Dubai"),
        TimezoneOption("Europe/London", "United Kingdom (Europe/London)", "London"),
        TimezoneOption("America/New_York", "United States (America/New_York / ET)", "New York"),
        TimezoneOption("UTC", "Coordinated Universal Time (UTC)", "UTC")
    )

    fun getDeviceDefaultTimezone(): String {
        return try {
            ZoneId.systemDefault().id
        } catch (_: Exception) {
            BANGLADESH_TIMEZONE
        }
    }

    /**
     * Parse date string (YYYY-MM-DD or DD MMM YYYY) and time string (e.g. 5:00 PM, 17:00, 9:30 AM)
     * in the source timezone into canonical UTC epoch milliseconds.
     */
    fun toUtcEpochMs(dateStr: String, timeStr: String, timezoneId: String): Long {
        val zoneId = try {
            ZoneId.of(timezoneId)
        } catch (_: Exception) {
            ZoneId.of(BOSNIA_TIMEZONE)
        }

        val localDate = parseDate(dateStr)
        val localTime = parseTime(timeStr)

        val zonedDateTime = ZonedDateTime.of(localDate, localTime, zoneId)
        return zonedDateTime.toInstant().toEpochMilli()
    }

    fun parseDate(dateStr: String): LocalDate {
        val clean = dateStr.trim()
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
        )

        for (formatter in formatters) {
            try {
                return LocalDate.parse(clean, formatter)
            } catch (_: DateTimeParseException) { }
        }

        return LocalDate.now()
    }

    fun parseTime(timeStr: String): LocalTime {
        val clean = timeStr.trim().uppercase(Locale.ENGLISH)
        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm")
        )

        for (formatter in formatters) {
            try {
                return LocalTime.parse(clean, formatter)
            } catch (_: DateTimeParseException) { }
        }

        return LocalTime.of(9, 0)
    }

    fun formatToLocalTime(utcEpochMs: Long, targetZoneId: String): String {
        return try {
            val zone = ZoneId.of(targetZoneId)
            val zdt = Instant.ofEpochMilli(utcEpochMs).atZone(zone)
            val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
            zdt.format(formatter)
        } catch (_: Exception) {
            "00:00"
        }
    }

    fun formatToLocalDate(utcEpochMs: Long, targetZoneId: String): String {
        return try {
            val zone = ZoneId.of(targetZoneId)
            val zdt = Instant.ofEpochMilli(utcEpochMs).atZone(zone)
            val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
            zdt.format(formatter)
        } catch (_: Exception) {
            ""
        }
    }

    fun formatToDayOnly(utcEpochMs: Long, targetZoneId: String): String {
        return try {
            val zone = ZoneId.of(targetZoneId)
            val zdt = Instant.ofEpochMilli(utcEpochMs).atZone(zone)
            val formatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
            zdt.format(formatter)
        } catch (_: Exception) {
            ""
        }
    }

    fun getTimezoneShortLabel(zoneId: String): String {
        val found = POPULAR_TIMEZONES.find { it.id == zoneId }
        if (found != null) return found.shortLabel
        return try {
            val zone = ZoneId.of(zoneId)
            zone.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        } catch (_: Exception) {
            zoneId
        }
    }

    fun getDualTimeDisplay(
        utcEpochMs: Long,
        sourceZoneId: String,
        targetZoneId: String
    ): Pair<String, String> {
        val sourceFormatted = formatToLocalTime(utcEpochMs, sourceZoneId)
        val sourceLabel = getTimezoneShortLabel(sourceZoneId)

        val targetFormatted = formatToLocalTime(utcEpochMs, targetZoneId)
        val targetLabel = getTimezoneShortLabel(targetZoneId)

        return Pair("$sourceFormatted ($sourceLabel)", "$targetFormatted ($targetLabel)")
    }
}
