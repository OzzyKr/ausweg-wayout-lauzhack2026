package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.NewNote
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.StripKind
import ch.ausweg.shared.tips.StripLeg
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

    val canPost: Boolean
        get() =
            status != ShareStatus.POSTING &&
                checked.any { it } &&
                (scope == ShareScope.ALWAYS || canUseThisDisruption)
}

/** The request for [ui], or null if it can't be posted. Notes are pinned after their ride (`legIndex`). */
fun newRoute(ui: ShareUi): NewRoute? {
    if (!ui.canPost) return null
    val strip = ui.strip
    return NewRoute(
        legs =
            strip.legs.map {
                RouteLeg(it.from, it.to, it.label, it.kind.mode(), it.departurePlatform, it.arrivalPlatform)
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

private fun StripKind.mode(): LegMode =
    when (this) {
        StripKind.TRAIN -> LegMode.RAIL
        StripKind.BUS, StripKind.REPLACEMENT_BUS -> LegMode.BUS
        StripKind.OTHER -> LegMode.OTHER
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
