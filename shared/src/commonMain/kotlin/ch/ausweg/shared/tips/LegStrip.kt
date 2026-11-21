package ch.ausweg.shared.tips

import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.Trip
import kotlin.time.Instant

/** How a strip rectangle is drawn: solid for trains, outlined for buses, dashed for replacement buses. */
enum class StripKind { TRAIN, BUS, REPLACEMENT_BUS, OTHER }

/**
 * One rectangle of the leg strip: one ride. Walks are not drawn; they are part of the change.
 *
 * @property label the line (`IC5`, `S30`), or null for a bus without one (the UI shows «Bus»).
 * @property mode the OJP mode, kept so a shared route stores trams, metros and boats as they are ([kind] only
 *   tells how to draw it).
 * @property delayMinutes realtime delay at departure or arrival, whichever is larger; 0 when on time or unknown.
 */
data class StripLeg(
    val label: String?,
    val kind: StripKind,
    val from: StopRef,
    val to: StopRef,
    val departure: Instant? = null,
    val arrival: Instant? = null,
    val departurePlatform: String? = null,
    val arrivalPlatform: String? = null,
    val direction: String? = null,
    val delayMinutes: Int = 0,
    val cancelled: Boolean = false,
    val mode: LegMode = kind.defaultMode(),
) {
    /** Riding time, if both times are known (the strip draws longer rides wider). */
    val minutes: Int? get() = minutesBetween(departure, arrival)
}

/** The mode a [StripKind] stands for when nothing more precise is known. */
private fun StripKind.defaultMode(): LegMode =
    when (this) {
        StripKind.TRAIN -> LegMode.RAIL
        StripKind.BUS, StripKind.REPLACEMENT_BUS -> LegMode.BUS
        StripKind.OTHER -> LegMode.OTHER
    }

/**
 * A change between ride [afterLeg] and the next one, where notes are pinned.
 *
 * @property minutes time to change (next departure − this arrival), if both are known.
 * @property notes the notes pinned here, with their number on the strip (1, 2, …, in route order).
 */
data class StripChange(
    val afterLeg: Int,
    val station: StopRef,
    val arrivalPlatform: String?,
    val departurePlatform: String?,
    val minutes: Int? = null,
    val notes: List<NumberedNote> = emptyList(),
)

data class NumberedNote(
    val number: Int,
    val tip: Tip,
)

/** A trip or community route as the strip and the stop list draw it. */
data class LegStrip(
    val legs: List<StripLeg>,
    val changes: List<StripChange>,
) {
    val departure: Instant? get() = legs.firstOrNull()?.departure
    val arrival: Instant? get() = legs.lastOrNull()?.arrival
    val from: StopRef? get() = legs.firstOrNull()?.from
    val to: StopRef? get() = legs.lastOrNull()?.to

    /** Every note, in strip order. */
    val notes: List<NumberedNote> get() = changes.flatMap { it.notes }
}

/**
 * The strip of an OJP trip, with [notes] pinned by [Tip.legIndex] (index among the rides, walks skipped). A first
 * departure or last arrival OJP left out comes from the trip itself.
 */
fun legStrip(
    trip: Trip,
    notes: List<Tip> = emptyList(),
): LegStrip {
    val rides =
        trip.legs
            .filter { it.mode != LegMode.WALK }
            .map { it.toStripLeg() }
            .toMutableList()
    if (rides.isNotEmpty()) {
        rides[0] = rides[0].let { it.copy(departure = it.departure ?: trip.departure) }
        rides[rides.lastIndex] = rides.last().let { it.copy(arrival = it.arrival ?: trip.arrival) }
    }
    return stripOf(rides, notes)
}

/** The strip of a community route with its [notes]. Routes have no times. */
fun legStrip(
    route: CommunityRoute,
    notes: List<Tip> = emptyList(),
): LegStrip =
    stripOf(
        route.legs.map {
            StripLeg(
                label = it.line,
                kind = it.mode.kind(),
                from = it.from,
                to = it.to,
                departurePlatform = it.departurePlatform,
                arrivalPlatform = it.arrivalPlatform,
                mode = it.mode,
            )
        },
        notes,
    )

/**
 * The strip for a replacement-bus pick: one dashed bus rectangle. Station names come from [trip]'s stops when the
 * bus stations are on it; otherwise the stop's ref is its DIDOK number, its name is empty and the UI shows only «Bus».
 */
fun busStrip(
    bus: ReplacementBus,
    trip: Trip,
): LegStrip {
    val stops = trip.legs.flatMap { listOf(it.from) + it.intermediate + it.to }.map { it.stop }

    fun stop(didok: Int?): StopRef =
        stops.firstOrNull { didok != null && it.didok == didok }
            ?: StopRef(ref = didok?.toString().orEmpty(), name = "", didok = didok)
    return LegStrip(
        legs =
            listOf(
                StripLeg(
                    label = null,
                    kind = StripKind.REPLACEMENT_BUS,
                    from = stop(bus.from),
                    to = stop(bus.to),
                    departure = bus.eta,
                ),
            ),
        changes = emptyList(),
    )
}

/**
 * A strip of [legs] with their changes, [notes] pinned by [Tip.legIndex]. Notes that aren't on a change (after the
 * last ride, or out of range) are left out before numbering, so the numbers on the strip have no gaps.
 */
fun stripOf(
    legs: List<StripLeg>,
    notes: List<Tip>,
): LegStrip {
    val sorted =
        notes
            .filter { it.legIndex != null && it.legIndex in 0 until legs.size - 1 }
            .sortedWith(compareBy({ it.legIndex }, { it.createdAt }))
    val numbered = sorted.mapIndexed { i, tip -> NumberedNote(i + 1, tip) }
    val changes =
        legs.zipWithNext().mapIndexed { i, (a, b) ->
            StripChange(
                afterLeg = i,
                station = a.to,
                arrivalPlatform = a.arrivalPlatform,
                departurePlatform = b.departurePlatform,
                minutes = minutesBetween(a.arrival, b.departure),
                notes = numbered.filter { it.tip.legIndex == i },
            )
        }
    return LegStrip(legs, changes)
}

private fun minutesBetween(
    a: Instant?,
    b: Instant?,
): Int? = if (a != null && b != null) (b - a).inWholeMinutes.toInt() else null

private fun Leg.toStripLeg(): StripLeg {
    val dep = from.departure ?: from.arrival
    val arr = to.arrival ?: to.departure
    return StripLeg(
        label = line,
        kind = mode.kind(),
        from = from.stop,
        to = to.stop,
        departure = dep?.let { it.estimated ?: it.timetabled },
        arrival = arr?.let { it.estimated ?: it.timetabled },
        departurePlatform = from.platform,
        arrivalPlatform = to.platform,
        direction = direction,
        delayMinutes = maxOf(dep.delayMinutes(), arr.delayMinutes()),
        cancelled = cancelled || from.notServiced || to.notServiced,
        mode = mode,
    )
}

private fun CallTime?.delayMinutes(): Int {
    val est = this?.estimated ?: return 0
    return (est - timetabled).inWholeMinutes.toInt().coerceAtLeast(0)
}

private fun LegMode.kind(): StripKind =
    when (this) {
        LegMode.RAIL -> StripKind.TRAIN
        LegMode.BUS -> StripKind.BUS
        else -> StripKind.OTHER
    }
