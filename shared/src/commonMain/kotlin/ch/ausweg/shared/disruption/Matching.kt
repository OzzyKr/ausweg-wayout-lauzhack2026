package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.model.TripImpact
import kotlin.time.Instant

/**
 * What in [trip] is hit by [disruptions] (the list from `StateResponse`). Pure: no clock, no network.
 *
 * Rules:
 * - Only `ACTIVE` disruptions count. A leg departing at or after `expectedEnd` isn't hit.
 *   Leg times are `estimated ?: timetabled`.
 * - A leg's path is `from` + `intermediate` + `to`, compared by `StopRef.didok` (walk legs and stops
 *   without a didok are skipped).
 * - A leg is in a `Section(a, b)` if its path has a and b, in either order, or if the section knows its `line`
 *   and two consecutive stops of the path are both on that line, on either side of (or inside) the section: the
 *   train ran along the line through it without stopping (e.g. IC Lausanne → Fribourg past Puidoux – Vauderens).
 *   A stop that isn't on the line in between breaks this (the train left the line). If `sections` is empty,
 *   it's hit if its path has at least 2 of `affectedStops` (or the only one, if there is one).
 * - `LINE_CLOSED` blocks every non-walk leg in the section. `REPLACEMENT_BUS` blocks only `RAIL` legs:
 *   a `BUS` leg there is the replacement itself.
 * - `leg.cancelled`, or `notServiced` on the leg's `from` / `to`, puts the leg in `cancelledLegs`.
 *   `notServiced` on an intermediate stop only adds it to `skippedStops`.
 *
 * A disruption is listed once, under `blocking` if it blocks any leg, else under `warnings`.
 */
fun affects(
    trip: Trip,
    disruptions: List<Disruption>,
): TripImpact {
    val active = disruptions.filter { it.status == DisruptionStatus.ACTIVE }
    val blocking = mutableSetOf<String>()
    val hit = mutableSetOf<String>()
    trip.legs.filter { it.mode != LegMode.WALK }.forEach { leg ->
        val path = leg.path()
        val ordered = leg.orderedPath()
        val departure = leg.departureTime()
        active.filter { it.hitsLeg(path, ordered, departure) }.forEach { d ->
            hit += d.id
            if (d.blocks(leg)) blocking += d.id
        }
    }
    return TripImpact(
        blocking = active.filter { it.id in blocking },
        warnings = active.filter { it.id in hit && it.id !in blocking },
        cancelledLegs = trip.legs.indices.filter { trip.legs[it].isCancelled() },
        skippedStops =
            trip.legs
                .flatMap { leg -> leg.intermediate.filter { it.notServiced }.mapNotNull { it.stop.didok } }
                .distinct(),
    )
}

/**
 * True if this disruption stops a train ridden from [from] to [to] (DIDOK), by the stops alone: a route a traveller
 * shares has no intermediate stops or times. Matches like [affects] (sections, [Section.line], affected stops); only
 * blocking disruptions count. Buses aren't asked: a road bus isn't on the closed line.
 */
fun Disruption.stopsTrain(
    from: Int?,
    to: Int?,
): Boolean {
    val ordered = listOf(from, to)
    return hints.any { it in DisruptionHints.BLOCKING } && hitsLeg(ordered.filterNotNull().toSet(), ordered, null)
}

/** DIDOK numbers along the leg, boarding stop first. */
private fun Leg.path(): Set<Int> = orderedPath().filterNotNull().toSet()

/** DIDOK numbers along the leg in order, null for stops without one. */
private fun Leg.orderedPath(): List<Int?> = (listOf(from) + intermediate + to).map { it.stop.didok }

/** When the leg leaves its boarding stop, realtime if known; null if OJP gave no time. */
private fun Leg.departureTime(): Instant? = (from.departure ?: from.arrival)?.let { it.estimated ?: it.timetabled }

private fun Leg.isCancelled(): Boolean = cancelled || from.notServiced || to.notServiced

private fun Disruption.hitsLeg(
    path: Set<Int>,
    ordered: List<Int?>,
    departure: Instant?,
): Boolean {
    val end = expectedEnd
    if (end != null && departure != null && departure >= end) return false
    return if (sections.isNotEmpty()) {
        sections.any { (it.from in path && it.to in path) || it.passedBy(ordered) }
    } else {
        val inPath = affectedStops.count { it in path }
        inPath >= minOf(2, affectedStops.size) && inPath > 0
    }
}

/**
 * True if two consecutive stops of [ordered] are on [Section.line] and the stretch between them overlaps the
 * section (more than touching an end: a train to Puidoux isn't in Puidoux – Vauderens).
 */
private fun Section.passedBy(ordered: List<Int?>): Boolean {
    val a = line.indexOf(from)
    val b = line.indexOf(to)
    if (a < 0 || b < 0) return false
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    return ordered.zipWithNext().any { (x, y) ->
        val i = x?.let(line::indexOf) ?: -1
        val j = y?.let(line::indexOf) ?: -1
        i >= 0 && j >= 0 && maxOf(i, j) > lo && minOf(i, j) < hi
    }
}

private fun Disruption.blocks(leg: Leg): Boolean =
    DisruptionHints.LINE_CLOSED in hints ||
        (DisruptionHints.REPLACEMENT_BUS in hints && leg.mode == LegMode.RAIL)
