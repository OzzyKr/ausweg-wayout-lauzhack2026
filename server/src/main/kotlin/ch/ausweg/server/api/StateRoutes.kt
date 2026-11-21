package ch.ausweg.server.api

import ch.ausweg.server.Backend
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.time.Instant

fun Route.stateRoutes(backend: Backend) {
    get("/health") { call.respond("ok") }

    /** `?at=<ISO instant>` shows what was known at that moment without moving the shared clock (debugging). */
    get("/state") {
        val atParam = call.request.queryParameters["at"]
        val at = atParam?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (atParam != null && at == null) {
            call.respond(HttpStatusCode.BadRequest, "at must be an ISO-8601 instant")
        } else {
            call.respond(backend.state(at))
        }
    }

    get("/scenarios") { call.respond(backend.source.load().scenarios) }
}
