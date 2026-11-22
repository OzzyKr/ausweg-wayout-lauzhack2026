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
import kotlinx.serialization.json.Json

/**
 * This device's votes. The backend keeps one vote per voter and target (a new one replaces the old). Voting the
 * value you already gave takes the vote back. [mine] highlights the buttons; it is kept in [Settings], so a vote
 * from before a reload can still be seen and taken back.
 */
class Voting(
    private val api: AuswegApi,
    private val settings: Settings,
) {
    private val voterId = settings.voterId()
    private val _mine = MutableStateFlow(load())
    val mine: StateFlow<Map<String, VoteValue>> = _mine.asStateFlow()

    /** Votes [value] on a route, or takes the vote back if it was [value] already. The route after, null on failure. */
    suspend fun route(
        id: String,
        value: VoteValue,
    ): CommunityRoute? = cast(id, value) { api.voteRoute(id, VoteRequest(voterId, it)) }

    /** Votes [value] on a tip, or takes the vote back if it was [value] already. The updated tip, null on failure. */
    suspend fun tip(
        id: String,
        value: VoteValue,
    ): Tip? = cast(id, value) { api.voteTip(id, VoteRequest(voterId, it)) }

    private suspend fun <T> cast(
        id: String,
        value: VoteValue,
        call: suspend (VoteValue?) -> T,
    ): T? {
        val next = value.takeUnless { _mine.value[id] == value }
        return attempt { call(next) }.getOrNull()?.also {
            _mine.update { m -> if (next == null) m - id else m + (id to next) }
            settings[SettingKeys.MY_VOTES] = Json.encodeToString(_mine.value)
        }
    }

    private fun load(): Map<String, VoteValue> =
        settings[SettingKeys.MY_VOTES]
            ?.let { runCatching { Json.decodeFromString<Map<String, VoteValue>>(it) }.getOrNull() }
            .orEmpty()
}
