package ch.ausweg.server.events

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.server.FakeWall
import ch.ausweg.server.ingest.JsonFileSource
import ch.ausweg.server.instant
import ch.ausweg.server.module
import ch.ausweg.server.resourcePath
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.StateResponse
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.take
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class EventsRouteTest {
    @Test
    fun `sends clock and state on connect, then the new clock after a mode change`() =
        testApplication {
            val wall = FakeWall(instant("2026-09-01T08:16:00Z"))
            val backend =
                Backend(JsonFileSource(resourcePath("events-fixture.json")), wall, sseTick = 20.milliseconds)
            application { module(backend) }
            val sseClient = createClient { install(SSE) }

            val received = mutableListOf<ServerSentEvent>()
            sseClient.sse("/events") {
                // Collect once (a second collect would reconnect): two events on connect, then switch mode.
                incoming.take(3).collect { event ->
                    received += event
                    if (received.size == 2) {
                        client.post("/replay/start") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"scenarioId":"A","play":false}""")
                        }
                    }
                }
            }
            assertEquals(listOf("clock", "state", "clock"), received.map { it.event })
            val state = AuswegJson.decodeFromString<StateResponse>(checkNotNull(received[1].data))
            assertEquals(listOf("A", "B"), state.disruptions.map { it.id })
            val clock = AuswegJson.decodeFromString<ClockState>(checkNotNull(received[2].data))
            assertEquals(Mode.HISTORICAL, clock.mode)
        }
}
