package ch.ausweg.shared.tips

import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.Votes
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A staff endorsement counts like this many net upvotes. */
const val STAFF_BONUS = 5.0

/** «Still valid» / «outdated» votes count this much each (they judge a tip's age, not its quality). */
const val VALIDITY_WEIGHT = 0.5

/** A disruption tip's score halves every this long: old advice fades, permanent tips don't. */
val DISRUPTION_TIP_HALF_LIFE: Duration = 30.days

/**
 * Score of a tip or route: net votes, plus half the «still valid» minus «outdated» votes, plus [STAFF_BONUS] if
 * endorsed; times an age decay ([DISRUPTION_TIP_HALF_LIFE]) when [decays]. [now] is the backend clock's time.
 */
fun score(
    votes: Votes,
    staffEndorsed: Boolean,
    createdAt: Instant,
    now: Instant,
    decays: Boolean,
): Double {
    val base =
        (votes.up - votes.down) +
            VALIDITY_WEIGHT * (votes.stillValid - votes.outdated) +
            (if (staffEndorsed) STAFF_BONUS else 0.0)
    if (!decays) return base
    val age = (now - createdAt).coerceAtLeast(Duration.ZERO)
    return base * 0.5.pow(age / DISRUPTION_TIP_HALF_LIFE)
}

/**
 * Whether something written during a disruption ([disruptionId], [sections], [reasonCode]) applies to the
 * current [disruptions]: the same one is still active, or an active one is similar (shares a station with
 * [sections]; when both sides have a cause code, it must be the same). Null if neither.
 */
fun disruptionMatch(
    disruptionId: String?,
    sections: List<Section>,
    reasonCode: String?,
    disruptions: List<Disruption>,
): CommunityMatch? {
    val active = disruptions.filter { it.status == DisruptionStatus.ACTIVE }
    val mine = sections.stations()
    return when {
        disruptionId != null && active.any { it.id == disruptionId } -> CommunityMatch.SAME_DISRUPTION
        active.any { d ->
            (d.sections.stations() + d.affectedStops).any { it in mine } &&
                (reasonCode == null || d.reasonCode == null || reasonCode == d.reasonCode)
        } -> CommunityMatch.SIMILAR_DISRUPTION
        else -> null
    }
}

/**
 * Tips to show now, best first: disruption tips that match a current disruption ([disruptionMatch]) and transfer
 * tips (only at [stations], if given). Route notes ([Tip.routeId]) are left out: they come with their route.
 * Order: same disruption, then similar, then permanent; within each, by [score], then newest.
 */
fun rankTips(
    tips: List<Tip>,
    disruptions: List<Disruption>,
    now: Instant,
    stations: Set<Int>? = null,
): List<RankedTip> =
    tips
        .filter { it.routeId == null }
        .mapNotNull { tip ->
            val match =
                when (tip.kind) {
                    TipKind.TRANSFER ->
                        CommunityMatch.PERMANENT.takeIf { stations == null || tip.station in stations }
                    TipKind.DISRUPTION -> disruptionMatch(tip.disruptionId, tip.sections, tip.reasonCode, disruptions)
                }
            val decays = tip.kind == TipKind.DISRUPTION
            match?.let { RankedTip(tip, it, score(tip.votes, tip.staffEndorsed, tip.createdAt, now, decays)) }
        }.sortedWith(compareBy<RankedTip>({ it.match.ordinal }, { -it.score }, { -it.tip.createdAt.epochSeconds }))

/**
 * Routes to show now, best first: «always» routes, and the others when they match a current disruption. Each
 * comes with its notes (from [notes], by [Tip.routeId]) in leg order. Same order rules as [rankTips].
 */
fun rankRoutes(
    routes: List<CommunityRoute>,
    notes: List<Tip>,
    disruptions: List<Disruption>,
    now: Instant,
): List<RankedRoute> {
    val notesByRoute = notes.filter { it.routeId != null }.groupBy { it.routeId }
    return routes
        .mapNotNull { route ->
            val match =
                if (route.permanent) {
                    CommunityMatch.PERMANENT
                } else {
                    disruptionMatch(route.disruptionId, route.sections, route.reasonCode, disruptions)
                }
            match?.let {
                RankedRoute(
                    route = route,
                    notes = notesByRoute[route.id].orEmpty().sortedBy { n -> n.legIndex },
                    match = it,
                    score = score(route.votes, route.staffEndorsed, route.createdAt, now, decays = !route.permanent),
                )
            }
        }.sortedWith(
            compareBy<RankedRoute>({ it.match.ordinal }, { -it.score }, { -it.route.createdAt.epochSeconds }),
        )
}

private fun List<Section>.stations(): Set<Int> = flatMap { listOf(it.from, it.to) }.toSet()
