package com.example

import com.example.data.model.ClassEntity
import com.example.util.IcsExporter
import com.example.util.TimezoneHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testTimezoneConversion_BosniaToIndiaAndBangladesh() {
        val date = "2026-10-26"
        val timeBosnia = "05:00 PM" // 17:00 in Europe/Sarajevo
        val bosniaTz = TimezoneHelper.BOSNIA_TIMEZONE

        val utcEpoch = TimezoneHelper.toUtcEpochMs(date, timeBosnia, bosniaTz)
        assertTrue("Epoch must be valid timestamp", utcEpoch > 0)

        // Format to IST (Asia/Kolkata)
        val istFormatted = TimezoneHelper.formatToLocalTime(utcEpoch, TimezoneHelper.INDIA_TIMEZONE)
        // Format to Bangladesh (Asia/Dhaka)
        val dhakaFormatted = TimezoneHelper.formatToLocalTime(utcEpoch, TimezoneHelper.BANGLADESH_TIMEZONE)

        // On 26 Oct 2026: Europe/Sarajevo is UTC+1 (Daylight saving ended on 25 Oct 2026 or is standard UTC+1).
        // 5:00 PM (17:00 UTC+1) = 16:00 UTC.
        // India (UTC+5:30) = 21:30 (09:30 PM).
        // Bangladesh (UTC+6) = 22:00 (10:00 PM).
        assertEquals("09:30 PM", istFormatted)
        assertEquals("10:00 PM", dhakaFormatted)
    }

    @Test
    fun testIcsExport_GeneratesValidVCalendar() {
        val sampleClass = ClassEntity(
            id = "test-123",
            subject = "Anatomy",
            classType = "Lecture",
            instructor = "Prof. Smith",
            sourceDate = "2026-10-26",
            sourceStartTime = "05:00 PM",
            sourceEndTime = "06:30 PM",
            sourceTimezone = TimezoneHelper.BOSNIA_TIMEZONE,
            utcStartEpochMs = 1793030400000L,
            utcEndEpochMs = 1793035800000L,
            groupName = "All",
            attendanceMode = "In person + Online",
            locationOrUrl = "https://meet.google.com/test-room",
            notes = "Bring textbook"
        )

        val ics = IcsExporter.generateIcsContent(listOf(sampleClass))
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:Anatomy (Lecture)"))
        assertTrue(ics.contains("LOCATION:https://meet.google.com/test-room"))
        assertTrue(ics.contains("END:VCALENDAR"))
    }

    @Test
    fun testTeamsChannelUrl_generation() {
        val url = com.example.util.MeetingLauncher.getTeamsChannelUrl("Anatomy", "Lectures")
        assertTrue(url.contains("teams.microsoft.com"))
        assertTrue(url.contains("anatomy"))
        assertTrue(url.contains("Lectures"))
    }
}
