package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.StopRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A from / to field: what's typed, what's chosen, and the stop suggestions for the typed text. */
data class StopInput(
    val query: String = "",
    val selected: StopRef? = null,
    val suggestions: List<Location> = emptyList(),
)

enum class StopField { FROM, TO }

/** The planner's question, ready for `POST /plan`. [departAt] null = leave now (backend clock). */
data class PlanQuery(
    val from: StopRef,
    val to: StopRef,
    val departAt: Instant? = null,
)

/**
 * The plan screen.
 *
 * @property prefilled true while the fields hold the replayed scenario's suggested stops (and nothing the user typed),
 *   so a new scenario may replace them.
 */
data class PlanUi(
    val from: StopInput = StopInput(),
    val to: StopInput = StopInput(),
    val departAt: Instant? = null,
    val prefilled: Boolean = false,
) {
    val query: PlanQuery?
        get() {
            val f = from.selected
            val t = to.selected
            return if (f == null || t == null || f.ref == t.ref) null else PlanQuery(f, t, departAt)
        }
}

/** Shortest text worth sending to `/stops`. */
const val MIN_STOP_QUERY = 2

/** Plan screen state: stop search with debounced suggestions, swap, departure time, scenario prefill. */
class PlanState(
    private val api: AuswegApi,
    private val scope: CoroutineScope,
    private val debounce: Duration = 300.milliseconds,
) {
    private val _ui = MutableStateFlow(PlanUi())
    val ui: StateFlow<PlanUi> = _ui.asStateFlow()
    private val searches = mutableMapOf<StopField, Job>()

    /** The user typed [text] in [field]: forget the chosen stop and look up suggestions after a short pause. */
    fun type(
        field: StopField,
        text: String,
    ) {
        edit(field) { StopInput(query = text) }
        _ui.update { it.copy(prefilled = false) }
        searches[field]?.cancel()
        if (text.trim().length < MIN_STOP_QUERY) return
        searches[field] =
            scope.launch {
                delay(debounce)
                val found = attempt { api.stops(text.trim()) }.getOrDefault(emptyList())
                edit(field) { if (it.query == text) it.copy(suggestions = found) else it }
            }
    }

    fun choose(
        field: StopField,
        location: Location,
    ) {
        searches[field]?.cancel()
        edit(field) { StopInput(query = location.stop.name, selected = location.stop) }
        _ui.update { it.copy(prefilled = false) }
    }

    fun swap() = _ui.update { it.copy(from = it.to, to = it.from) }

    /** Null = leave now. */
    fun leaveAt(at: Instant?) = _ui.update { it.copy(departAt = at) }

    /**
     * Fills from / to with [scenario]'s suggested stops if the user hasn't typed anything of their own, and resets
     * the departure time (a time picked in another scenario makes no sense in this one).
     */
    fun prefill(scenario: Scenario?) {
        val s = _ui.value
        val untouched = s.prefilled || (s.from.query.isEmpty() && s.to.query.isEmpty())
        if (scenario == null || !untouched) return
        val from = scenario.stop(scenario.suggestedFrom, scenario.suggestedFromName)
        val to = scenario.stop(scenario.suggestedTo, scenario.suggestedToName)
        if (from == null || to == null) return
        _ui.value = PlanUi(StopInput(from.name, from), StopInput(to.name, to), departAt = null, prefilled = true)
    }

    private fun Scenario.stop(
        ref: String?,
        name: String?,
    ): StopRef? = if (ref != null && name != null) StopRef(ref, name, ref.toIntOrNull()) else null

    private fun edit(
        field: StopField,
        change: (StopInput) -> StopInput,
    ) = _ui.update {
        when (field) {
            StopField.FROM -> it.copy(from = change(it.from))
            StopField.TO -> it.copy(to = change(it.to))
        }
    }
}
