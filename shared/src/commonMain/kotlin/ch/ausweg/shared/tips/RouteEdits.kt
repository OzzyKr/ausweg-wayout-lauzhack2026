package ch.ausweg.shared.tips

import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.StopRef

/*
 * Editing the rides of a route on the share screen. Stops are numbered like ShareUi.stops: 0 is the start, i is
 * where ride i - 1 ends and ride i starts, size is the end. A ride whose stop changes loses its time and platform
 * there: they belonged to the old stop. All pure.
 */

/** Same stop: by DIDOK number when both have one, else by ref. */
fun StopRef.isSameStop(other: StopRef): Boolean =
    if (didok != null && other.didok != null) didok == other.didok else ref == other.ref

/**
 * Ride [leg] split at [stop]: the first part keeps the line and the departure, the second is a new train ride
 * without a line (the user fills it in).
 */
fun List<StripLeg>.withChangeAt(
    leg: Int,
    stop: StopRef,
): List<StripLeg> {
    val ride = getOrNull(leg) ?: return this
    val first = ride.copy(to = stop, arrival = null, arrivalPlatform = null, delayMinutes = 0)
    val second = StripLeg(label = null, kind = StripKind.TRAIN, from = stop, to = ride.to)
    return take(leg) + first + second + drop(leg + 1)
}

/** Stop [index] replaced by [stop]. */
fun List<StripLeg>.withStop(
    index: Int,
    stop: StopRef,
): List<StripLeg> =
    mapIndexed { i, ride ->
        when (i) {
            index - 1 -> ride.copy(to = stop, arrival = null, arrivalPlatform = null, delayMinutes = 0)
            index -> ride.copy(from = stop, departure = null, departurePlatform = null, delayMinutes = 0)
            else -> ride
        }
    }

/**
 * The change at stop [index] left out: the rides before and after it become one, with the first one's line (or the
 * second's if the first has none). The start and the end can't be left out, only replaced.
 */
fun List<StripLeg>.withoutChange(index: Int): List<StripLeg> {
    if (index !in 1..lastIndex) return this
    val a = this[index - 1]
    val b = this[index]
    val merged = a.copy(to = b.to, arrival = b.arrival, arrivalPlatform = b.arrivalPlatform, label = a.label ?: b.label)
    return take(index - 1) + merged + drop(index + 1)
}

/** Ride [leg] as a train or a bus (the share screen's toggle). */
fun List<StripLeg>.withBus(
    leg: Int,
    bus: Boolean,
): List<StripLeg> =
    mapIndexed { i, ride ->
        when {
            i != leg -> ride
            bus -> ride.copy(kind = StripKind.BUS, mode = LegMode.BUS)
            else -> ride.copy(kind = StripKind.TRAIN, mode = LegMode.RAIL)
        }
    }

/** True for the rides the share screen shows as «Bus». */
val StripLeg.isBus: Boolean get() = kind == StripKind.BUS || kind == StripKind.REPLACEMENT_BUS
