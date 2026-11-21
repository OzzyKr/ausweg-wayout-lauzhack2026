package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.StopCall
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Trip
import kotlin.time.Instant

/**
 * Puidoux – Vauderens fire, 25 Sep 2026 (`data/scenarios/puidoux-vauderens_2026-09-25.events.json`).
 * Station numbers are the scenario's DIDOK numbers; trips and times are illustrative. Times are UTC.
 */
object Fx {
    const val LAUSANNE = 8501120
    const val PUIDOUX = 8504012
    const val PALEZIEUX = 8504014
    const val VAUDERENS = 8504021
    const val ROMONT = 8504023
    const val FRIBOURG = 8504100
    const val MOUDON = 8504120
    const val LUCENS = 8504121
    const val YVERDON = 8504200
    const val PAYERNE = 8504221

    fun t(hhmm: String): Instant = Instant.parse("2026-09-25T$hhmm:00Z")

    /** The PUBLIC 16:27 version: both sections closed until 18:00 «environ» (14:27Z / 16:00Z). */
    val fire =
        Disruption(
            id = "2195005195853",
            version = 2,
            publishedAt = t("14:27"),
            status = DisruptionStatus.ACTIVE,
            expectedEnd = t("16:00"),
            expectedEndType = EndType.APPROX,
            hints = listOf(DisruptionHints.LINE_CLOSED),
            sections = listOf(Section(PUIDOUX, VAUDERENS), Section(PUIDOUX, LUCENS)),
        )

    /** A stop call; `null` didok means OJP gave no station number. */
    fun call(
        didok: Int?,
        time: String? = null,
        notServiced: Boolean = false,
    ): StopCall =
        StopCall(
            stop = StopRef(ref = "ch:1:sloid:${didok ?: 0}", name = "$didok", didok = didok),
            departure = time?.let { CallTime(t(it)) },
            arrival = time?.let { CallTime(t(it)) },
            notServiced = notServiced,
        )

    fun leg(
        vararg stops: Int,
        dep: String,
        mode: LegMode = LegMode.RAIL,
        line: String? = "IC1",
    ): Leg =
        Leg(
            mode = mode,
            from = call(stops.first(), dep),
            to = call(stops.last()),
            line = line,
            intermediate = stops.drop(1).dropLast(1).map { call(it) },
        )

    fun trip(
        id: String,
        dep: String,
        arr: String,
        vararg legs: Leg,
    ): Trip = Trip(id, t(dep), t(arr), transfers = legs.size - 1, legs = legs.toList())

    /** IC1 Lausanne – Fribourg through the closed section, 14:45 – 15:30. */
    val ic1 =
        trip("ic1", "14:45", "15:30", leg(LAUSANNE, PUIDOUX, PALEZIEUX, VAUDERENS, ROMONT, FRIBOURG, dep = "14:45"))

    /** Detour via Yverdon and Payerne, avoiding both sections. */
    fun viaYverdon(
        dep: String,
        arr: String,
    ): Trip =
        trip(
            "yverdon-$dep",
            dep,
            arr,
            leg(LAUSANNE, YVERDON, dep = dep, line = "IC5"),
            leg(YVERDON, PAYERNE, FRIBOURG, dep = dep, line = "S30"),
        )

    /** Lausanne – Moudon – Lucens – Payerne: through Puidoux – Lucens, so blocked too. */
    val viaLucens =
        trip("lucens", "14:50", "15:50", leg(LAUSANNE, PUIDOUX, PALEZIEUX, MOUDON, LUCENS, PAYERNE, dep = "14:50"))
}
