package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `POST /plan`. Stop refs are DIDOK numbers (`8501120`) or SLOIDs.
 *
 * @property departAt when to leave; null means the backend clock's `now` (simulated in historical mode).
 * @property via stops the trip must pass, if the user picked any.
 */
@Serializable
data class PlanRequest(
    val from: String,
    val to: String,
    val departAt: Instant? = null,
    val via: List<String> = emptyList(),
)

/** Where the trips of a [PlanResponse] came from. */
@Serializable
enum class TripSource {
    /** OJP, today, with realtime data. */
    LIVE,

    /** OJP for the same weekday and time today, moved onto the replayed date (historical mode). */
    SHIFTED,

    /** Recorded OJP answers, moved onto the requested date (OJP unreachable or no key). */
    FIXTURE,
}

/**
 * `POST /plan` answer. The app picks a trip and runs `advise(trip, disruptions, clock.now, otherTrips)`.
 *
 * @property trips best first: trips that get through the current disruptions, earliest arrival first, then
 *   blocked ones (still shown, pushed down).
 * @property disruptions the disruptions known at the backend clock's `now`, the same list as `StateResponse`.
 * @property situations OJP's own disruption notices for [trips] (live only; empty for shifted or recorded trips).
 * @property rerouted true if every direct trip was blocked and the backend searched again through other stops.
 */
@Serializable
data class PlanResponse(
    val trips: List<Trip>,
    val disruptions: List<Disruption>,
    val source: TripSource,
    val situations: List<Situation> = emptyList(),
    val rerouted: Boolean = false,
)
