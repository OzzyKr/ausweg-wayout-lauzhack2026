package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.disruption.stopsTrain
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.NewNote
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.routing.Pick
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.StripLeg
import ch.ausweg.shared.tips.busStrip
import ch.ausweg.shared.tips.isSameStop
import ch.ausweg.shared.tips.legStrip
import ch.ausweg.shared.tips.stripOf
import ch.ausweg.shared.tips.withBus
import ch.ausweg.shared.tips.withChangeAt
import ch.ausweg.shared.tips.withStop
import ch.ausweg.shared.tips.withoutChange
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

/** «When is this useful?» on the share screen. */
enum class ShareScope {
    /** Shown again when the same stretch closes again. Needs a current disruption. */
    THIS_DISRUPTION,

    /** A general tip for this from → to («always»). */
    ALWAYS,
}

enum class ShareStatus { EDITING, POSTING, POSTED, FAILED }

/** Longest note we accept. */
const val NOTE_MAX_LENGTH = 500

/** Longest line name («IR 15», «Bus 701»). */
const val LINE_MAX_LENGTH = 20

/** Most rides a shared route may have (the backend's limit). */
const val MAX_ROUTE_RIDES = 12

/** Which stop the share screen's stop search changes. */
sealed interface StopTarget {
    /** Stop [index] of [ShareUi.stops]. */
    data class Stop(
        val index: Int,
    ) : StopTarget

    /** A new change in the middle of ride [leg]. */
    data class NewChange(
        val leg: Int,
    ) : StopTarget
}

/** The open stop search: what it changes and what's typed. */
data class StopEdit(
    val target: StopTarget,
    val input: StopInput = StopInput(),
)

/**
 * The share screen.
 *
 * @property legs the rides of the route being shared: the suggested or taken trip at first, then as the user edits
 *   it (other stops, extra changes, line names).
 * @property editingRoute the route's edit controls (other stops, extra changes, line names) are shown.
 * @property editing the open stop search, if any.
 * @property notes note text per change index of [strip]; a change with an entry (even empty) shows its tip field.
 * @property disruption the disruption on this trip, for «this disruption»; null if there's none.
 * @property throughDisruption the route still goes by train through [disruption]: «this disruption» is off until the
 *   user edits it round the closure, or later travellers would be sent onto the closed line.
 */
data class ShareUi(
    val legs: List<StripLeg> = emptyList(),
    val editingRoute: Boolean = false,
    val editing: StopEdit? = null,
    val notes: Map<Int, String> = emptyMap(),
    val scope: ShareScope = ShareScope.ALWAYS,
    val disruption: Disruption? = null,
    val throughDisruption: Boolean = false,
    val status: ShareStatus = ShareStatus.EDITING,
) {
    val disruptionId: String? get() = disruption?.id

    /** Start, every change, end. */
    val stops: List<StopRef> get() = legs.firstOrNull()?.let { listOf(it.from) + legs.map { l -> l.to } }.orEmpty()

    /** The rides with their changes. */
    val strip: LegStrip get() = stripOf(legs, emptyList())

    val canUseThisDisruption: Boolean get() = disruption != null && !throughDisruption

    /** A ride ends where it starts (the same stop twice in a row): the share screen says why it can't post. */
    val hasLoop: Boolean get() = legs.any { it.from.isSameStop(it.to) }

    val canAddChange: Boolean get() = legs.size < MAX_ROUTE_RIDES

    /** The change (index into [strip]`.changes`) at stop [stop] of [stops]; null for the start, the end or no stop. */
    fun changeAt(stop: Int): Int? = (stop - 1).takeIf { stop in 1 until stops.lastIndex }

    val canPost: Boolean
        get() =
            status != ShareStatus.POSTING &&
                !editingRoute &&
                editing == null &&
                legs.size in 1..MAX_ROUTE_RIDES &&
                !hasLoop &&
                (scope == ShareScope.ALWAYS || canUseThisDisruption)
}

/**
 * What «Share yours» shares. [avoidsDisruptions] is false when the route goes through the disruption (a wait pick,
 * or a taken trip that is blocked): it must not be offered as «this disruption», or later travellers would be sent
 * onto the closed line.
 */
data class ShareSource(
    val strip: LegStrip,
    val avoidsDisruptions: Boolean,
)

/**
 * The [taken] trip if there is one, else the options [pick]: a bus pick shares the replacement bus, not the blocked
 * train it replaces. Null when there is nothing to share. Pure.
 */
fun shareSource(
    taken: Taken?,
    pick: Pick?,
    disruptions: List<Disruption>,
): ShareSource? =
    when {
        taken is Taken.Planned -> ShareSource(taken.strip, !affects(taken.trip, disruptions).isBlocked)
        taken is Taken.Shared -> ShareSource(taken.strip, avoidsDisruptions = true)
        pick is Pick.Bus -> ShareSource(busStrip(pick.bus, pick.trip), avoidsDisruptions = true)
        pick is Pick.Wait -> ShareSource(legStrip(pick.trip), avoidsDisruptions = false)
        pick is Pick.Go -> ShareSource(legStrip(pick.trip), avoidsDisruptions = true)
        else -> null
    }

/** The request for [ui], or null if it can't be posted. Notes are pinned after their ride (`legIndex`). */
fun newRoute(ui: ShareUi): NewRoute? {
    if (!ui.canPost) return null
    val strip = ui.strip
    return NewRoute(
        legs =
            strip.legs.map {
                RouteLeg(
                    it.from,
                    it.to,
                    it.label?.trim()?.ifEmpty { null },
                    it.mode,
                    it.departurePlatform,
                    it.arrivalPlatform,
                )
            },
        permanent = ui.scope == ShareScope.ALWAYS,
        disruptionId = ui.disruptionId.takeIf { ui.scope == ShareScope.THIS_DISRUPTION },
        notes =
            strip.changes.mapNotNull { c ->
                ui.notes[c.afterLeg]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { NewNote(c.afterLeg, it) }
            },
    )
}

/**
 * «Share your route»: starts from the suggested or taken trip; the user can change any stop, add or remove changes,
 * name the lines, pin notes on the changes, choose when it's useful and post.
 */
class ShareState(
    private val api: AuswegApi,
    private val scope: CoroutineScope,
    private val debounce: Duration = 300.milliseconds,
) {
    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()
    private var search: Job? = null

    /**
     * Starts sharing [legs] on a trip hit by [disruption]. [throughDisruption] says whether [legs] still go through it
     * (known best from the whole trip, so the caller tells); if not, «this disruption» is preselected.
     */
    fun start(
        legs: List<StripLeg>,
        disruption: Disruption?,
        throughDisruption: Boolean = false,
    ) {
        search?.cancel()
        val ui =
            ShareUi(
                legs = legs,
                disruption = disruption,
                throughDisruption =
                    disruption != null && throughDisruption,
            )
        _ui.value = ui.copy(scope = if (ui.canUseThisDisruption) ShareScope.THIS_DISRUPTION else ShareScope.ALWAYS)
    }

    /** Opens the stop search for [target] (an existing stop, or a new change), or closes it with null. */
    fun search(target: StopTarget?) {
        search?.cancel()
        _ui.update { s ->
            val valid =
                when (target) {
                    null -> true
                    is StopTarget.Stop -> target.index in s.stops.indices
                    is StopTarget.NewChange -> s.canAddChange && target.leg in s.legs.indices
                }
            if (valid) s.copy(editing = target?.let { StopEdit(it) }) else s
        }
    }

    /** The user typed [text] in the stop search: look up suggestions after a short pause. */
    fun typeStop(text: String) {
        if (_ui.value.editing == null) return
        _ui.update { it.withInput { StopInput(query = text) } }
        search?.cancel()
        if (text.trim().length < MIN_STOP_QUERY) return
        search =
            scope.launch {
                delay(debounce)
                val found = attempt { api.stops(text.trim()) }.getOrDefault(emptyList())
                _ui.update { s -> s.withInput { if (it.query == text) it.copy(suggestions = found) else it } }
            }
    }

    /** Puts [location] where the stop search says. */
    fun chooseStop(location: Location) {
        search?.cancel()
        _ui.update { s ->
            val edit = s.editing ?: return@update s
            val legs =
                when (val target = edit.target) {
                    is StopTarget.Stop -> s.legs.withStop(target.index, location.stop)
                    is StopTarget.NewChange -> s.legs.withChangeAt(target.leg, location.stop)
                }
            s.routeChanged(legs)
        }
    }

    /** Leaves out the change at stop [index] (not the start or end). */
    fun removeChange(index: Int) = _ui.update { it.routeChanged(it.legs.withoutChange(index)) }

    /** Ride [leg]'s [line] name, as typed, and/or whether it was a [bus]; null leaves that as it is. */
    fun ride(
        leg: Int,
        line: String? = null,
        bus: Boolean? = null,
    ) = _ui.update { s ->
        var legs = s.legs
        if (line !=
            null
        ) {
            legs = legs.mapIndexed { i, l -> if (i == leg) l.copy(label = line.take(LINE_MAX_LENGTH)) else l }
        }
        if (bus != null) legs = legs.withBus(leg, bus)
        s.withLegs(legs).copy(status = ShareStatus.EDITING)
    }

    /** Shows the route's edit controls, or hides them (and closes the stop search) with false. */
    fun editRoute(on: Boolean) {
        search?.cancel()
        _ui.update { it.copy(editingRoute = on, editing = null) }
    }

    /** The tip on [change] (index into [ShareUi.strip]`.changes`); «Add a tip» starts it empty. */
    fun note(
        change: Int,
        text: String,
    ) = _ui.update { s ->
        val at = change.takeIf { it in s.strip.changes.indices } ?: return@update s
        s.copy(notes = s.notes + (at to text.take(NOTE_MAX_LENGTH)))
    }

    fun scope(scope: ShareScope) = _ui.update { it.copy(scope = scope) }

    /** Posts the route; [onPosted] runs after the backend accepted it. */
    fun post(onPosted: () -> Unit = {}) {
        val request = newRoute(_ui.value) ?: return
        _ui.update { it.copy(status = ShareStatus.POSTING) }
        scope.launch {
            attempt { api.postRoute(request) }
                .onSuccess {
                    _ui.update { s -> s.copy(status = ShareStatus.POSTED) }
                    onPosted()
                }.onFailure { _ui.update { s -> s.copy(status = ShareStatus.FAILED) } }
        }
    }
}

private fun ShareUi.withInput(change: (StopInput) -> StopInput): ShareUi =
    editing?.let { copy(editing = it.copy(input = change(it.input))) } ?: this

/** The changes moved, so notes pinned on them start over. */
private fun ShareUi.routeChanged(legs: List<StripLeg>): ShareUi =
    withLegs(legs).copy(editing = null, notes = emptyMap(), status = ShareStatus.EDITING)

/**
 * The user edited the route to [legs]: does it still go by train through [ShareUi.disruption]? «This disruption» falls
 * back to «always» when it does.
 */
private fun ShareUi.withLegs(legs: List<StripLeg>): ShareUi {
    val d = disruption
    val through = d != null && legs.any { it.mode == LegMode.RAIL && d.stopsTrain(it.from.didok, it.to.didok) }
    val next = copy(legs = legs, throughDisruption = through)
    return if (next.scope == ShareScope.THIS_DISRUPTION && !next.canUseThisDisruption) {
        next.copy(scope = ShareScope.ALWAYS)
    } else {
        next
    }
}
