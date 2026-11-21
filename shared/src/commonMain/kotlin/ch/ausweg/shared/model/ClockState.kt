package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Which clock the backend runs on: the real one, or a simulated one replaying a past disruption. */
@Serializable
enum class Mode { LIVE, HISTORICAL }

/** The backend's clock as seen by every client. In [Mode.LIVE] it is always playing at speed 1. */
@Serializable
data class ClockState(
    val mode: Mode,
    val now: Instant,
    val playing: Boolean,
    val speed: Double,
    val scenarioId: String? = null,
)
