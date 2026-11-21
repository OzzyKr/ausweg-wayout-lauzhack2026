package ch.ausweg.server.api

import ch.ausweg.server.Backend
import ch.ausweg.server.replay.ReplayClock
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.SeekRequest
import ch.ausweg.shared.model.SpeedRequest
import ch.ausweg.shared.model.StartScenarioRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Replay bar controls. All of them answer with the new [ClockState]; SSE pushes it to every client too. */
fun Route.replayRoutes(backend: Backend) {
    val mode = backend.mode

    route("/replay") {
        post("/start") {
            val req = call.receive<StartScenarioRequest>()
            val speed = req.speed
            if (speed != null && !validSpeed(speed)) return@post call.badSpeed()
            val state = mode.startScenario(req.scenarioId, req.play, req.speed)
            if (state == null) call.respond(HttpStatusCode.NotFound, "unknown scenario") else call.respond(state)
        }
        post("/play") { call.respondControl(mode.control { it.play() }) }
        post("/pause") { call.respondControl(mode.control { it.pause() }) }
        post("/seek") {
            val req = call.receive<SeekRequest>()
            call.respondControl(mode.control { it.seek(req.at) })
        }
        post("/speed") {
            val req = call.receive<SpeedRequest>()
            if (!validSpeed(req.x)) return@post call.badSpeed()
            call.respondControl(mode.control { it.setSpeed(req.x) })
        }
    }

    post("/mode/live") { call.respond(mode.goLive()) }
}

private fun validSpeed(x: Double) = x > 0.0 && x <= ReplayClock.MAX_SPEED

private suspend fun RoutingCall.badSpeed() =
    respond(HttpStatusCode.BadRequest, "speed must be in (0, ${ReplayClock.MAX_SPEED}]")

private suspend fun RoutingCall.respondControl(state: ClockState?) {
    if (state == null) respond(HttpStatusCode.Conflict, "live mode: start a scenario first") else respond(state)
}
