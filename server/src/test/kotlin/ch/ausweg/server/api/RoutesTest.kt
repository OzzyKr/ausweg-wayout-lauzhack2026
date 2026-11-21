package ch.ausweg.server.api

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.server.FakeWall
import ch.ausweg.server.ingest.JsonFileSource
import ch.ausweg.server.instant
import ch.ausweg.server.module
import ch.ausweg.server.resourcePath
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.StateResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RoutesTest {
    private val wall = FakeWall(instant("2026-11-21T10:00:00Z"))

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(Backend(JsonFileSource(resourcePath("events-fixture.json")), wall)) }
            block()
        }

    private suspend inline fun <reified T> HttpResponse.decode(): T = AuswegJson.decodeFromString(bodyAsText())

    private suspend fun HttpClient.postJson(
        path: String,
        body: String = "{}",
    ) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `starts in live mode on the wall clock`() =
        withApp {
            val state = client.get("/state").decode<StateResponse>()
            assertEquals(Mode.LIVE, state.clock.mode)
            assertEquals(wall.now, state.clock.now)
            assertTrue(state.disruptions.isEmpty())
        }

    @Test
    fun `lists derived scenarios`() =
        withApp {
            assertEquals(listOf("A", "B"), client.get("/scenarios").decode<List<Scenario>>().map { it.id })
        }

    @Test
    fun `replay shows what was known as the clock moves`() =
        withApp {
            val started = client.postJson("/replay/start", """{"scenarioId":"A","play":false}""").decode<ClockState>()
            assertEquals(Mode.HISTORICAL, started.mode)
            assertEquals("A", started.scenarioId)
            assertTrue(
                client
                    .get("/state")
                    .decode<StateResponse>()
                    .disruptions
                    .isEmpty(),
            )

            client.postJson("/replay/seek", """{"at":"2026-09-01T08:45:00Z"}""")
            assertEquals(
                2,
                client
                    .get("/state")
                    .decode<StateResponse>()
                    .disruptions
                    .single()
                    .version,
            )

            client.postJson("/replay/speed", """{"x":60}""")
            client.postJson("/replay/play")
            wall.now += 1.minutes
            val later = client.get("/state").decode<StateResponse>()
            assertEquals(instant("2026-09-01T09:45:00Z"), later.clock.now)
            assertEquals(3, later.disruptions.single().version)

            assertEquals(Mode.LIVE, client.postJson("/mode/live").decode<ClockState>().mode)
        }

    @Test
    fun `state can be inspected at any time without moving the clock`() =
        withApp {
            val state = client.get("/state?at=2026-09-01T08:16:00Z").decode<StateResponse>()
            assertEquals(listOf("A", "B"), state.disruptions.map { it.id })
            assertEquals(HttpStatusCode.BadRequest, client.get("/state?at=yesterday").status)
        }

    @Test
    fun `rejects bad replay commands`() =
        withApp {
            assertEquals(HttpStatusCode.Conflict, client.postJson("/replay/play").status)
            assertEquals(HttpStatusCode.NotFound, client.postJson("/replay/start", """{"scenarioId":"nope"}""").status)
            client.postJson("/replay/start", """{"scenarioId":"A"}""")
            assertEquals(HttpStatusCode.BadRequest, client.postJson("/replay/speed", """{"x":0}""").status)
            assertEquals(HttpStatusCode.BadRequest, client.postJson("/replay/seek", """{"at":"soon"}""").status)
        }
}
