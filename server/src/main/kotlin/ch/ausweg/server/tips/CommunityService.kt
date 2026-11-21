package ch.ausweg.server.tips

import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.NewRoute
import ch.ausweg.shared.model.NewTip
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.VoteRequest
import ch.ausweg.shared.tips.rankRoutes
import ch.ausweg.shared.tips.rankTips
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Longest tip or note text. */
const val MAX_TEXT = 500

/** Most legs a shared route may have. */
const val MAX_LEGS = 12

private const val MAX_VOTER_ID = 64
private const val MAX_PLATFORM = 8
private val VOTER_ID = Regex("[A-Za-z0-9_-]{1,$MAX_VOTER_ID}")

/** A request the app shouldn't have sent (empty text, transfer tip without station…). Answered with 400. */
class InvalidRequest(
    message: String,
) : Exception(message)

/**
 * Community features over a [CommunityStore]: ranking with the shared `rankTips` / `rankRoutes` against the
 * disruptions known now ([state]), and creating tips and routes stamped with the backend clock (simulated in
 * historical mode). [revision] goes up on every change so SSE can tell the app to refetch.
 */
class CommunityService(
    private val store: CommunityStore,
    private val state: () -> StateResponse,
    private val staffToken: String? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val revisionFlow = MutableStateFlow(0L)
    val revision: StateFlow<Long> = revisionFlow.asStateFlow()

    /** Tips for now; [stations] limits transfer tips, [disruptionId] limits disruption tips to that disruption. */
    fun tips(
        stations: Set<Int>?,
        disruptionId: String?,
    ): List<RankedTip> {
        val now = state()
        val disruptions = now.disruptions.filter { disruptionId == null || it.id == disruptionId }
        return rankTips(store.tips(), disruptions, now.clock.now, stations)
    }

    /** Routes for now, optionally only those from [from] to [to] (DIDOK numbers). */
    fun routes(
        from: Int?,
        to: Int?,
    ): List<RankedRoute> {
        val now = state()
        val routes =
            store.routes().filter {
                (from == null || it.from.didok == from) &&
                    (to == null || it.to.didok == to)
            }
        return rankRoutes(routes, store.tips(), now.disruptions, now.clock.now)
    }

    /** @throws InvalidRequest */
    fun addTip(new: NewTip): Tip {
        val text = cleanText(new.text)
        if (new.kind == TipKind.TRANSFER && new.station == null) throw InvalidRequest("a transfer tip needs a station")
        val now = state()
        val disruption = new.disruptionId?.let { id -> now.disruptions.find { it.id == id } }
        val tip =
            Tip(
                id = newId(),
                kind = new.kind,
                text = text,
                createdAt = now.clock.now,
                station = new.station,
                platformFrom = new.platformFrom?.take(MAX_PLATFORM),
                platformTo = new.platformTo?.take(MAX_PLATFORM),
                sections = disruption?.sections.orEmpty(),
                reasonCode = disruption?.reasonCode,
                disruptionId = new.disruptionId,
            )
        store.addTip(tip)
        changed()
        return tip
    }

    /** Stores the route and its notes (as tips pinned on the route). @throws InvalidRequest */
    fun addRoute(new: NewRoute): CommunityRoute {
        if (new.legs.isEmpty() || new.legs.size > MAX_LEGS) throw InvalidRequest("a route needs 1 to $MAX_LEGS legs")
        val notes = new.notes.map { it.copy(text = cleanText(it.text)) }
        if (notes.any { it.legIndex !in 0 until new.legs.size - 1 }) throw InvalidRequest("notes go on a change")
        val now = state()
        val disruption = new.disruptionId?.let { id -> now.disruptions.find { it.id == id } }
        val route =
            CommunityRoute(
                id = newId(),
                from = new.legs.first().from,
                to = new.legs.last().to,
                legs = new.legs,
                createdAt = now.clock.now,
                permanent = new.permanent,
                sections = disruption?.sections.orEmpty(),
                reasonCode = disruption?.reasonCode,
                disruptionId = new.disruptionId,
            )
        store.addRoute(route)
        notes.forEach { note ->
            store.addTip(
                Tip(
                    id = newId(),
                    kind = if (new.permanent) TipKind.TRANSFER else TipKind.DISRUPTION,
                    text = note.text,
                    createdAt = now.clock.now,
                    station = new.legs[note.legIndex].to.didok,
                    sections = route.sections,
                    reasonCode = route.reasonCode,
                    disruptionId = route.disruptionId,
                    routeId = route.id,
                    legIndex = note.legIndex,
                ),
            )
        }
        changed()
        return route
    }

    /** The tip after the vote, or null if there's no such tip. @throws InvalidRequest */
    fun voteTip(
        id: String,
        vote: VoteRequest,
    ): Tip? = if (castVote(id, vote)) store.tips().find { it.id == id } else null

    /** The route after the vote, or null if there's no such route. @throws InvalidRequest */
    fun voteRoute(
        id: String,
        vote: VoteRequest,
    ): CommunityRoute? = if (castVote(id, vote)) store.routes().find { it.id == id } else null

    /** True if [token] is the configured staff token (no token configured: nobody is staff). */
    fun isStaff(token: String?): Boolean = staffToken != null && token == staffToken

    /** Endorses a tip or route; false if there's no such id. */
    fun endorse(id: String): Boolean = store.endorse(id).also { if (it) changed() }

    private fun castVote(
        id: String,
        vote: VoteRequest,
    ): Boolean {
        if (!VOTER_ID.matches(vote.voterId)) throw InvalidRequest("voterId: 1 to $MAX_VOTER_ID of A-Z a-z 0-9 _ -")
        return store.vote(id, vote.voterId, vote.value).also { if (it) changed() }
    }

    private fun changed() = revisionFlow.update { it + 1 }
}

/** Trimmed [text]. @throws InvalidRequest if empty or longer than [MAX_TEXT]. */
private fun cleanText(text: String): String {
    val t = text.trim()
    if (t.isEmpty() || t.length > MAX_TEXT) throw InvalidRequest("text must be 1 to $MAX_TEXT characters")
    return t
}
