package ch.ausweg.shared.routing

import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Trip

/** How many extra OJP searches a reroute may cost. */
const val DEFAULT_MAX_VIAS = 3

/**
 * Stops to search through when every direct trip is blocked: [hubs] (in their given order) that aren't a
 * station of a [blocking] disruption and aren't in [exclude] (the trip's own origin and destination).
 */
fun viaCandidates(
    blocking: List<Disruption>,
    hubs: List<Int>,
    exclude: Set<Int> = emptySet(),
    max: Int = DEFAULT_MAX_VIAS,
): List<Int> {
    val closed = blocking.flatMap { d -> d.affectedStops + d.sections.flatMap { listOf(it.from, it.to) } }.toSet()
    return hubs
        .distinct()
        .filter { it !in closed && it !in exclude }
        .take(max)
}

/**
 * Trips from several searches merged into one list, best first: trips that get through [disruptions]
 * (earliest arrival first), then blocked ones, pushed down but still listed. The same journey found by two
 * searches is kept once. Trip ids are made unique, since each OJP answer numbers its own trips.
 */
fun rankTrips(
    trips: List<Trip>,
    disruptions: List<Disruption>,
): List<Trip> {
    val (blocked, open) = trips.distinctBy { it.signature() }.partition { affects(it, disruptions).isBlocked }
    return (open.sortedBy { it.arrival } + blocked.sortedBy { it.arrival }).withUniqueIds()
}

/** Same vehicles between the same stops at the same timetabled times. */
private fun Trip.signature(): String =
    legs.joinToString(";") { leg ->
        val dep = leg.from.departure?.timetabled ?: leg.from.arrival?.timetabled
        "${leg.mode}|${leg.line}|${leg.from.stop.ref}|${leg.to.stop.ref}|$dep"
    }

private fun List<Trip>.withUniqueIds(): List<Trip> {
    val seen = mutableMapOf<String, Int>()
    return map { trip ->
        val n = seen.getOrElse(trip.id) { 0 }
        seen[trip.id] = n + 1
        if (n == 0) trip else trip.copy(id = "${trip.id}#$n")
    }
}
