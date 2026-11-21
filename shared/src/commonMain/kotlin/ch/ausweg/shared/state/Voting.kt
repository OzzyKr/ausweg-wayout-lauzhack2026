package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.VoteRequest
import ch.ausweg.shared.model.VoteValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * This device's votes. The backend keeps one vote per voter and target (a new one replaces the old), so voting the
 * same value twice is skipped. [mine] remembers the votes of this run, to highlight the buttons.
 */
class Voting(
    private val api: AuswegApi,
    private val voterId: String,
) {
    private val _mine = MutableStateFlow<Map<String, VoteValue>>(emptyMap())
    val mine: StateFlow<Map<String, VoteValue>> = _mine.asStateFlow()

    /** The updated route, or null if the vote was a repeat or failed. */
    suspend fun route(
        id: String,
        value: VoteValue,
    ): CommunityRoute? = cast(id, value) { api.voteRoute(id, VoteRequest(voterId, value)) }

    /** The updated tip, or null if the vote was a repeat or failed. */
    suspend fun tip(
        id: String,
        value: VoteValue,
    ): Tip? = cast(id, value) { api.voteTip(id, VoteRequest(voterId, value)) }

    private suspend fun <T> cast(
        id: String,
        value: VoteValue,
        call: suspend () -> T,
    ): T? {
        if (_mine.value[id] == value) return null
        return attempt { call() }.getOrNull()?.also { _mine.update { m -> m + (id to value) } }
    }
}
