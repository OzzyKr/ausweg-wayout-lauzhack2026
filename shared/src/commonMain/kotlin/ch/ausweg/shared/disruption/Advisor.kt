package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.AdviceReason
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.Trip
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Waiting wins over a detour that arrives at most this much earlier (waiting is less hassle). */
val DEFAULT_WAIT_MARGIN: Duration = 5.minutes

/**
 * Decides what to do about [trip] at [now] (the replay clock's `ClockState.now`, never the device clock).
 * [alternatives] are OJP trips for the same origin and destination, fetched by the caller. Pure.
 *
 * Rules, in order:
 * 1. `affects(trip)` not blocked -> [Advice.Ok] with its warnings and skipped stops.
 * 2. Unblocked alternatives (`affects(alt)` not blocked) that haven't left yet (`departure >= now`), earliest
 *    arrival first; replacement-bus trips count. Callers re-run this as the clock moves, with the trips of an
 *    older search, so trips that already left must not come back as the best detour.
 * 3. Every blocking disruption has an `expectedEnd`: waitArrival = `max(now, trip.departure, latest end)`
 *    + trip duration. Wait if no alternative, or `waitArrival <= best.arrival + waitMargin` -> [Advice.Wait]
 *    (WAIT_IS_FASTER), else [Advice.Detour] (DETOUR_IS_FASTER).
 * 4. Some end unknown: [Advice.Detour] (OPEN_ENDED) if there's an alternative.
 * 5. No alternative, but a blocking disruption has a `bus` -> [Advice.TakeBus] (BUS_AVAILABLE).
 * 6. Otherwise [Advice.Wait] with `until = null` (NO_ALTERNATIVE).
 *
 * A trip blocked only by cancelled legs (no blocking disruption) has no known end, so rule 3 never applies.
 */
fun advise(
    trip: Trip,
    disruptions: List<Disruption>,
    now: Instant,
    alternatives: List<Trip>,
    waitMargin: Duration = DEFAULT_WAIT_MARGIN,
): Advice {
    val impact = affects(trip, disruptions)
    if (!impact.isBlocked) return Advice.Ok(impact.warnings, impact.skippedStops)

    val usable =
        alternatives
            .filter { it.departure >= now && !affects(it, disruptions).isBlocked }
            .sortedBy { it.arrival }
    val best = usable.firstOrNull()
    val lastToEnd = lastToEnd(impact.blocking)

    return when {
        lastToEnd != null -> {
            val (disruption, end) = lastToEnd
            val waitArrival = maxOf(now, trip.departure, end) + (trip.arrival - trip.departure)
            if (best == null || waitArrival <= best.arrival + waitMargin) {
                Advice.Wait(end, disruption.expectedEndType, AdviceReason.WAIT_IS_FASTER, impact)
            } else {
                Advice.Detour(usable, AdviceReason.DETOUR_IS_FASTER, impact)
            }
        }
        best != null -> Advice.Detour(usable, AdviceReason.OPEN_ENDED, impact)
        else -> {
            val bus = impact.blocking.firstNotNullOfOrNull { it.bus }
            if (bus != null) {
                Advice.TakeBus(bus, AdviceReason.BUS_AVAILABLE, impact)
            } else {
                Advice.Wait(null, EndType.UNKNOWN, AdviceReason.NO_ALTERNATIVE, impact)
            }
        }
    }
}

/** The blocking disruption that ends last and its end, or null if there is none or any of them has no known end. */
private fun lastToEnd(blocking: List<Disruption>): Pair<Disruption, Instant>? {
    val ends = blocking.map { d -> d.expectedEnd?.let { d to it } ?: return null }
    return ends.maxByOrNull { it.second }
}
