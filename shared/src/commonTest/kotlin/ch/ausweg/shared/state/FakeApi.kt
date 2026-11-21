package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.ServerEvent
import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.NewTip
import ch.ausweg.shared.model.PlanRequest
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.TripSource
import ch.ausweg.shared.model.VoteRequest
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.model.Votes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Instant

/** In-memory backend for state tests. Push SSE events with [emit]; every call is recorded in [calls]. */
class FakeApi : AuswegApi {
    val events = MutableSharedFlow<ServerEvent>(replay = 16)
    val calls = mutableListOf<String>()
    var clock = ClockState(Mode.HISTORICAL, Fx.t("14:30"), playing = false, speed = 1.0, scenarioId = SCENARIO.id)
    var planResponse: PlanResponse? =
        PlanResponse(listOf(Fx.ic1, Fx.viaYverdon("14:40", "15:50")), emptyList(), TripSource.FIXTURE)
    var planError: Throwable? = null
    var stops: List<Location> = listOf(Location(LAUSANNE))
    var routes: List<RankedRoute> = listOf(ROUTE)
    var tips: List<RankedTip> = listOf(TIP)
    val posted = mutableListOf<NewRoute>()
    var failPost = false

    suspend fun emit(event: ServerEvent) = events.emit(event)

    override suspend fun state(): StateResponse = StateResponse(clock, emptyList()).also { calls += "state" }

    override suspend fun scenarios(): List<Scenario> = listOf(SCENARIO).also { calls += "scenarios" }

    override suspend fun startScenario(
        scenarioId: String,
        speed: Double?,
    ): ClockState = clock.copy(scenarioId = scenarioId, playing = true).also { calls += "start $scenarioId" }

    override suspend fun play(): ClockState = clock.copy(playing = true).also { calls += "play" }

    override suspend fun pause(): ClockState = clock.copy(playing = false).also { calls += "pause" }

    override suspend fun seek(at: Instant): ClockState = clock.copy(now = at).also { calls += "seek $at" }

    override suspend fun speed(x: Double): ClockState = clock.copy(speed = x).also { calls += "speed $x" }

    override suspend fun live(): ClockState = clock.copy(mode = Mode.LIVE, scenarioId = null).also { calls += "live" }

    override suspend fun stops(query: String): List<Location> = stops.also { calls += "stops $query" }

    override suspend fun plan(request: PlanRequest): PlanResponse {
        calls += "plan ${request.from} ${request.to}"
        planError?.let { throw it }
        return checkNotNull(planResponse)
    }

    override suspend fun tips(
        stations: Set<Int>?,
        disruptionId: String?,
    ): List<RankedTip> = tips.also { calls += "tips" }

    override suspend fun routes(
        from: Int?,
        to: Int?,
    ): List<RankedRoute> = routes.also { calls += "routes $from $to" }

    override suspend fun postTip(tip: NewTip): Tip = error("not used")

    override suspend fun postRoute(route: NewRoute): CommunityRoute {
        if (failPost) error("backend down")
        posted += route
        return ROUTE.route.copy(id = "new", legs = route.legs)
    }

    override suspend fun voteTip(
        id: String,
        vote: VoteRequest,
    ): Tip {
        calls += "voteTip $id ${vote.value}"
        return TIP.tip.copy(votes = TIP.tip.votes.copy(up = TIP.tip.votes.up + 1))
    }

    override suspend fun voteRoute(
        id: String,
        vote: VoteRequest,
    ): CommunityRoute {
        calls += "voteRoute $id ${vote.value} ${vote.voterId}"
        val v = ROUTE.route.votes
        return ROUTE.route.copy(votes = if (vote.value == VoteValue.UP) v.copy(up = v.up + 1) else v)
    }

    override fun events(): Flow<ServerEvent> = events

    companion object {
        val LAUSANNE = StopRef("8501120", "Lausanne", Fx.LAUSANNE)
        val FRIBOURG = StopRef("8504100", "Fribourg/Freiburg", Fx.FRIBOURG)
        val YVERDON = StopRef("8504200", "Yverdon-les-Bains", Fx.YVERDON)

        val SCENARIO =
            Scenario(
                id = "puidoux",
                title = "Puidoux – Vauderens",
                start = Fx.t("14:00"),
                end = Fx.t("16:30"),
                suggestedFrom = "8501120",
                suggestedTo = "8504100",
                suggestedFromName = "Lausanne",
                suggestedToName = "Fribourg/Freiburg",
            )

        val ROUTE =
            RankedRoute(
                route =
                    CommunityRoute(
                        id = "r1",
                        from = LAUSANNE,
                        to = FRIBOURG,
                        legs = listOf(RouteLeg(LAUSANNE, YVERDON, "IC5"), RouteLeg(YVERDON, FRIBOURG, "S30")),
                        createdAt = Fx.t("12:00"),
                        disruptionId = "seed",
                        votes = Votes(up = 11),
                    ),
                notes = emptyList(),
                match = CommunityMatch.SIMILAR_DISRUPTION,
                score = 10.0,
            )

        val TIP =
            RankedTip(
                Tip("t1", TipKind.TRANSFER, "Platform 1 to 7 is fine", Fx.t("08:00"), station = Fx.LAUSANNE),
                CommunityMatch.PERMANENT,
                1.0,
            )
    }
}
