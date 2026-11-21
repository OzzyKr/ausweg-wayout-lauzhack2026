package ch.ausweg.shared.model

import kotlinx.serialization.Serializable

/** `GET /state` and the SSE `state` event: the clock plus every disruption known at `clock.now`. */
@Serializable
data class StateResponse(
    val clock: ClockState,
    val disruptions: List<Disruption>,
)
