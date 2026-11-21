package ch.ausweg.server.api

import ch.ausweg.server.Backend
import ch.ausweg.server.tips.InvalidRequest
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.NewTip
import ch.ausweg.shared.model.VoteRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Header carrying the staff token for endorsements (demo only: one shared token, `STAFF_TOKEN`). */
const val STAFF_TOKEN_HEADER = "X-Staff-Token"

/** Community tips, routes, votes and staff endorsements. Everything is anonymous; see `shared/model/Community.kt`. */
fun Route.communityRoutes(backend: Backend) {
    val community = backend.community

    route("/tips") {
        /** `?stations=8501120,8504100` limits transfer tips; `?disruptionId=` limits disruption tips. */
        get {
            val stations = call.intList("stations")?.toSet()
            call.respond(community.tips(stations, call.request.queryParameters["disruptionId"]))
        }
        post {
            val new = call.receive<NewTip>()
            call.validated { call.respond(HttpStatusCode.Created, community.addTip(new)) }
        }
        post("/{id}/vote") {
            val vote = call.receive<VoteRequest>()
            call.validated { call.respondOrNotFound(community.voteTip(call.id(), vote)) }
        }
        post("/{id}/endorse") { call.endorse(backend) }
    }

    route("/routes") {
        /** `?from=8501120&to=8504100` (DIDOK numbers), both optional. */
        get {
            val from = call.request.queryParameters["from"]?.toIntOrNull()
            val to = call.request.queryParameters["to"]?.toIntOrNull()
            call.respond(community.routes(from, to))
        }
        post {
            val new = call.receive<NewRoute>()
            call.validated { call.respond(HttpStatusCode.Created, community.addRoute(new)) }
        }
        post("/{id}/vote") {
            val vote = call.receive<VoteRequest>()
            call.validated { call.respondOrNotFound(community.voteRoute(call.id(), vote)) }
        }
        post("/{id}/endorse") { call.endorse(backend) }
    }
}

private fun RoutingCall.id(): String = parameters["id"].orEmpty()

private fun RoutingCall.intList(name: String): List<Int>? =
    request.queryParameters[name]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }

private suspend fun RoutingCall.validated(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: InvalidRequest) {
        respond(HttpStatusCode.BadRequest, e.message.orEmpty())
    }
}

private suspend inline fun <reified T : Any> RoutingCall.respondOrNotFound(value: T?) {
    if (value == null) respond(HttpStatusCode.NotFound, "not found") else respond(value)
}

/** 403 without the staff token, 404 for an unknown id, else 204. */
private suspend fun RoutingCall.endorse(backend: Backend) {
    when {
        !backend.community.isStaff(
            request.headers[STAFF_TOKEN_HEADER],
        ) -> respond(HttpStatusCode.Forbidden, "staff only")
        !backend.community.endorse(id()) -> respond(HttpStatusCode.NotFound, "not found")
        else -> respond(HttpStatusCode.NoContent)
    }
}
