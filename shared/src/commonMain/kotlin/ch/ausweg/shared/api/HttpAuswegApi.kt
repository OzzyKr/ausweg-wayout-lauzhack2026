package ch.ausweg.shared.api

import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.NewTip
import ch.ausweg.shared.model.PlanRequest
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.SeekRequest
import ch.ausweg.shared.model.SpeedRequest
import ch.ausweg.shared.model.StartScenarioRequest
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.VoteRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** JSON settings shared with the server (`server/AuswegJson.kt`). */
val ApiJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

/**
 * [AuswegApi] over HTTP. [baseUrl] is e.g. `http://10.0.2.2:8080` (Android emulator) or `http://localhost:8080`.
 * The engine comes from the platform (OkHttp on Android, CIO on the JVM, fetch in the browser).
 */
@Suppress("TooManyFunctions") // One function per backend endpoint.
class HttpAuswegApi(
    baseUrl: String,
    private val client: HttpClient = defaultClient(),
) : AuswegApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun state(): StateResponse = client.get("$base/state").body()

    override suspend fun scenarios(): List<Scenario> = client.get("$base/scenarios").body()

    override suspend fun startScenario(
        scenarioId: String,
        speed: Double?,
    ): ClockState = postJson("/replay/start", StartScenarioRequest(scenarioId, play = true, speed = speed))

    override suspend fun play(): ClockState = client.post("$base/replay/play").body()

    override suspend fun pause(): ClockState = client.post("$base/replay/pause").body()

    override suspend fun seek(at: Instant): ClockState = postJson("/replay/seek", SeekRequest(at))

    override suspend fun speed(x: Double): ClockState = postJson("/replay/speed", SpeedRequest(x))

    override suspend fun live(): ClockState = client.post("$base/mode/live").body()

    override suspend fun stops(query: String): List<Location> =
        client.get("$base/stops") { parameter("q", query) }.body()

    override suspend fun plan(request: PlanRequest): PlanResponse = postJson("/plan", request)

    override suspend fun tips(
        stations: Set<Int>?,
        disruptionId: String?,
    ): List<RankedTip> =
        client
            .get("$base/tips") {
                stations?.let { parameter("stations", it.sorted().joinToString(",")) }
                disruptionId?.let { parameter("disruptionId", it) }
            }.body()

    override suspend fun routes(
        from: Int?,
        to: Int?,
    ): List<RankedRoute> =
        client
            .get("$base/routes") {
                from?.let { parameter("from", it) }
                to?.let { parameter("to", it) }
            }.body()

    override suspend fun postTip(tip: NewTip): Tip = postJson("/tips", tip)

    override suspend fun postRoute(route: NewRoute): CommunityRoute = postJson("/routes", route)

    override suspend fun voteTip(
        id: String,
        vote: VoteRequest,
    ): Tip = postJson("/tips/$id/vote", vote)

    override suspend fun voteRoute(
        id: String,
        vote: VoteRequest,
    ): CommunityRoute = postJson("/routes/$id/vote", vote)

    override fun events(): Flow<ServerEvent> =
        channelFlow {
            client.sse("$base/events") {
                incoming.collect { message -> parseServerEvent(message.event, message.data)?.let { send(it) } }
            }
        }

    /** Body type is reified too, so serialization never needs runtime reflection (wasm). */
    private suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
    ): T =
        client
            .post("$base$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body()

    companion object {
        /** A client with JSON, SSE and errors for non-2xx answers. */
        fun defaultClient(): HttpClient =
            HttpClient {
                expectSuccess = true
                install(ContentNegotiation) { json(ApiJson) }
                install(SSE)
            }
    }
}
