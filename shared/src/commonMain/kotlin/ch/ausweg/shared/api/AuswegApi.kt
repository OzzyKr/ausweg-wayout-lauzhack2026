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
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.VoteRequest
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** The backend as the app sees it (`docs/api.md`). [HttpAuswegApi] talks HTTP; tests use a fake. */
@Suppress("TooManyFunctions") // One function per backend endpoint.
interface AuswegApi {
    suspend fun state(): StateResponse

    suspend fun scenarios(): List<Scenario>

    suspend fun startScenario(
        scenarioId: String,
        speed: Double? = null,
    ): ClockState

    suspend fun play(): ClockState

    suspend fun pause(): ClockState

    suspend fun seek(at: Instant): ClockState

    suspend fun speed(x: Double): ClockState

    suspend fun live(): ClockState

    suspend fun stops(query: String): List<Location>

    suspend fun plan(request: PlanRequest): PlanResponse

    suspend fun tips(
        stations: Set<Int>? = null,
        disruptionId: String? = null,
    ): List<RankedTip>

    suspend fun routes(
        from: Int?,
        to: Int?,
    ): List<RankedRoute>

    suspend fun postTip(tip: NewTip): Tip

    suspend fun postRoute(route: NewRoute): CommunityRoute

    suspend fun voteTip(
        id: String,
        vote: VoteRequest,
    ): Tip

    suspend fun voteRoute(
        id: String,
        vote: VoteRequest,
    ): CommunityRoute

    /** `GET /events`: ends or throws when the connection drops. See [liveEvents] for the resilient version. */
    fun events(): Flow<ServerEvent>
}
