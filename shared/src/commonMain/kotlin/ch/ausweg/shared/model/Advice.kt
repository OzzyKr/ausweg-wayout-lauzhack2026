package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What in a trip is hit by disruptions. Result of `affects()`.
 *
 * @property blocking active disruptions with a [DisruptionHints.BLOCKING] hint that hit a leg.
 * @property warnings other active disruptions that hit a leg (shown, but the trip still works).
 * @property cancelledLegs indexes into `Trip.legs` of legs OJP marks as cancelled, or that skip
 *   their boarding or alighting stop.
 * @property skippedStops DIDOK numbers of intermediate stops a leg passes without stopping (a warning only).
 */
@Serializable
data class TripImpact(
    val blocking: List<Disruption> = emptyList(),
    val warnings: List<Disruption> = emptyList(),
    val cancelledLegs: List<Int> = emptyList(),
    val skippedStops: List<Int> = emptyList(),
) {
    /** True if the trip can't be travelled as planned. */
    val isBlocked: Boolean get() = blocking.isNotEmpty() || cancelledLegs.isNotEmpty()
}

/** Why an [Advice] was given. The UI turns each value into an i18n key; there is no text here. */
@Serializable
enum class AdviceReason {
    NOT_AFFECTED,
    WAIT_IS_FASTER,
    DETOUR_IS_FASTER,
    BUS_AVAILABLE,
    OPEN_ENDED,
    NO_ALTERNATIVE,
}

/** The answer to «what should I do now?» for one trip. Result of `advise()`, rendered by the screens. */
@Serializable
sealed class Advice {
    abstract val reason: AdviceReason

    /** Go as planned. [warnings] are disruptions on the way that don't stop the trip. */
    @Serializable
    data class Ok(
        val warnings: List<Disruption> = emptyList(),
        val skippedStops: List<Int> = emptyList(),
    ) : Advice() {
        override val reason: AdviceReason get() = AdviceReason.NOT_AFFECTED
    }

    /**
     * Wait for the disruption to end. [until] is null when the end is unknown.
     * [endType] says how firm [until] is (APPROX = «environ», AT_LEAST = «au minimum»).
     */
    @Serializable
    data class Wait(
        val until: Instant?,
        val endType: EndType?,
        override val reason: AdviceReason,
        val impact: TripImpact,
    ) : Advice()

    /** Take another trip. [alternatives] are unblocked OJP trips, earliest arrival first. */
    @Serializable
    data class Detour(
        val alternatives: List<Trip>,
        override val reason: AdviceReason,
        val impact: TripImpact,
    ) : Advice()

    /** Take the replacement bus from the disruption data, when OJP doesn't offer a way through. */
    @Serializable
    data class TakeBus(
        val bus: ReplacementBus,
        override val reason: AdviceReason,
        val impact: TripImpact,
    ) : Advice()
}
