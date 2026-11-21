package ch.ausweg.server.api

import ch.ausweg.server.Backend
import ch.ausweg.server.ojp.OjpUnavailable
import ch.ausweg.shared.model.PlanRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Trip planning and stop search (OJP behind the backend: the key never reaches the app). */
fun Route.planRoutes(backend: Backend) {
    get("/stops") {
        val q = call.request.queryParameters["q"]?.trim()
        if (q.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "q is required")
        } else {
            call.respond(backend.plan.stops(q))
        }
    }

    post("/plan") {
        val req = call.receive<PlanRequest>()
        try {
            call.respond(backend.plan.plan(req))
        } catch (_: OjpUnavailable) {
            call.respond(HttpStatusCode.ServiceUnavailable, "trip search unavailable")
        }
    }
}
