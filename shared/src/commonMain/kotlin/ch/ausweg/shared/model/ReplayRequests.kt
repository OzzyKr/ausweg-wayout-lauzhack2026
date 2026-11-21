package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** `POST /replay/start`. */
@Serializable
data class StartScenarioRequest(
    val scenarioId: String,
    val play: Boolean = true,
    val speed: Double? = null,
)

/** `POST /replay/seek`. */
@Serializable
data class SeekRequest(
    val at: Instant,
)

/** `POST /replay/speed`: simulated seconds per real second. */
@Serializable
data class SpeedRequest(
    val x: Double,
)
