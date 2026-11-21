package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.Disruption
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
 * 2. Unblocked alternatives (`affects(alt)` not blocked), earliest arrival first; replacement-bus trips count.
 * 3. Every blocking disruption has an `expectedEnd`: waitArrival = `max(now, trip.departure, latest end)`
 *    + trip duration. Wait if no alternative, or `waitArrival <= best.arrival + waitMargin` -> [Advice.Wait]
 *    (WAIT_IS_FASTER), else [Advice.Detour] (DETOUR_IS_FASTER).
 * 4. Some end unknown: [Advice.Detour] (OPEN_ENDED) if there's an alternative.
 * 5. No alternative, but a blocking disruption has a `bus` -> [Advice.TakeBus] (BUS_AVAILABLE).
 * 6. Otherwise [Advice.Wait] with `until = null` (NO_ALTERNATIVE).
 */
@Suppress("UnusedParameter") // stub: remove when implemented
fun advise(
    trip: Trip,
    disruptions: List<Disruption>,
    now: Instant,
    alternatives: List<Trip>,
    waitMargin: Duration = DEFAULT_WAIT_MARGIN,
): Advice = TODO("blue")
