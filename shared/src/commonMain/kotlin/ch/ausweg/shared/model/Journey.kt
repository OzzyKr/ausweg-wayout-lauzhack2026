package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A stop as OJP names it.
 *
 * @property ref OJP reference, usually a SLOID like `ch:1:sloid:7000:4:7` (platform) or `ch:1:sloid:7000` (station).
 * @property didok station number (e.g. `8507000` for Bern), to match against the DIDOK numbers in SBB disruption data.
 */
@Serializable
data class StopRef(
    val ref: String,
    val name: String,
    val didok: Int? = null,
)

/** A scheduled time and, when realtime data has it, the expected one. */
@Serializable
data class CallTime(
    val timetabled: Instant,
    val estimated: Instant? = null,
)

/**
 * A leg stopping at a stop.
 *
 * @property platform the expected platform if it changed, otherwise the planned one.
 * @property notServiced the vehicle passes without stopping (e.g. a skipped stop during a disruption).
 */
@Serializable
data class StopCall(
    val stop: StopRef,
    val platform: String? = null,
    val arrival: CallTime? = null,
    val departure: CallTime? = null,
    val notServiced: Boolean = false,
)

@Serializable
enum class LegMode { RAIL, BUS, TRAM, METRO, WATER, CABLEWAY, WALK, OTHER }

/**
 * One part of a trip: a ride on one vehicle, or a walk ([LegMode.WALK], no line).
 *
 * @property intermediate stops between [from] and [to], needed to tell whether the leg passes an affected section.
 * @property situationIds ids of [Situation]s (disruption notices) OJP attached to this leg.
 */
@Serializable
data class Leg(
    val mode: LegMode,
    val from: StopCall,
    val to: StopCall,
    val line: String? = null,
    val direction: String? = null,
    val intermediate: List<StopCall> = emptyList(),
    val cancelled: Boolean = false,
    val situationIds: List<String> = emptyList(),
)

@Serializable
data class Trip(
    val id: String,
    val departure: Instant,
    val arrival: Instant,
    val transfers: Int,
    val legs: List<Leg>,
)
