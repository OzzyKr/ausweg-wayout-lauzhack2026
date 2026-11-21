package ch.ausweg.server.ojp

import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.PlanRequest
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.ojp.TripQuery
import ch.ausweg.shared.ojp.didokOf
import ch.ausweg.shared.routing.rankTrips
import ch.ausweg.shared.routing.viaCandidates
import org.slf4j.LoggerFactory

private const val MAX_OFFLINE_STOPS = 10

/**
 * `POST /plan` and `GET /stops`.
 *
 * Planning: search OJP; if every trip is blocked by a current disruption (shared `affects()`), search again through
 * up to 3 hub stations that avoid the closed sections ([hubs], shared `viaCandidates()`), then rank everything with
 * shared `rankTrips()`. The app runs `advise()` on the result.
 *
 * @param state the backend state (clock + disruptions) at the moment of the request.
 * @param stations every station the scenario data knows, for stop search while OJP is unreachable.
 */
class PlanService(
    private val ojp: OjpService,
    private val hubs: List<Int>,
    private val state: () -> StateResponse,
    private val stations: () -> List<StopRef>,
) {
    private val log = LoggerFactory.getLogger(PlanService::class.java)

    /** @throws OjpUnavailable if the direct search has neither OJP nor a fixture. */
    suspend fun plan(request: PlanRequest): PlanResponse {
        val now = state()
        val historical = now.clock.mode == Mode.HISTORICAL
        val query = TripQuery(request.from, request.to, request.departAt ?: now.clock.now, request.via)
        val direct = ojp.trips(query, historical)

        val impacts = direct.trips.map { affects(it, now.disruptions) }
        var trips = direct.trips
        var rerouted = false
        if (impacts.isNotEmpty() && impacts.all { it.isBlocked }) {
            val blocking = impacts.flatMap { it.blocking }.distinctBy { it.id }
            val exclude = setOfNotNull(didokOf(request.from), didokOf(request.to))
            val extra =
                viaCandidates(blocking, hubs, exclude).mapNotNull { via ->
                    try {
                        ojp.trips(query.copy(via = request.via + via.toString()), historical).trips
                    } catch (e: OjpUnavailable) {
                        log.warn("Reroute search via {} failed: {}", via, e.message)
                        null
                    }
                }
            trips = trips + extra.flatten()
            rerouted = extra.any { it.isNotEmpty() }
        }
        return PlanResponse(
            rankTrips(trips, now.disruptions),
            now.disruptions,
            direct.source,
            direct.situations,
            rerouted,
        )
    }

    /** OJP stop search, or a name search over the scenario stations when OJP can't answer. */
    suspend fun stops(name: String): List<Location> =
        try {
            ojp.stops(name)
        } catch (e: OjpUnavailable) {
            log.warn("OJP stop search failed ({}), using known stations", e.message)
            offlineStops(name)
        }

    private fun offlineStops(name: String): List<Location> {
        val q = name.normalized()
        return stations()
            .filter { it.name.normalized().contains(q) }
            .sortedWith(compareBy({ !it.name.normalized().startsWith(q) }, { it.name }))
            .take(MAX_OFFLINE_STOPS)
            .map { Location(it) }
    }
}

/** Lowercase without accents, so «palez» finds «Palézieux». */
private fun String.normalized(): String =
    java.text.Normalizer
        .normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
