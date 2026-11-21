package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A past disruption that historical mode can replay from [start] to [end].
 *
 * @property suggestedFrom stop ref (DIDOK number) to prefill the plan screen with, if any.
 * @property suggestedFromName / suggestedToName the official station names of [suggestedFrom] / [suggestedTo],
 *   so the plan screen can show them without a search.
 */
@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val disruptionIds: List<String> = emptyList(),
    val suggestedFrom: String? = null,
    val suggestedTo: String? = null,
    val suggestedFromName: String? = null,
    val suggestedToName: String? = null,
)
