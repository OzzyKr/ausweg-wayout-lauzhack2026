package ch.ausweg.server.ojp

import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.disruption.disruptionsFrom
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.PlanRequest
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.TripSource
import ch.ausweg.shared.ojp.TripQuery
import ch.ausweg.shared.ojp.didokOf
import ch.ausweg.shared.routing.rankTrips
import ch.ausweg.shared.routing.viaCandidates
import org.slf4j.LoggerFactory
import kotlin.time.Instant

private const val MAX_OFFLINE_STOPS = 10

/**
 * `POST /plan` and `GET /stops`.
 *
 * Planning: search OJP; in live mode, the situations OJP attaches to the answer become disruptions too (shared
 * `disruptionsFrom()`). If every trip is blocked by a current disruption (shared `affects()`), search again through
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
        var answers = listOf(direct)
        var disruptions = withLive(now.disruptions, answers, query.departAt)

        val impacts = direct.trips.map { affects(it, disruptions) }
        var trips = direct.trips
        var rerouted = false
        if (impacts.isNotEmpty() && impacts.all { it.isBlocked }) {
            val blocking = impacts.flatMap { it.blocking }.distinctBy { it.id }
            val exclude = setOfNotNull(didokOf(request.from), didokOf(request.to))
            val extra =
                viaCandidates(blocking, hubs, exclude).mapNotNull { via ->
                    try {
                        ojp.trips(query.copy(via = request.via + via.toString()), historical)
                    } catch (e: OjpUnavailable) {
                        log.warn("Reroute search via {} failed: {}", via, e.message)
                        null
                    }
                }
            answers = answers + extra
            trips = trips + extra.flatMap { it.trips }
            rerouted = extra.any { it.trips.isNotEmpty() }
            disruptions = withLive(now.disruptions, answers, query.departAt)
        }
        return PlanResponse(
            rankTrips(trips, disruptions),
            disruptions,
            direct.source,
            answers.flatMap { it.situations }.distinctBy { it.id },
            rerouted,
        )
    }

    /**
     * [known] plus the OJP situations of the live [answers] as disruptions (shared `disruptionsFrom()`), as they stand
     * at [departAt]: a night closure starting at 21:30 counts for a 22:00 trip planned at 21:00. All answers go in
     * together, so a situation is built from every leg that references it, not just the direct search's. Shifted and
     * recorded answers have no situations, so historical mode is unchanged.
     */
    private fun withLive(
        known: List<Disruption>,
        answers: List<FoundTrips>,
        departAt: Instant,
    ): List<Disruption> {
        val live = answers.filter { it.source == TripSource.LIVE }
        if (live.isEmpty()) return known
        val found = disruptionsFrom(live.flatMap { it.situations }, live.flatMap { it.trips }, departAt)
        return (known + found).distinctBy { it.id }
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
