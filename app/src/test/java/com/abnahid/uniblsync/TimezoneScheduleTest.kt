package com.abnahid.uniblsync

import com.abnahid.uniblsync.util.TimezoneHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Timezone tests: Europe/Sarajevo <-> Asia/Dhaka, winter vs summer offsets,
 * DST transitions and reminder trigger conversion.
 */
class TimezoneScheduleTest {

    private val sarajevo = ZoneId.of("Europe/Sarajevo")
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun instant(date: String, time: String, zone: ZoneId): Instant {
        val localTime = time.split(":").let { LocalTime.of(it[0].toInt(), it[1].toInt()) }
        return ZonedDateTime.of(LocalDate.parse(date), localTime, zone).toInstant()
    }

    @Test
    fun `sarajevo winter time is UTC plus one`() {
        // 2026-01-12 is inside CET (no DST).
        val epoch = TimezoneHelper.toUtcEpochMs("2026-01-12", "09:00", "Europe/Sarajevo")
        val expected = instant("2026-01-12", "09:00", sarajevo).toEpochMilli()
        assertEquals(expected, epoch)
        // 09:00 CET == 08:00 UTC
        assertEquals(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epoch)), "2026-01-12T08:00:00Z")
    }

    @Test
    fun `sarajevo summer time is UTC plus two`() {
        // 2026-07-13 is inside CEST (Bosnia DST, UTC+2).
        val epoch = TimezoneHelper.toUtcEpochMs("2026-07-13", "09:00", "Europe/Sarajevo")
        assertEquals(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epoch)), "2026-07-13T07:00:00Z")
    }

    @Test
    fun `anatomy thursday lecture lands at the same local time all year in sarajevo`() {
        // University schedule: Thursdays 11:45-13:15 Europe time.
        val winter = instant("2026-01-15", "11:45", sarajevo)
        val summer = instant("2026-07-16", "11:45", sarajevo)
        // Jan 15 -> Jul 16 is 182 days. Identical wall-clock times across a DST
        // boundary must differ by exactly (182 days - 1 DST hour) in UTC instants.
        val elapsed = java.time.Duration.between(winter, summer)
        assertEquals(
            java.time.Duration.ofDays(182).minusHours(1).toMillis(),
            elapsed.toMillis()
        )
    }

    @Test
    fun `sarajevo to dhaka is five hours in winter`() {        // 09:00 CET (UTC+1) == 14:00 Dhaka (UTC+6)
        val epoch = TimezoneHelper.toUtcEpochMs("2026-01-12", "09:00", TimezoneHelper.BOSNIA_TIMEZONE)
        val dhakaTime = Instant.ofEpochMilli(epoch).atZone(dhaka)
        assertEquals(LocalTime.of(14, 0), dhakaTime.toLocalTime())
        assertEquals(LocalDate.parse("2026-01-12"), dhakaTime.toLocalDate())
    }

    @Test
    fun `sarajevo to dhaka is four hours in summer`() {
        // 09:00 CEST (UTC+2) == 13:00 Dhaka (UTC+6)
        val epoch = TimezoneHelper.toUtcEpochMs("2026-07-13", "09:00", TimezoneHelper.BOSNIA_TIMEZONE)
        val dhakaTime = Instant.ofEpochMilli(epoch).atZone(dhaka)
        assertEquals(LocalTime.of(13, 0), dhakaTime.toLocalTime())
    }

    @Test
    fun `amphitheatre lecture at 1800 bosnia dst reads 2200 in dhaka`() {
        // Real schedule entry: "Medicine and Society, 18.00-19.30 (theoretical)" on Tuesday
        // 6 Oct 2026; Europe DST is still active until 25 Oct so the offset is +4h.
        val epoch = TimezoneHelper.toUtcEpochMs("2026-10-06", "18:00", TimezoneHelper.BOSNIA_TIMEZONE)
        val dhakaTime = Instant.ofEpochMilli(epoch).atZone(dhaka)
        assertEquals(LocalTime.of(22, 0), dhakaTime.toLocalTime())
    }

    @Test
    fun `dhaka does not observe daylight saving`() {
        val winter = instant("2026-01-12", "09:00", dhaka)
        val summer = instant("2026-07-13", "09:00", dhaka)
        // Jan 12 -> Jul 13 is exactly 182 days; Asia/Dhaka has a fixed UTC+6 offset.
        val elapsed = java.time.Duration.between(winter, summer)
        assertEquals(java.time.Duration.ofDays(182).toMillis(), elapsed.toMillis())
    }

    @Test
    fun `dst transition dates round trip without double conversion`() {
        // DST start 2026-03-29 03:00 and end 2026-10-25 03:00 in Europe/Sarajevo.
        val afterSpringTransition = TimezoneHelper.toUtcEpochMs("2026-03-29", "09:00", TimezoneHelper.BOSNIA_TIMEZONE)
        assertEquals("2026-03-29T07:00:00Z", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(afterSpringTransition)))

        val afterAutumnTransition = TimezoneHelper.toUtcEpochMs("2026-11-02", "11:45", TimezoneHelper.BOSNIA_TIMEZONE)
        assertEquals("2026-11-02T10:45:00Z", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(afterAutumnTransition)))
    }

    @Test
    fun `reminder trigger conversion subtracts lead minutes from the real instant`() {
        // Thursday Anatomy lecture 11:45-13:15 (11:45 CEST = 09:45 UTC), user asked for 15 min.
        val startEpoch = TimezoneHelper.toUtcEpochMs("2026-10-08", "11:45", TimezoneHelper.BOSNIA_TIMEZONE)
        val triggerEpoch = startEpoch - 15L * 60_000
        val trigger = Instant.ofEpochMilli(triggerEpoch)

        assertEquals("2026-10-08T09:30:00Z", DateTimeFormatter.ISO_INSTANT.format(trigger))
        // Displayed in Dhaka: class starts 15:45 local, reminder fires 15:30 local.
        assertEquals(LocalTime.of(15, 30), trigger.atZone(dhaka).toLocalTime())
    }

    @Test
    fun `pm am style times parse the same as 24h times`() {
        assertEquals(
            TimezoneHelper.toUtcEpochMs("2026-10-05", "05:00 PM", TimezoneHelper.BOSNIA_TIMEZONE),
            TimezoneHelper.toUtcEpochMs("2026-10-05", "17:00", TimezoneHelper.BOSNIA_TIMEZONE)
        )
    }

    @Test
    fun `iso date round trip in both timezones for a class crossing midnight`() {
        // Bosnia 18.30-20.00 class: 2026-10-26 18:30 CET == 2026-10-26 23:30 Dhaka (same date).
        val epoch = TimezoneHelper.toUtcEpochMs("2026-10-26", "18:30", TimezoneHelper.BOSNIA_TIMEZONE)
        val isoBosnia = TimezoneHelper.formatToIsoDate(epoch, TimezoneHelper.BOSNIA_TIMEZONE)
        val isoDhaka = TimezoneHelper.formatToIsoDate(epoch, TimezoneHelper.BANGLADESH_TIMEZONE)
        assertEquals("2026-10-26", isoBosnia)
        assertEquals("2026-10-26", isoDhaka)

        // Late 22:00 Bosnia class lands next day in Dhaka.
        val lateEpoch = TimezoneHelper.toUtcEpochMs("2026-01-12", "20:00", TimezoneHelper.BOSNIA_TIMEZONE)
        assertEquals("2026-01-13", TimezoneHelper.formatToIsoDate(lateEpoch, TimezoneHelper.BANGLADESH_TIMEZONE))
    }
}
