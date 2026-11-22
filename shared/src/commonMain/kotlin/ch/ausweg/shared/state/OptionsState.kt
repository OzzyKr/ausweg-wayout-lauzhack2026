package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.PlanRequest
import ch.ausweg.shared.model.PlanResponse
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.routing.OptionsView
import ch.ausweg.shared.routing.needsReplan
import ch.ausweg.shared.routing.planOptions
import ch.ausweg.shared.routing.trip
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Why the options screen has nothing to show. Each value is an i18n key (`options.error.<name>`). */
enum class LoadError {
    /** The backend answered, but there's no connection. */
    NO_CONNECTIONS,

    /** The backend can't get trips (OJP down and no recorded answer: 503). */
    NO_DATA,

    /** The backend can't be reached. */
    NETWORK,
}

sealed interface OptionsUi {
    data object Idle : OptionsUi

    data class Loading(
        val query: PlanQuery,
    ) : OptionsUi

    data class Failed(
        val query: PlanQuery,
        val error: LoadError,
    ) : OptionsUi

    /**
     * @property routes community routes for from → to, best first.
     * @property tips transfer and disruption tips at the stations of the usual and picked trips.
     */
    data class Ready(
        val query: PlanQuery,
        val response: PlanResponse,
        val view: OptionsView,
        val routes: List<RankedRoute> = emptyList(),
        val tips: List<RankedTip> = emptyList(),
    ) : OptionsUi
}

/** The question being answered, whatever the state. */
val OptionsUi.query: PlanQuery?
    get() =
        when (this) {
            OptionsUi.Idle -> null
            is OptionsUi.Loading -> query
            is OptionsUi.Failed -> query
            is OptionsUi.Ready -> query
        }

/**
 * «Your options»: plans with the backend, then keeps the pick current. On every `state` event the advice is
 * recomputed locally ([planOptions]); on every `community` event routes and tips are fetched again.
 *
 * The trips themselves come from one `/plan` call, so they age while the clock runs (replay or live). The answer is
 * fetched again quietly, keeping the old one on screen, when the disruptions change (the backend's detour search
 * depends on them) and, for «leave now» searches, when [needsReplan] says the pick has left or the clock went back.
 * At most one such call runs per [replanGap] (real time), so a fast replay can't flood OJP.
 */
class OptionsState(
    private val api: AuswegApi,
    private val session: AppSession,
    private val scope: CoroutineScope,
    private val replanGap: Duration = REPLAN_GAP,
) {
    private val _ui = MutableStateFlow<OptionsUi>(OptionsUi.Idle)
    val ui: StateFlow<OptionsUi> = _ui.asStateFlow()
    private var job: Job? = null
    private var language: String? = null

    /** The backend clock when the shown answer was asked for. */
    private var plannedAt: Instant? = null

    /** False after a fresh answer was already stale (e.g. a recorded one): replanning on the clock wouldn't help. */
    private var followClock = true
    private var replanJob: Job? = null
    private var replanAgain = false

    init {
        scope.launch {
            session.disruptions.drop(1).collect {
                recompute(it)
                requestReplan()
            }
        }
        scope.launch { session.community.drop(1).collect { refreshCommunity() } }
        scope.launch {
            session.now.collect { now ->
                if (now != null && replanJob?.isActive != true && isStale(now)) requestReplan()
            }
        }
    }

    /** Searches [query]; [language] is the UI language, for the texts of live disruptions. */
    fun search(
        query: PlanQuery,
        language: String? = null,
    ) {
        job?.cancel()
        replanJob?.cancel()
        replanAgain = false
        this.language = language
        _ui.value = OptionsUi.Loading(query)
        job =
            scope.launch {
                val plannedAt = session.currentNow()
                attempt { api.plan(query.toRequest(language)) }
                    .onSuccess { response ->
                        if (response.trips.isEmpty()) {
                            _ui.value = OptionsUi.Failed(query, LoadError.NO_CONNECTIONS)
                        } else {
                            show(query, response, plannedAt)
                        }
                    }.onFailure { _ui.value = OptionsUi.Failed(query, it.toLoadError()) }
            }
    }

    /** Shows [response], asked for at [plannedAt], as the answer to [query]; routes and tips stay until refreshed. */
    private fun show(
        query: PlanQuery,
        response: PlanResponse,
        plannedAt: Instant,
    ) {
        val disruptions = session.disruptionsFor(response.disruptions)
        val view = planOptions(response.trips, disruptions, session.currentNow())
        val old = _ui.value as? OptionsUi.Ready
        _ui.value =
            if (old != null && old.query == query) {
                old.copy(response = response, view = view)
            } else {
                OptionsUi.Ready(query, response, view)
            }
        this.plannedAt = plannedAt
        followClock = query.departAt == null && !needsReplan(view, plannedAt, plannedAt)
        refreshCommunity()
    }

    private fun isStale(now: Instant): Boolean {
        val ready = _ui.value as? OptionsUi.Ready
        val at = plannedAt
        return followClock && ready != null && at != null && needsReplan(ready.view, at, now)
    }

    /**
     * Fetches the shown answer again without leaving [OptionsUi.Ready]; keeps the old one if that fails or finds
     * nothing. Requests during a call or its [replanGap] are merged into one more call after it.
     */
    private fun requestReplan() {
        if (_ui.value !is OptionsUi.Ready) return
        if (replanJob?.isActive == true) {
            replanAgain = true
            return
        }
        replanJob =
            scope.launch {
                do {
                    replanAgain = false
                    val ready = _ui.value as? OptionsUi.Ready ?: break
                    val plannedAt = session.currentNow()
                    attempt { api.plan(ready.query.toRequest(language)) }.onSuccess { response ->
                        val current = _ui.value
                        if (response.trips.isNotEmpty() && current is OptionsUi.Ready && current.query == ready.query) {
                            show(ready.query, response, plannedAt)
                        }
                    }
                    delay(replanGap)
                } while (replanAgain)
            }
    }

    fun retry() {
        when (val s = _ui.value) {
            is OptionsUi.Failed -> search(s.query, language)
            is OptionsUi.Ready -> search(s.query, language)
            else -> Unit
        }
    }

    private fun recompute(disruptions: List<Disruption>) =
        _ui.update { s ->
            if (s is OptionsUi.Ready) {
                s.copy(view = planOptions(s.response.trips, disruptions, session.currentNow()))
            } else {
                s
            }
        }

    /**
     * Fetches routes and tips for the current answer; keeps the old ones if that fails. Routes need both DIDOK
     * numbers: `/routes` treats a missing one as «any», which would list routes for other stations.
     */
    fun refreshCommunity() {
        val ready = _ui.value as? OptionsUi.Ready ?: return
        val from = ready.query.from.didok
        val to = ready.query.to.didok
        scope.launch {
            val routes =
                if (from == null || to == null) emptyList() else attempt { api.routes(from, to) }.getOrNull()
            val trips = listOfNotNull(ready.view.usual, ready.view.pick?.trip)
            val tips = attempt { api.tips(stations = trips.stations()) }.getOrNull()
            _ui.update { s ->
                if (s is OptionsUi.Ready && s.query == ready.query) {
                    s.copy(routes = routes ?: s.routes, tips = tips ?: s.tips)
                } else {
                    s
                }
            }
        }
    }
}

/** At most one automatic re-plan per this much real time. */
private val REPLAN_GAP: Duration = 5.seconds

private fun PlanQuery.toRequest(language: String?): PlanRequest =
    PlanRequest(from.planRef(), to.planRef(), departAt, language = language)

/** What `/plan` takes for a stop: its DIDOK number when known, else its OJP ref. */
fun StopRef.planRef(): String = didok?.toString() ?: ref

/** DIDOK numbers of every stop on these trips. */
fun List<Trip>.stations(): Set<Int> =
    flatMap { t -> t.legs.flatMap { l -> (listOf(l.from) + l.intermediate + l.to).mapNotNull { it.stop.didok } } }
        .toSet()

private fun Throwable.toLoadError(): LoadError =
    if (this is ResponseException && response.status == HttpStatusCode.ServiceUnavailable) {
        LoadError.NO_DATA
    } else {
        LoadError.NETWORK
    }
