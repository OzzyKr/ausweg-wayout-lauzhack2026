package ch.ausweg.shared.routing

import ch.ausweg.shared.disruption.advise
import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.Trip
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the options screen puts in the «Our pick» card. */
sealed interface Pick {
    /** Take [trip]: the usual trip if it's fine, else the best detour. */
    data class Go(
        val trip: Trip,
    ) : Pick

    /** Wait for the line to reopen, then take [trip]. [until] is null when nobody knows. */
    data class Wait(
        val trip: Trip,
        val until: Instant?,
        val endType: EndType?,
    ) : Pick

    /** Take the replacement bus; [trip] is the blocked usual trip it replaces. */
    data class Bus(
        val bus: ReplacementBus,
        val trip: Trip,
    ) : Pick
}

/** The trip of any [Pick]. */
val Pick.trip: Trip
    get() =
        when (this) {
            is Pick.Go -> trip
            is Pick.Wait -> trip
            is Pick.Bus -> trip
        }

/** A trip in the «other connections» list. [blocked] ones are shown, but pushed down and marked. */
data class TripOption(
    val trip: Trip,
    val blocked: Boolean,
)

/**
 * The options screen in one value.
 *
 * @property usual the trip the user would take without the disruption: the earliest blocked trip, or the first
 *   trip when nothing is blocked. Null when there are no trips.
 * @property blockedBy disruptions that block [usual], for the red «your usual route is interrupted» banner.
 * @property warnings disruptions on the way that don't stop the picked trip.
 * @property others every trip but the pick's, in the backend's order (open ones first).
 */
data class OptionsView(
    val usual: Trip?,
    val advice: Advice?,
    val pick: Pick?,
    val blockedBy: List<Disruption>,
    val warnings: List<Disruption>,
    val others: List<TripOption>,
)

/**
 * Builds the [OptionsView] for `POST /plan`'s [trips] (best first, see `rankTrips`) under [disruptions] at [now]
 * (backend clock). Runs again on every `state` event, so the pick follows the disruption as it develops.
 */
fun planOptions(
    trips: List<Trip>,
    disruptions: List<Disruption>,
    now: Instant,
): OptionsView {
    val blocked = trips.filter { affects(it, disruptions).isBlocked }
    val usual = blocked.minByOrNull { it.departure } ?: trips.firstOrNull()
    if (usual == null) return OptionsView(null, null, null, emptyList(), emptyList(), emptyList())

    val advice = advise(usual, disruptions, now, trips.filter { it.id != usual.id })
    val pick =
        when (advice) {
            is Advice.Ok -> Pick.Go(usual)
            is Advice.Detour -> Pick.Go(advice.alternatives.first())
            is Advice.Wait ->
                Pick.Wait(
                    firstAfterReopening(trips, disruptions, advice.until) ?: usual,
                    advice.until,
                    advice.endType,
                )
            is Advice.TakeBus -> Pick.Bus(advice.bus, usual)
        }
    val usualImpact = affects(usual, disruptions)
    val pickImpact = if (pick.trip.id == usual.id) usualImpact else affects(pick.trip, disruptions)
    val blockedIds = blocked.map { it.id }.toSet()
    return OptionsView(
        usual = usual,
        advice = advice,
        pick = pick,
        blockedBy = usualImpact.blocking,
        warnings = pickImpact.warnings,
        others = trips.filter { it.id != pick.trip.id }.map { TripOption(it, it.id in blockedIds) },
    )
}

/**
 * The trip to take once the line reopens at [until]: the earliest-arriving one among [trips] that leaves at or after
 * it and isn't blocked. Null when the end is unknown or the search has no such trip (then the card shows the usual
 * trip, which can't run before [until]).
 */
private fun firstAfterReopening(
    trips: List<Trip>,
    disruptions: List<Disruption>,
    until: Instant?,
): Trip? =
    until?.let { end ->
        trips
            .filter { it.departure >= end && !affects(it, disruptions).isBlocked }
            .minByOrNull { it.arrival }
    }

/** Door-to-door time of the trip. */
val Trip.duration: Duration get() = arrival - departure

/** How far the clock may go back before a search and still keep its answer (heartbeat jitter, not a seek). */
private val SEEK_SLACK: Duration = 1.minutes

/**
 * True when options from a «leave now» search at [plannedAt] no longer fit the clock at [now], so a fresh `/plan`
 * is due: the picked trip has left (the replay or the day moved on, e.g. the disruption ended and the first trip of
 * the old answer is long gone), or the clock went back before the search (replay seek, new scenario).
 */
fun needsReplan(
    view: OptionsView,
    plannedAt: Instant,
    now: Instant,
): Boolean {
    val pick = view.pick ?: return false
    return pick.trip.departure < now || now < plannedAt - SEEK_SLACK
}
