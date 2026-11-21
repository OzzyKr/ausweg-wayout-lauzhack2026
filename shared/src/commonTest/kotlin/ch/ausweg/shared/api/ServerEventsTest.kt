package ch.ausweg.shared.api

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.state.FakeApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerEventsTest {
    private val clock = ClockState(Mode.HISTORICAL, Fx.t("14:30"), playing = true, speed = 10.0, scenarioId = "p")

    @Test
    fun parsesTheThreeEvents() {
        val c = assertIs<ServerEvent.Clock>(parseServerEvent("clock", ApiJson.encodeToString(clock)))
        assertEquals(clock, c.clock)
        val state = StateResponse(clock, listOf(Fx.fire))
        val s = assertIs<ServerEvent.State>(parseServerEvent("state", ApiJson.encodeToString(state)))
        assertEquals(state, s.state)
        assertEquals(ServerEvent.Community(7), parseServerEvent("community", "7\n"))
    }

    @Test
    fun badOrUnknownEventsAreSkipped() {
        assertNull(parseServerEvent("clock", "{not json"))
        assertNull(parseServerEvent("other", "{}"))
        assertNull(parseServerEvent("community", "x"))
        assertNull(parseServerEvent("clock", null))
    }

    @Test
    fun sseEventsComeAfterALiveLink() =
        runTest {
            val api = FakeApi()
            api.emit(ServerEvent.Community(3))
            val got = api.liveEvents().take(2).toList()
            assertEquals(listOf(ServerEvent.Link(LinkStatus.LIVE), ServerEvent.Community(3)), got)
        }

    @Test
    fun aBugInTheCollectorIsNotSwallowed() =
        runTest {
            val api = FakeApi()
            api.emit(ServerEvent.Community(3))
            val e =
                assertFailsWith<IllegalStateException> {
                    api.liveEvents().collect { if (it is ServerEvent.Community) error("bug in onEvent") }
                }
            assertEquals("bug in onEvent", e.message)
        }

    @Test
    fun brokenSseFallsBackToPolling() =
        runTest {
            val api =
                object : AuswegApi by FakeApi() {
                    override fun events(): Flow<ServerEvent> = flow { error("no SSE in this browser") }
                }
            val got = api.liveEvents().take(3).toList()
            assertEquals(ServerEvent.Link(LinkStatus.POLLING), got[0])
            assertIs<ServerEvent.Clock>(got[1])
            assertIs<ServerEvent.State>(got[2])
        }

    @Test
    fun unreachableBackendIsOffline() =
        runTest {
            val api =
                object : AuswegApi by FakeApi() {
                    override fun events(): Flow<ServerEvent> = flow { error("down") }

                    override suspend fun state(): StateResponse = error("down")
                }
            assertEquals(listOf(ServerEvent.Link(LinkStatus.OFFLINE)), api.liveEvents().take(1).toList())
        }
}
