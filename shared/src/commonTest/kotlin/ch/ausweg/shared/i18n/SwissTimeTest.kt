package ch.ausweg.shared.i18n

import ch.ausweg.shared.model.EndType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SwissTimeTest {
    private fun i(s: String) = Instant.parse(s)

    @Test
    fun summerAndWinterOffsets() {
        assertEquals(2.hours, SwissTime.offset(i("2026-09-25T14:00:00Z")))
        assertEquals(1.hours, SwissTime.offset(i("2026-01-15T12:00:00Z")))
        assertEquals("16:19", SwissTime.hhmm(i("2026-09-25T14:19:00Z")))
        assertEquals("13:00", SwissTime.hhmm(i("2026-12-01T12:00:00Z")))
    }

    @Test
    fun switchesAtOneUtcOnTheLastSundays() {
        // 2026: summer time from Sun 29 March, winter time from Sun 25 October, both at 01:00 UTC.
        assertEquals(1.hours, SwissTime.offset(i("2026-03-29T00:59:59Z")))
        assertEquals(2.hours, SwissTime.offset(i("2026-03-29T01:00:00Z")))
        assertEquals(2.hours, SwissTime.offset(i("2026-10-25T00:59:59Z")))
        assertEquals(1.hours, SwissTime.offset(i("2026-10-25T01:00:00Z")))
        assertEquals("03:00", SwissTime.hhmm(i("2026-03-29T01:00:00Z")))
    }

    @Test
    fun localDateCrossesMidnight() {
        assertEquals("2026-09-26", SwissTime.local(i("2026-09-25T22:30:00Z")).date.toString())
    }

    @Test
    fun atPicksTheTimeOnTheSwissDay() {
        val day = i("2026-09-25T22:30:00Z") // already 26 Sep in Zurich
        assertEquals(i("2026-09-26T06:15:00Z"), SwissTime.at(day, 8, 15))
        assertEquals(i("2026-12-01T07:00:00Z"), SwissTime.at(i("2026-12-01T12:00:00Z"), 8, 0))
    }

    private val s = Strings.of("en", null, EN)

    @Test
    fun durations() {
        assertEquals("1 h 19", formatDuration(79.minutes, s))
        assertEquals("2 h 05", formatDuration(125.minutes, s))
        assertEquals("45 min", formatDuration(45.minutes, s))
        assertEquals("0 min", formatDuration((-3).minutes, s))
    }

    @Test
    fun datesAndAges() {
        val now = i("2026-09-25T14:30:00Z")
        assertEquals("25 Sep 2026", formatDate(now, s))
        assertEquals("just now", formatAgo(now, now, s))
        assertEquals("11 min ago", formatAgo(now - 11.minutes, now, s))
        assertEquals("3 h ago", formatAgo(now - 3.hours, now, s))
        assertEquals("12 Sep 2026", formatAgo(i("2026-09-12T15:40:00Z"), now, s))
    }

    @Test
    fun ends() {
        val end = i("2026-09-25T16:00:00Z")
        assertEquals("until approx. 18:00", formatUntil(end, EndType.APPROX, s))
        assertEquals("at least until 18:00", formatUntil(end, EndType.AT_LEAST, s))
        assertEquals("until 18:00", formatUntil(end, null, s))
        assertEquals("end unknown", formatUntil(null, EndType.UNKNOWN, s))
    }

    private companion object {
        val EN =
            """
            {
              "time.duration_hm": "{h} h {min}", "time.duration_m": "{min} min",
              "time.date": "{day} {month} {year}", "time.month.9": "Sep",
              "time.just_now": "just now", "time.ago_min": "{n} min ago", "time.ago_h": "{n} h ago",
              "time.until_approx": "until approx. {time}", "time.until_at_least": "at least until {time}",
              "time.until": "until {time}", "time.end_unknown": "end unknown"
            }
            """.trimIndent()
    }
}
