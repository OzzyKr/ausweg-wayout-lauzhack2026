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
import ch.ausweg.shared.routing.planOptions
import ch.ausweg.shared.routing.trip
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * «Your options»: plans with the backend, then keeps the pick current. On every `state` event the advice is
 * recomputed locally ([planOptions]); on every `community` event routes and tips are fetched again.
 */
class OptionsState(
    private val api: AuswegApi,
    private val session: AppSession,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow<OptionsUi>(OptionsUi.Idle)
    val ui: StateFlow<OptionsUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        scope.launch { session.disruptions.drop(1).collect { recompute(it) } }
        scope.launch { session.community.drop(1).collect { refreshCommunity() } }
    }

    fun search(query: PlanQuery) {
        job?.cancel()
        _ui.value = OptionsUi.Loading(query)
        job =
            scope.launch {
                val request = PlanRequest(query.from.planRef(), query.to.planRef(), query.departAt)
                attempt { api.plan(request) }
                    .onSuccess { response ->
                        if (response.trips.isEmpty()) {
                            _ui.value = OptionsUi.Failed(query, LoadError.NO_CONNECTIONS)
                        } else {
                            val disruptions = session.disruptionsFor(response.disruptions)
                            val view = planOptions(response.trips, disruptions, session.currentNow())
                            _ui.value = OptionsUi.Ready(query, response, view)
                            refreshCommunity()
                        }
                    }.onFailure { _ui.value = OptionsUi.Failed(query, it.toLoadError()) }
            }
    }

    fun retry() {
        when (val s = _ui.value) {
            is OptionsUi.Failed -> search(s.query)
            is OptionsUi.Ready -> search(s.query)
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
