package ch.ausweg.shared.i18n

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Swiss local time (Europe/Zurich), the time every screen shows: the data, the timetable and the replay are Swiss,
 * so a laptop set to another zone still shows 16:19, as SBB published it.
 *
 * Done by hand with the EU rule (CET +1, CEST +2 from the last Sunday of March to the last Sunday of October,
 * switching at 01:00 UTC) because `TimeZone.of("Europe/Zurich")` needs the `@js-joda/timezone` npm package on wasm.
 */
object SwissTime {
    private val winter = 1.hours
    private val summer = 2.hours

    /** UTC offset in Zurich at [instant]. */
    fun offset(instant: Instant): Duration {
        val year = instant.toLocalDateTime(TimeZone.UTC).year
        val start = lastSunday(year, Month.MARCH).atStartOfDayIn(TimeZone.UTC) + 1.hours
        val end = lastSunday(year, Month.OCTOBER).atStartOfDayIn(TimeZone.UTC) + 1.hours
        return if (instant >= start && instant < end) summer else winter
    }

    /** Wall-clock date and time in Zurich. */
    fun local(instant: Instant): LocalDateTime = (instant + offset(instant)).toLocalDateTime(TimeZone.UTC)

    /** `09:12`. */
    fun hhmm(instant: Instant): String {
        val t = local(instant)
        return "${t.hour.pad()}:${t.minute.pad()}"
    }

    /** Hour and minute in Zurich (to start the «Leave at» picker). */
    fun hourMinute(instant: Instant): Pair<Int, Int> = local(instant).let { it.hour to it.minute }

    /**
     * The instant at [hour]:[minute] Zurich time on the Zurich day of [day] (the «Leave at» picker; [day] is the
     * backend clock, so in replay it's the replayed date).
     */
    fun at(
        day: Instant,
        hour: Int,
        minute: Int,
    ): Instant {
        val date = local(day).date
        val guess = LocalDateTime(date, LocalTime(hour, minute)).toInstant(TimeZone.UTC)
        // Offset at the result, not at [day]: they differ only on the two switch days.
        return guess - offset(guess - winter)
    }

    private fun lastSunday(
        year: Int,
        month: Month,
    ): LocalDate {
        var d = LocalDate(year, month, LAST_DAY)
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        return d
    }

    /** March and October both have 31 days. */
    private const val LAST_DAY = 31
}

private fun Int.pad(): String = toString().padStart(2, '0')
