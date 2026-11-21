package ch.ausweg.server

import ch.ausweg.server.ingest.DisruptionSource
import ch.ausweg.server.ingest.FallbackSource
import ch.ausweg.server.ingest.JsonFileSource
import ch.ausweg.server.ingest.ScenarioDirSource
import ch.ausweg.server.ojp.HttpOjpTransport
import ch.ausweg.server.ojp.OjpFixtures
import ch.ausweg.server.ojp.OjpService
import ch.ausweg.server.ojp.PlanService
import ch.ausweg.server.replay.ModeController
import ch.ausweg.server.replay.Snapshot
import ch.ausweg.server.replay.SystemWallClock
import ch.ausweg.server.replay.WallClock
import ch.ausweg.server.tips.CommunityService
import ch.ausweg.server.tips.CommunityStore
import ch.ausweg.shared.model.StateResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Optional parts of the [Backend]; the defaults work offline (no OJP, in-memory community store).
 *
 * @property ojp trip and stop search; null builds one without a key (fixtures only).
 * @property hubs DIDOK numbers to reroute through.
 */
class BackendServices(
    val ojp: OjpService? = null,
    val hubs: List<Int> = emptyList(),
    val community: CommunityStore = CommunityStore.inMemory(),
    val staffToken: String? = null,
)

/** The server's long-lived parts, built once and handed to the routes. */
class Backend(
    val source: DisruptionSource,
    wall: WallClock = SystemWallClock,
    val sseTick: Duration = 1.seconds,
    val sseHeartbeat: Duration = 5.seconds,
    services: BackendServices = BackendServices(),
) {
    val mode = ModeController(source, wall)

    val plan =
        PlanService(
            ojp = services.ojp ?: OjpService(transport = null, wall = wall),
            hubs = services.hubs,
            state = { state() },
            stations = { source.load().stations },
        )

    val community = CommunityService(services.community, state = { state() }, staffToken = services.staffToken)

    /** The clock plus every disruption known at [at] (default: the clock's current time), and their timeline. */
    fun state(at: Instant? = null): StateResponse {
        val clock = mode.clockState().let { if (at != null) it.copy(now = at) else it }
        val data = source.load()
        val disruptions = Snapshot.at(data.updates, clock.now)
        val visible = disruptions.map { it.id }.toSet()
        val timeline = data.timeline.filter { it.t <= clock.now && it.disruptionId in visible }.sortedBy { it.t }
        return StateResponse(clock, disruptions, timeline)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Backend::class.java)

        fun fromConfig(config: ServerConfig): Backend {
            val source = FallbackSource(ScenarioDirSource(config.scenariosDir), JsonFileSource(config.eventsFile))
            val transport = config.ojpKey?.let(::HttpOjpTransport)
            if (transport == null) log.warn("No OJP_API_TOKEN: trip search uses recorded fixtures only")
            val ojp = OjpService(transport, OjpFixtures(config.fixturesDir, config.recordOjp))
            val community = CommunityStore.open(config.dbFile).also { it.seedIfEmpty(config.communitySeed) }
            val services = BackendServices(ojp, readHubs(config.hubsFile), community, config.staffToken?.value)
            return Backend(source, services = services)
        }

        private fun readHubs(file: Path): List<Int> =
            try {
                AuswegJson.decodeFromString<HubsFile>(file.readText()).hubs.map { it.didok }
            } catch (e: IOException) {
                log.warn("Hubs file {} not readable ({}), no rerouting", file, e.javaClass.simpleName)
                emptyList()
            } catch (e: IllegalArgumentException) {
                log.warn("Hubs file {} invalid ({}), no rerouting", file, e.javaClass.simpleName)
                emptyList()
            }
    }
}

/** `data/routing/hubs.json`: stations to reroute through, most useful first. */
@Serializable
private data class HubsFile(
    val hubs: List<Hub>,
)

@Serializable
private data class Hub(
    val name: String,
    val didok: Int,
)
