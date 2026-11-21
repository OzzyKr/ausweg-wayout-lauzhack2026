package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.NewNote
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.routing.Pick
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.StripLeg
import ch.ausweg.shared.tips.busStrip
import ch.ausweg.shared.tips.legStrip
import ch.ausweg.shared.tips.stripOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * The share screen.
 *
 * @property legs every ride of the trip being shared; [checked] says which ones the user took.
 * @property selectedChange the change (index into [strip]`.changes`) whose note is being edited.
 * @property notes note text per change index of [strip].
 * @property disruptionId the disruption the trip avoided, for «this disruption»; null if there's none.
 */
data class ShareUi(
    val legs: List<StripLeg> = emptyList(),
    val checked: List<Boolean> = emptyList(),
    val selectedChange: Int? = null,
    val notes: Map<Int, String> = emptyMap(),
    val scope: ShareScope = ShareScope.ALWAYS,
    val disruptionId: String? = null,
    val status: ShareStatus = ShareStatus.EDITING,
) {
    /** The strip of the checked rides, with their changes. */
    val strip: LegStrip get() = stripOf(legs.filterIndexed { i, _ -> checked.getOrElse(i) { false } }, emptyList())

    val canUseThisDisruption: Boolean get() = disruptionId != null

    /**
     * The checked rides follow each other: unchecking a ride in the middle would post a route with a gap (a «change»
     * at Yverdon that leaves from Payerne). Only rides at the start or end can be left out.
     */
    val checkedInOneGo: Boolean
        get() {
            val first = checked.indexOfFirst { it }
            val last = checked.indexOfLast { it }
            return first >= 0 && (first..last).all { checked[it] }
        }

    /** Some rides are checked, but not in one go: the share screen says why it can't post (`share.gap`). */
    val hasGap: Boolean get() = checked.any { it } && !checkedInOneGo

    val canPost: Boolean
        get() =
            status != ShareStatus.POSTING &&
                checkedInOneGo &&
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
                RouteLeg(it.from, it.to, it.label, it.mode, it.departurePlatform, it.arrivalPlatform)
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

/** «Share a route»: pick the rides you took, pin notes on the changes, choose when it's useful, post. */
class ShareState(
    private val api: AuswegApi,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()

    /** Starts sharing [legs] (all checked). With a [disruptionId], «this disruption» is preselected. */
    fun start(
        legs: List<StripLeg>,
        disruptionId: String?,
    ) {
        _ui.value =
            ShareUi(
                legs = legs,
                checked = legs.map { true },
                scope = if (disruptionId != null) ShareScope.THIS_DISRUPTION else ShareScope.ALWAYS,
                disruptionId = disruptionId,
            )
    }

    /** Checking or unchecking a ride changes the changes, so notes start over. */
    fun toggle(leg: Int) =
        _ui.update { s ->
            val checked = s.checked.mapIndexed { i, c -> if (i == leg) !c else c }
            s.copy(checked = checked, selectedChange = null, notes = emptyMap(), status = ShareStatus.EDITING)
        }

    fun selectChange(change: Int) = _ui.update { it.copy(selectedChange = change) }

    fun note(text: String) =
        _ui.update { s ->
            val change = s.selectedChange ?: return@update s
            s.copy(notes = s.notes + (change to text.take(NOTE_MAX_LENGTH)))
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
