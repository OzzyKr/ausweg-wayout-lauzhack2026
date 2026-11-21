package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.VoteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The community tab: tips that apply now. [failed] when the last fetch failed (old tips stay). */
data class CommunityUi(
    val tips: List<RankedTip> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Tips for the community tab, best first (ranked by the backend with shared `rankTips`). Fetched again whenever
 * tips or votes change, or the set of disruptions does (a tip from 12 Sep shows again when the line closes again).
 */
class CommunityState(
    private val api: AuswegApi,
    private val session: AppSession,
    private val voting: Voting,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(CommunityUi())
    val ui: StateFlow<CommunityUi> = _ui.asStateFlow()

    init {
        scope.launch {
            combine(session.community, session.disruptions) { rev, ds -> rev to ds.map { it.id to it.status } }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun refresh() {
        scope.launch {
            attempt { api.tips() }
                .onSuccess { tips -> _ui.value = CommunityUi(tips, loading = false) }
                .onFailure { _ui.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun vote(
        tipId: String,
        value: VoteValue,
    ) {
        scope.launch {
            val updated = voting.tip(tipId, value) ?: return@launch
            _ui.update { s -> s.copy(tips = s.tips.map { if (it.tip.id == tipId) it.copy(tip = updated) else it }) }
        }
    }
}
