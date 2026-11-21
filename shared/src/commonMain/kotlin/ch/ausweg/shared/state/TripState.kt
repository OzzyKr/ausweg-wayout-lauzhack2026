package ch.ausweg.shared.state

import ch.ausweg.shared.disruption.RouteCheck
import ch.ausweg.shared.disruption.RouteUpdate
import ch.ausweg.shared.disruption.advise
import ch.ausweg.shared.disruption.affects
import ch.ausweg.shared.disruption.routeUpdate
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.TimelineEntry
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.routing.Progress
import ch.ausweg.shared.routing.progress
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
import kotlin.time.Instant

/** The trip the user is on: an OJP trip (with times) or a community route (without). */
sealed interface Taken {
    val strip: LegStrip

    /** [alternatives]: the other trips of the same search, so the advice can still suggest a detour. */
    data class Planned(
        val trip: Trip,
        val alternatives: List<Trip>,
    ) : Taken {
        override val strip: LegStrip get() = legStrip(trip)
    }

    data class Shared(
        val route: RankedRoute,
    ) : Taken {
        override val strip: LegStrip get() = legStrip(route.route, route.notes)
    }
}

/**
 * The disruption-watching part of a taken trip; changes only when disruptions do.
 *
 * @property planDisruptions the disruptions of the `/plan` answer the trip came from. Used in live mode only, while
 *   the backend pushes none: OJP disruptions exist only in `/plan` answers, not in `/state`.
 * @property nextUpdateId the [RouteUpdate.id] of the next banner.
 */
data class TripWatch(
    val taken: Taken,
    val watched: Set<String>,
    val advice: Advice?,
    val disruptions: List<Disruption>,
    val updates: List<RouteUpdate> = emptyList(),
    val planDisruptions: List<Disruption> = emptyList(),
    val nextUpdateId: Long = 1,
)

/**
 * The live trip screen.
 *
 * @property updates update banners, newest first.
 * @property timeline what was learned about the watched disruptions, newest first.
 */
data class TripUi(
    val taken: Taken,
    val strip: LegStrip,
    val advice: Advice?,
    val progress: Progress,
    val updates: List<RouteUpdate>,
    val timeline: List<TimelineEntry>,
    val watched: Set<String>,
) {
    val from: StopRef? get() = strip.from
    val to: StopRef? get() = strip.to
}

/**
 * The taken trip, watched against every new disruption state: the advice is re-run and [routeUpdate] decides
 * whether it's worth a banner.
 */
class TripState(
    private val session: AppSession,
    private val scope: CoroutineScope,
) {
    private val watch = MutableStateFlow<TripWatch?>(null)

    val ui: StateFlow<TripUi?> =
        combine(watch, session.now, session.timeline) { w, now, timeline ->
            w?.let {
                val strip = it.taken.strip
                TripUi(
                    taken = it.taken,
                    strip = strip,
                    advice = it.advice,
                    progress = progress(strip, now ?: session.currentNow()),
                    updates = it.updates,
                    timeline = timeline.filter { e -> e.disruptionId in it.watched }.sortedByDescending { e -> e.t },
                    watched = it.watched,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { session.disruptions.collect { onDisruptions(it) } }
    }

    /**
     * Takes [trip]. [context] are the disruptions that blocked the usual route, watched for banners too.
     * [planDisruptions] are those of the `/plan` answer, used in live mode while the backend pushes none.
     */
    fun take(
        trip: Trip,
        alternatives: List<Trip>,
        context: List<Disruption>,
        planDisruptions: List<Disruption> = emptyList(),
    ) {
        val disruptions = session.disruptionsFor(planDisruptions)
        val impact = affects(trip, disruptions)
        watch.value =
            TripWatch(
                taken = Taken.Planned(trip, alternatives),
                watched = (context + impact.blocking + impact.warnings).map { it.id }.toSet(),
                advice = advise(trip, disruptions, session.currentNow(), alternatives),
                disruptions = disruptions,
                planDisruptions = planDisruptions,
            )
    }

    /** Takes a community route; without times only the [context] disruptions are watched. */
    fun take(
        route: RankedRoute,
        context: List<Disruption>,
    ) {
        val ids = (context.map { it.id } + listOfNotNull(route.route.disruptionId)).toSet()
        watch.value = TripWatch(Taken.Shared(route), ids, null, session.disruptions.value)
    }

    fun dismiss(update: RouteUpdate) = watch.update { w -> w?.copy(updates = w.updates - update) }

    fun end() {
        watch.value = null
    }

    private fun onDisruptions(disruptions: List<Disruption>) =
        watch.update { w -> w?.let { next(it, disruptions, session.currentNow(), session.isLive) } }
}

/**
 * [w] after a new disruption state at [now]: new advice, more watched disruptions, maybe one more banner. [live]:
 * the backend runs live, so an empty [pushed] falls back to the plan answer's disruptions.
 */
internal fun next(
    w: TripWatch,
    pushed: List<Disruption>,
    now: Instant,
    live: Boolean = false,
): TripWatch {
    val disruptions = currentDisruptions(pushed, w.planDisruptions, live)
    val taken = w.taken
    val (advice, watched) =
        when (taken) {
            is Taken.Planned -> {
                val impact = affects(taken.trip, disruptions)
                advise(taken.trip, disruptions, now, taken.alternatives) to
                    w.watched + (impact.blocking + impact.warnings).map { it.id }
            }
            is Taken.Shared -> null to w.watched
        }
    val update =
        routeUpdate(RouteCheck(w.advice, w.disruptions), RouteCheck(advice, disruptions), watched, now)
            ?.copy(id = w.nextUpdateId)
    return w.copy(
        watched = watched,
        advice = advice,
        disruptions = disruptions,
        updates = listOfNotNull(update) + w.updates,
        nextUpdateId = if (update != null) w.nextUpdateId + 1 else w.nextUpdateId,
    )
}
