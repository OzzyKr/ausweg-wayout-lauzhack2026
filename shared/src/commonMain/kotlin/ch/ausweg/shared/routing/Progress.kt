package ch.ausweg.shared.routing

import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.StripChange
import kotlin.time.Instant

/** Where the traveller is on a taken trip. */
enum class Stage { BEFORE_DEPARTURE, ON_THE_WAY, ARRIVED, UNKNOWN }

/**
 * Progress on the live trip screen at [now] (backend clock).
 *
 * @property currentLeg the ride the traveller is on or waiting for (highlighted red on the strip).
 * @property nextChange the next change after [currentLeg], null on the last ride.
 * @property delayMinutes realtime delay of [currentLeg]; null when there are no times (community routes).
 */
data class Progress(
    val stage: Stage,
    val currentLeg: Int,
    val nextChange: StripChange?,
    val delayMinutes: Int?,
)

/** Pure: the current ride is the first one that hasn't arrived yet at [now]. Unknown without a final arrival. */
fun progress(
    strip: LegStrip,
    now: Instant,
): Progress {
    // A ride without an arrival time ends when the next one leaves.
    val ends = strip.legs.mapIndexed { i, leg -> leg.arrival ?: strip.legs.getOrNull(i + 1)?.departure }
    val current = ends.indexOfFirst { end -> end?.let { now < it } == true }
    val first = strip.legs.firstOrNull()?.departure
    return when {
        strip.legs.isEmpty() -> Progress(Stage.UNKNOWN, 0, null, null)
        ends.last() == null -> Progress(Stage.UNKNOWN, 0, strip.changes.firstOrNull(), null)
        current < 0 -> Progress(Stage.ARRIVED, strip.legs.lastIndex, null, null)
        else ->
            Progress(
                stage = if (first != null && now < first) Stage.BEFORE_DEPARTURE else Stage.ON_THE_WAY,
                currentLeg = current,
                nextChange = strip.changes.firstOrNull { it.afterLeg >= current },
                delayMinutes = strip.legs[current].delayMinutes,
            )
    }
}
