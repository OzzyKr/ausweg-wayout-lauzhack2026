package ch.ausweg.server

import ch.ausweg.server.api.replayRoutes
import ch.ausweg.server.api.stateRoutes
import ch.ausweg.server.events.eventsRoute
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port) { module(Backend.fromConfig(config)) }.start(wait = true)
}

fun Application.module(backend: Backend) {
    install(ContentNegotiation) { json(AuswegJson) }
    install(SSE)
    // The web app is served from another origin (wasm dev server, GitHub Pages). No cookies or auth, so any host.
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
    }
    routing {
        stateRoutes(backend)
        replayRoutes(backend)
        eventsRoute(backend)
    }
}
