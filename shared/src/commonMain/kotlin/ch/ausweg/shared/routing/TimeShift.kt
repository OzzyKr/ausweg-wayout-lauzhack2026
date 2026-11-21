package ch.ausweg.shared.routing

import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.StopCall
import ch.ausweg.shared.model.Trip
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val DAYS_IN_WEEK = 7

/*
 * OJP only plans on the current timetable, so historical replay asks OJP for the same weekday and local time
 * close to today, then moves the answer back onto the replayed date. Callers pass the zone (Europe/Zurich):
 * on the web, named zones need a timezone database, so nothing here looks one up by itself.
 */

/** The instant with [target]'s weekday and local time, on one of the 7 days starting at [today]'s date. */
fun sameWeekdayNear(
    target: Instant,
    today: Instant,
    zone: TimeZone,
): Instant {
    val wanted = target.toLocalDateTime(zone)
    val start = today.toLocalDateTime(zone).date
    val date =
        (0 until DAYS_IN_WEEK)
            .map { start.plus(it, DateTimeUnit.DAY) }
            .first { it.dayOfWeek == wanted.date.dayOfWeek }
    return LocalDateTime(date, wanted.time).toInstant(zone)
}

/** Calendar days from [from]'s local date to [to]'s local date (negative if [to] is earlier). */
fun daysBetween(
    from: Instant,
    to: Instant,
    zone: TimeZone,
): Int = from.toLocalDateTime(zone).date.daysUntil(to.toLocalDateTime(zone).date)

/**
 * This trip moved by [days] calendar days, keeping every local time (so a 16:30 departure stays 16:30 across a
 * DST change). Realtime data belongs to the day it was fetched, so estimated times, cancellations, skipped stops
 * and situation refs are dropped: the result is the timetable only.
 */
fun Trip.movedByDays(
    days: Int,
    zone: TimeZone,
): Trip {
    val move = { t: Instant -> t.movedByDays(days, zone) }
    return copy(
        departure = move(departure),
        arrival = move(arrival),
        legs = legs.map { it.movedBy(move) },
    )
}

private fun Instant.movedByDays(
    days: Int,
    zone: TimeZone,
): Instant {
    val local = toLocalDateTime(zone)
    return LocalDateTime(local.date.plus(days, DateTimeUnit.DAY), local.time).toInstant(zone)
}

private fun Leg.movedBy(move: (Instant) -> Instant): Leg =
    copy(
        from = from.movedBy(move),
        to = to.movedBy(move),
        intermediate = intermediate.map { it.movedBy(move) },
        cancelled = false,
        situationIds = emptyList(),
    )

private fun StopCall.movedBy(move: (Instant) -> Instant): StopCall =
    copy(
        arrival = arrival?.let { CallTime(move(it.timetabled)) },
        departure = departure?.let { CallTime(move(it.timetabled)) },
        notServiced = false,
    )
