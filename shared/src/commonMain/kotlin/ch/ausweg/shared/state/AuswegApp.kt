package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.disruption.sortForDisplay
import ch.ausweg.shared.i18n.nextLanguage
import ch.ausweg.shared.i18n.pickLanguage
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A disruption in the alerts list. [affectsYou]: it blocks the planned usual route or touches the taken trip. */
data class AlertItem(
    val disruption: Disruption,
    val affectsYou: Boolean,
)

/**
 * The whole app's state, one per process. Screens read the holders' `StateFlow`s and call the methods here for
 * anything that spans screens (plan → options → trip, share, back).
 */
class AuswegApp(
    api: AuswegApi,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    val session = AppSession(api, scope)
    val navigator = Navigator()
    val voting = Voting(api, settings.voterId())
    val plan = PlanState(api, scope)
    val options = OptionsState(api, session, scope)
    val route = RouteState(voting, scope)
    val share = ShareState(api, scope)
    val trip = TripState(session, scope)
    val community = CommunityState(api, session, voting, scope)

    private val _language = MutableStateFlow(settings[SettingKeys.LANGUAGE])

    /** The UI language; null until [initLanguage]. */
    val language: StateFlow<String?> = _language.asStateFlow()

    /** Disruptions known now, active and blocking first, flagged when they concern the user. */
    val alerts: StateFlow<List<AlertItem>> =
        combine(session.disruptions, options.ui, trip.ui) { disruptions, opts, taken ->
            val mine =
                (opts as? OptionsUi.Ready)
                    ?.view
                    ?.blockedBy
                    .orEmpty()
                    .map { it.id }
                    .toSet() +
                    taken?.watched.orEmpty()
            sortForDisplay(disruptions).map { AlertItem(it, it.id in mine) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun start() {
        session.start()
        scope.launch { session.activeScenario.collect { plan.prefill(it) } }
        scope.launch { options.ui.collect { (it as? OptionsUi.Ready)?.let { r -> route.sync(r.routes) } } }
    }

    /** Picks the saved language, else the device's, else English, among [available]. */
    fun initLanguage(
        available: List<String>,
        device: List<String>,
    ) {
        _language.value = pickLanguage(listOfNotNull(settings[SettingKeys.LANGUAGE]) + device, available)
    }

    fun cycleLanguage(available: List<String>) {
        val next = nextLanguage(_language.value ?: available.firstOrNull().orEmpty(), available)
        _language.value = next
        settings[SettingKeys.LANGUAGE] = next
    }

    /** «Find my way». */
    fun findMyWay() {
        val query = plan.ui.value.query ?: return
        options.search(query)
        navigator.push(Screen.Options)
    }

    fun openRoute(route: RankedRoute) {
        this.route.open(route)
        navigator.push(Screen.Route(route.route.id))
    }

    /** Takes the pick, or another trip from the options, and switches to the live trip tab. */
    fun takeTrip(trip: Trip) {
        val ready = options.ui.value as? OptionsUi.Ready
        val all = ready?.response?.trips.orEmpty()
        this.trip.take(
            trip,
            all.filter { it.id != trip.id },
            ready?.view?.blockedBy.orEmpty(),
            ready?.response?.disruptions.orEmpty(),
        )
        navigator.select(Tab.TRIPS)
    }

    /** «Take this route» on the open community route. */
    fun takeRoute() {
        val r = route.ui.value?.ranked ?: return
        val ready = options.ui.value as? OptionsUi.Ready
        trip.take(r, ready?.view?.blockedBy.orEmpty())
        navigator.select(Tab.TRIPS)
    }

    /**
     * «Share yours»: shares the taken trip if there is one, else the current pick ([shareSource]). «This disruption»
     * is offered when a disruption blocked the usual route and the shared route gets round it.
     */
    fun openShare() {
        val ready = options.ui.value as? OptionsUi.Ready
        val disruptions = session.disruptions.value.ifEmpty { ready?.response?.disruptions.orEmpty() }
        val source = shareSource(trip.ui.value?.taken, ready?.view?.pick, disruptions) ?: return
        val disruptionId =
            if (source.avoidsDisruptions) {
                ready
                    ?.view
                    ?.blockedBy
                    ?.firstOrNull()
                    ?.id
                    ?: trip.ui.value
                        ?.watched
                        ?.firstOrNull { id -> disruptions.any { it.id == id } }
            } else {
                null
            }
        share.start(source.strip.legs, disruptionId)
        navigator.push(Screen.Share)
    }

    /** Posts the shared route and goes back; the options list refreshes on the `community` event. */
    fun postShare() = share.post { navigator.back() }

    fun back(): Boolean = navigator.back()
}
