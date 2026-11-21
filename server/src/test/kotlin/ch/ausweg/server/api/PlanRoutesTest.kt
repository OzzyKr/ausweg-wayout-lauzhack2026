package ch.ausweg.server.api

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.server.BackendServices
import ch.ausweg.server.FakeWall
import ch.ausweg.server.OjpXml
import ch.ausweg.server.ingest.ScenarioDirSource
import ch.ausweg.server.instant
import ch.ausweg.server.module
import ch.ausweg.server.ojp.OjpService
import ch.ausweg.server.ojp.OjpTransport
import ch.ausweg.server.ojp.OjpUnavailable
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.TripSource
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class PlanRoutesTest {
    private val wall = FakeWall(instant("2026-11-21T17:00:00Z"))
    private val hubs = listOf(8504012, 8504200, 8507000) // Puidoux (closed, skipped), Yverdon, Bern

    // OJP answers for next Friday: the direct IR15 through the closure, or the Yverdon detour when a via is asked.
    private val ojp =
        OjpTransport { xml ->
            if (xml.contains("<Via>")) {
                OjpXml.response(OjpXml.viaYverdon("2026-11-27T15:40:00Z", "2026-11-27T17:05:00Z"))
            } else {
                OjpXml.response(OjpXml.direct("2026-11-27T15:45:00Z", "2026-11-27T16:30:00Z"))
            }
        }

    private fun withApp(
        transport: OjpTransport? = ojp,
        replayAt: String? = null,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val backend =
            Backend(
                ScenarioDirSource(Path.of("../data/scenarios")),
                wall,
                services = BackendServices(OjpService(transport, wall = wall, minInterval = Duration.ZERO), hubs),
            )
        if (replayAt != null) {
            backend.mode.startScenario("puidoux-vauderens_2026-09-25", play = false)
            backend.mode.control { it.seek(instant(replayAt)) }
        }
        application { module(backend) }
        block()
    }

    private suspend fun ApplicationTestBuilder.plan(body: String = """{"from":"8501120","to":"8504100"}""") =
        client.post("/plan") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun `during the fire the detour comes first and the blocked direct trip is pushed down`() =
        withApp(replayAt = "2026-09-25T14:30:00Z") {
            val res = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertEquals(TripSource.SHIFTED, res.source)
            assertTrue(res.rerouted)
            assertEquals(listOf("IC5", "IR15"), res.trips.map { it.legs.first().line })
            assertEquals(2, res.trips.size) // Yverdon and Bern searches return the same detour: kept once
            assertEquals(instant("2026-09-25T14:40:00Z"), res.trips.first().departure)
            assertEquals("2195005195853", res.disruptions.single().id)
        }

    @Test
    fun `before the fire the direct trip is fine and no reroute happens`() =
        withApp(replayAt = "2026-09-25T14:01:00Z") {
            val res = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertFalse(res.rerouted)
            assertEquals(listOf("IR15"), res.trips.map { it.legs.first().line })
        }

    @Test
    fun `via searches that find nothing are not a reroute`() =
        withApp(
            transport = { xml ->
                if (xml.contains("<Via>")) {
                    OjpXml.response()
                } else {
                    OjpXml.response(OjpXml.direct("2026-11-27T15:45:00Z", "2026-11-27T16:30:00Z"))
                }
            },
            replayAt = "2026-09-25T14:30:00Z",
        ) {
            val res = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertFalse(res.rerouted)
            assertEquals(listOf("IR15"), res.trips.map { it.legs.first().line })
        }

    @Test
    fun `no OJP and no fixture answers 503`() =
        withApp(transport = { throw OjpUnavailable("down") }) {
            assertEquals(HttpStatusCode.ServiceUnavailable, plan().status)
        }

    @Test
    fun `stop search uses OJP`() =
        withApp(transport = { OjpXml.locations(8501120 to "Lausanne") }) {
            val stops = AuswegJson.decodeFromString<List<Location>>(client.get("/stops?q=Laus").bodyAsText())
            assertEquals(listOf("Lausanne"), stops.map { it.stop.name })
        }

    @Test
    fun `stop search falls back to the scenario stations without OJP`() =
        withApp(transport = null) {
            val stops = AuswegJson.decodeFromString<List<Location>>(client.get("/stops?q=palez").bodyAsText())
            assertEquals(listOf("Palézieux" to 8504014), stops.map { it.stop.name to it.stop.didok })
        }

    @Test
    fun `stop search needs q`() =
        withApp {
            assertEquals(HttpStatusCode.BadRequest, client.get("/stops").status)
        }

    /** Live mode at 17:00: OJP answers today's trips, and the direct one carries [situation]. */
    private fun liveOjp(situation: String) =
        OjpTransport { xml ->
            if (xml.contains("<Via>")) {
                OjpXml.response(OjpXml.viaYverdon("2026-11-21T17:10:00Z", "2026-11-21T18:35:00Z"))
            } else {
                OjpXml.responseWithSituations(
                    listOf(situation),
                    OjpXml.direct("2026-11-21T17:15:00Z", "2026-11-21T18:00:00Z"),
                )
            }
        }

    @Test
    fun `live OJP interruption blocks the direct trip and reroutes`() =
        withApp(
            transport =
                liveOjp(
                    OjpXml.situation(
                        "sit-1",
                        "Der Bahnverkehr zwischen Puidoux und Vauderens ist unterbrochen.",
                        "2026-11-21T16:00:00Z",
                        "2026-11-21T20:00:00Z",
                        8504012,
                        8504021,
                    ),
                ),
        ) {
            val res = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertEquals(TripSource.LIVE, res.source)
            assertEquals(listOf("ojp:sit-1"), res.disruptions.map { it.id })
            assertTrue(res.rerouted)
            assertEquals(listOf("IC5", "IR15"), res.trips.map { it.legs.first().line })
            assertEquals(listOf("sit-1"), res.situations.map { it.id })
        }

    @Test
    fun `live OJP restriction is only a warning`() =
        withApp(
            transport =
                liveOjp(
                    OjpXml.situation(
                        "sit-2",
                        "Der Bahnverkehr zwischen Puidoux und Vauderens ist eingeschränkt.",
                        "2026-11-21T16:00:00Z",
                        "2026-11-21T20:00:00Z",
                        8504012,
                        8504021,
                    ),
                ),
        ) {
            val res = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertEquals(listOf("ojp:sit-2"), res.disruptions.map { it.id })
            assertFalse(res.rerouted)
            assertEquals(listOf("IR15"), res.trips.map { it.legs.first().line })
        }

    @Test
    fun `live situations count at the departure time, not when planning`() =
        withApp(
            transport =
                liveOjp(
                    OjpXml.situation(
                        "sit-night",
                        "Der Bahnverkehr zwischen Puidoux und Vauderens ist unterbrochen.",
                        "2026-11-21T21:30:00Z",
                        "2026-11-22T02:30:00Z",
                        8504012,
                        8504021,
                    ),
                ),
        ) {
            val now = AuswegJson.decodeFromString<PlanResponse>(plan().bodyAsText())
            assertTrue(now.disruptions.isEmpty(), "17:00: the night closure isn't on yet")

            val later = plan("""{"from":"8501120","to":"8504100","departAt":"2026-11-21T22:00:00Z"}""")
            val res = AuswegJson.decodeFromString<PlanResponse>(later.bodyAsText())
            assertEquals(listOf("ojp:sit-night"), res.disruptions.map { it.id })
        }
}
