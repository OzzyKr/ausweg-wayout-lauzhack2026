package ch.ausweg.server.events

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.shared.model.Disruption
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration

/**
 * `GET /events` (server-sent events):
 * - `clock`: [ch.ausweg.shared.model.ClockState] on every replay/mode change, and as a heartbeat.
 * - `state`: [ch.ausweg.shared.model.StateResponse] whenever the set of known disruptions changes,
 *   e.g. because the (simulated) clock passed a new publication.
 *
 * - `community`: the community revision (a number) whenever a tip, route, vote or endorsement changes:
 *   the app refetches `/tips` and `/routes`.
 *
 * All three are sent once on connect. Deciding whether a change matters to the user's route is the app's job.
 */
fun Route.eventsRoute(backend: Backend) {
    sse("/events") {
        var lastRevision = -1L
        var lastDisruptions: List<Disruption>? = null
        var lastCommunity = -1L
        var sinceHeartbeat = Duration.ZERO
        while (currentCoroutineContext().isActive) {
            val revision = backend.mode.revision.value
            val state = backend.state()
            if (revision != lastRevision || sinceHeartbeat >= backend.sseHeartbeat) {
                send(AuswegJson.encodeToString(state.clock), event = "clock")
                lastRevision = revision
                sinceHeartbeat = Duration.ZERO
            }
            if (state.disruptions != lastDisruptions) {
                send(AuswegJson.encodeToString(state), event = "state")
                lastDisruptions = state.disruptions
            }
            val community = backend.community.revision.value
            if (community != lastCommunity) {
                send(community.toString(), event = "community")
                lastCommunity = community
            }
            delay(backend.sseTick)
            sinceHeartbeat += backend.sseTick
        }
    }
}
