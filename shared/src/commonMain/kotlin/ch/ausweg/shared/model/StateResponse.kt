package ch.ausweg.shared.model

import kotlinx.serialization.Serializable

/**
 * `GET /state` and the SSE `state` event: the clock plus every disruption known at `clock.now`.
 *
 * @property timeline what was learned about the listed disruptions up to `clock.now`, oldest first.
 */
@Serializable
data class StateResponse(
    val clock: ClockState,
    val disruptions: List<Disruption>,
    val timeline: List<TimelineEntry> = emptyList(),
)
