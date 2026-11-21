package ch.ausweg.server.ojp

import ch.ausweg.server.replay.SystemWallClock
import ch.ausweg.server.replay.WallClock
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.model.TripSource
import ch.ausweg.shared.ojp.OjpParseException
import ch.ausweg.shared.ojp.OjpParser
import ch.ausweg.shared.ojp.OjpRequests
import ch.ausweg.shared.ojp.OjpTripResponse
import ch.ausweg.shared.ojp.TripQuery
import ch.ausweg.shared.routing.daysBetween
import ch.ausweg.shared.routing.movedByDays
import ch.ausweg.shared.routing.sameWeekdayNear
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Generic requestor name sent to OJP: no personal or project details. */
const val OJP_REQUESTOR = "ausweg-hackathon"

/** Trips from OJP (or the fixtures) for one query, on the requested date. */
data class FoundTrips(
    val trips: List<Trip>,
    val situations: List<Situation>,
    val source: TripSource,
)

/**
 * OJP trip and stop searches with what the demo needs around them:
 * - a cache ([cacheTtl]) and a minimum gap between calls ([minInterval]), to stay under OJP's rate limit;
 * - historical mode: OJP only knows the current timetable, so the search runs on the same weekday and local time
 *   close to today and the trips are moved back onto the replayed date ([TripSource.SHIFTED]);
 * - when OJP fails (or there is no key, [transport] null), recorded answers from [fixtures] ([TripSource.FIXTURE]).
 *
 * The key never appears in logs; only error classes and fixture names do.
 */
class OjpService(
    private val transport: OjpTransport?,
    private val fixtures: OjpFixtures = OjpFixtures(null),
    private val zone: TimeZone = TimeZone.of("Europe/Zurich"),
    private val wall: WallClock = SystemWallClock,
    private val minInterval: Duration = 250.milliseconds,
    private val cacheTtl: Duration = 60.seconds,
) {
    private val log = LoggerFactory.getLogger(OjpService::class.java)
    private val cache = ConcurrentHashMap<String, Pair<Instant, String>>()
    private val gate = Mutex()
    private var lastCall: TimeSource.Monotonic.ValueTimeMark? = null

    val configured: Boolean get() = transport != null

    /**
     * Trips for [query] on its own date. [historical]: [TripQuery.departAt] is a replayed (past) time.
     * @throws OjpUnavailable if neither OJP nor a fixture can answer.
     */
    suspend fun trips(
        query: TripQuery,
        historical: Boolean,
    ): FoundTrips {
        val askAt = if (historical) sameWeekdayNear(query.departAt, wall(), zone) else query.departAt
        val asked = query.copy(departAt = askAt)
        return try {
            val xml = fetch("trip:$asked") { OjpRequests.trip(asked, wall(), OJP_REQUESTOR) }
            val response = parseTrips(xml)
            fixtures.write(query, xml)
            if (historical) {
                val days = daysBetween(askAt, query.departAt, zone)
                FoundTrips(response.trips.map { it.movedByDays(days, zone) }, emptyList(), TripSource.SHIFTED)
            } else {
                FoundTrips(response.trips, response.situations, TripSource.LIVE)
            }
        } catch (e: OjpUnavailable) {
            log.warn("OJP trip search failed ({}), trying fixture {}", e.message, OjpFixtures.fileName(query))
            fromFixture(query) ?: throw e
        }
    }

    /** Stops matching [name], best first. @throws OjpUnavailable when OJP can't answer. */
    suspend fun stops(name: String): List<Location> {
        val xml = fetch("loc:$name") { OjpRequests.location(name, wall(), OJP_REQUESTOR) }
        return try {
            OjpParser.locations(xml).locations
        } catch (e: OjpParseException) {
            throw OjpUnavailable("Unreadable OJP location answer", e)
        }
    }

    /** The recorded answer for [query], moved so its first trip falls on the requested date; null if none. */
    private fun fromFixture(query: TripQuery): FoundTrips? {
        val trips =
            try {
                fixtures.read(query)?.let { parseTrips(it).trips }
            } catch (_: OjpUnavailable) {
                null
            } ?: return null
        val days = trips.minOfOrNull { it.departure }?.let { daysBetween(it, query.departAt, zone) } ?: 0
        return FoundTrips(trips.map { it.movedByDays(days, zone) }, emptyList(), TripSource.FIXTURE)
    }

    private fun parseTrips(xml: String): OjpTripResponse =
        try {
            OjpParser.trips(xml)
        } catch (e: OjpParseException) {
            throw OjpUnavailable("Unreadable OJP trip answer", e)
        }

    private suspend fun fetch(
        key: String,
        body: () -> String,
    ): String {
        cache[key]?.let { (at, xml) -> if (wall() - at < cacheTtl) return xml }
        val t = transport ?: throw OjpUnavailable("OJP not configured (set OJP_API_TOKEN)")
        val xml =
            gate.withLock {
                lastCall?.let { delay(minInterval - it.elapsedNow()) }
                try {
                    t.post(body())
                } finally {
                    lastCall = TimeSource.Monotonic.markNow()
                }
            }
        if (cache.size > MAX_CACHE) cache.clear()
        cache[key] = wall() to xml
        return xml
    }

    private companion object {
        const val MAX_CACHE = 500
    }
}
