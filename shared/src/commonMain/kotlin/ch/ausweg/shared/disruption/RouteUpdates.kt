package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import kotlin.time.Instant

/** What an update banner says. Each value is an i18n key (`update.<name>`). */
enum class UpdateKind {
    /** The taken trip was fine and now is blocked: see the new advice. */
    BLOCKED,

    /** Still blocked, but the advice changed (other detour, other wait time, bus instead of waiting…). */
    ADVICE_CHANGED,

    /** The taken trip was blocked and now gets through. */
    RESOLVED,

    /** A watched disruption now has (other) replacement buses; the taken trip is still the best. */
    BUS_RUNNING,

    /** A watched disruption ended; the taken trip still works. */
    LINE_REOPENED,

    /** A watched disruption's expected end moved; the taken trip still works. */
    END_CHANGED,
}

/**
 * One update banner on the live trip screen.
 *
 * @property disruption the latest version of the disruption it's about, if any.
 * @property advice the advice for the taken trip after the change (null for community routes, which have no times).
 * @property id unique among the banners of one taken trip, set by `TripState` (lists key on it: two banners can
 *   have the same kind, time and version after replay seeks).
 */
data class RouteUpdate(
    val kind: UpdateKind,
    val at: Instant,
    val disruption: Disruption?,
    val advice: Advice?,
    val id: Long = 0,
)

/** The advice for the taken trip under one disruption state, and that state. */
data class RouteCheck(
    val advice: Advice?,
    val disruptions: List<Disruption>,
)

/**
 * Whether a new disruption state deserves a banner for the taken trip: the spec's «only when the user's route or
 * ETA changes». Pure.
 *
 * - Advice changes come first: Ok → blocked ([UpdateKind.BLOCKED]), blocked → Ok ([UpdateKind.RESOLVED]), or a
 *   different blocked advice ([UpdateKind.ADVICE_CHANGED]: other kind, other `until`, other best detour or bus).
 * - Otherwise only [watched] disruptions count (the ones that blocked the usual route when the trip was taken, and
 *   the ones on the trip): ended or gone ([UpdateKind.LINE_REOPENED]), and while still active, new or changed bus
 *   ([UpdateKind.BUS_RUNNING]) or moved expected end ([UpdateKind.END_CHANGED]). New text alone gives no banner.
 *
 * Advice is null for routes without times; then only watched disruptions count.
 */
fun routeUpdate(
    before: RouteCheck,
    after: RouteCheck,
    watched: Set<String>,
    now: Instant,
): RouteUpdate? {
    val oldAdvice = before.advice
    val newAdvice = after.advice
    val adviceKind = adviceChange(oldAdvice, newAdvice)
    if (adviceKind != null) {
        val about = newAdvice?.blocking()?.firstOrNull() ?: oldAdvice?.blocking()?.firstOrNull()
        return RouteUpdate(adviceKind, now, about, newAdvice)
    }
    val old = before.disruptions.associateBy { it.id }
    val new = after.disruptions.associateBy { it.id }
    return watched.sorted().firstNotNullOfOrNull { id ->
        disruptionChange(old[id], new[id], now)?.let { (kind, d) -> RouteUpdate(kind, now, d, newAdvice) }
    }
}

private fun adviceChange(
    old: Advice?,
    new: Advice?,
): UpdateKind? =
    when {
        old == null || new == null -> null
        old is Advice.Ok && new !is Advice.Ok -> UpdateKind.BLOCKED
        old !is Advice.Ok && new is Advice.Ok -> UpdateKind.RESOLVED
        old !is Advice.Ok && old.key() != new.key() -> UpdateKind.ADVICE_CHANGED
        else -> null
    }

/** What the traveller would notice about an advice: its kind, reason, and the time or bus it names. */
private fun Advice.key(): List<Any?> =
    when (this) {
        is Advice.Ok -> listOf("ok")
        is Advice.Wait -> listOf("wait", reason, until)
        is Advice.Detour -> listOf("detour", reason, alternatives.firstOrNull()?.arrival)
        is Advice.TakeBus -> listOf("bus", bus)
    }

private fun Advice.blocking(): List<Disruption> =
    when (this) {
        is Advice.Ok -> emptyList()
        is Advice.Wait -> impact.blocking
        is Advice.Detour -> impact.blocking
        is Advice.TakeBus -> impact.blocking
    }

/**
 * A watched disruption that is gone counts as reopened only if [now] is after it was published: a replay seek back
 * to before it existed also removes it, and that's not news for the traveller. Bus and end changes only count
 * while it is still active.
 */
private fun disruptionChange(
    old: Disruption?,
    new: Disruption?,
    now: Instant,
): Pair<UpdateKind, Disruption>? {
    val wasActive = old?.status == DisruptionStatus.ACTIVE
    val isActive = new?.status == DisruptionStatus.ACTIVE
    return when {
        old == null -> null
        new == null -> if (wasActive && now >= old.publishedAt) UpdateKind.LINE_REOPENED to old else null
        wasActive && new.status == DisruptionStatus.ENDED -> UpdateKind.LINE_REOPENED to new
        !isActive -> null
        new.bus != null && new.bus != old.bus -> UpdateKind.BUS_RUNNING to new
        new.expectedEnd != old.expectedEnd -> UpdateKind.END_CHANGED to new
        else -> null
    }
}
