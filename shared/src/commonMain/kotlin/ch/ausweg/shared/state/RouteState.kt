package ch.ausweg.shared.state

import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.legStrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The community route screen. [myVote] is this device's vote on it, if any. */
data class RouteUi(
    val ranked: RankedRoute,
    val strip: LegStrip,
    val myVote: VoteValue?,
)

/** One community route: its strip with numbered notes, and voting. */
class RouteState(
    private val voting: Voting,
    private val scope: CoroutineScope,
) {
    private val selected = MutableStateFlow<RankedRoute?>(null)

    val ui: StateFlow<RouteUi?> =
        combine(selected, voting.mine) { r, mine ->
            r?.let { RouteUi(it, legStrip(it.route, it.notes), mine[it.route.id]) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun open(route: RankedRoute) {
        selected.value = route
    }

    /** Keeps the open route current when the list it came from is fetched again. */
    fun sync(routes: List<RankedRoute>) =
        selected.update { cur -> cur?.let { c -> routes.firstOrNull { it.route.id == c.route.id } ?: c } }

    fun vote(value: VoteValue) {
        val id = selected.value?.route?.id ?: return
        scope.launch {
            val updated = voting.route(id, value) ?: return@launch
            selected.update { cur -> if (cur?.route?.id == id) cur.copy(route = updated) else cur }
        }
    }
}
