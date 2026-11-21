package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
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
 * - A leg is in a `Section(a, b)` if its path has a and b, in either order. If `sections` is empty,
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
        val departure = leg.departureTime()
        active.filter { it.hitsLeg(path, departure) }.forEach { d ->
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

/** DIDOK numbers along the leg, boarding stop first. */
private fun Leg.path(): Set<Int> = (listOf(from) + intermediate + to).mapNotNull { it.stop.didok }.toSet()

/** When the leg leaves its boarding stop, realtime if known; null if OJP gave no time. */
private fun Leg.departureTime(): Instant? = (from.departure ?: from.arrival)?.let { it.estimated ?: it.timetabled }

private fun Leg.isCancelled(): Boolean = cancelled || from.notServiced || to.notServiced

private fun Disruption.hitsLeg(
    path: Set<Int>,
    departure: Instant?,
): Boolean {
    val end = expectedEnd
    if (end != null && departure != null && departure >= end) return false
    return if (sections.isNotEmpty()) {
        sections.any { it.from in path && it.to in path }
    } else {
        val inPath = affectedStops.count { it in path }
        inPath >= minOf(2, affectedStops.size) && inPath > 0
    }
}

private fun Disruption.blocks(leg: Leg): Boolean =
    DisruptionHints.LINE_CLOSED in hints ||
        (DisruptionHints.REPLACEMENT_BUS in hints && leg.mode == LegMode.RAIL)
