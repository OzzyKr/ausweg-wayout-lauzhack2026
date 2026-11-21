package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class DisruptionStatus { ACTIVE, ENDED }

/** Title and body of a disruption in one language, as SBB published it (not translated by us). */
@Serializable
data class DisruptionText(
    val title: String,
    val body: String = "",
)

/**
 * One disruption as it was known at [publishedAt]: the latest published version.
 *
 * @property affectedStops DIDOK numbers of the affected stations/section, to match against trip legs.
 * @property texts language code (`de`, `fr`, `it`, `en`) to text.
 * @property hints tags from the keyword rules, e.g. `REPLACEMENT_BUS`, `LINE_CLOSED`.
 */
@Serializable
data class Disruption(
    val id: String,
    val version: Int,
    val publishedAt: Instant,
    val status: DisruptionStatus,
    val reasonCode: String? = null,
    val affectedStops: List<Int> = emptyList(),
    val lines: List<String> = emptyList(),
    val expectedEnd: Instant? = null,
    val texts: Map<String, DisruptionText> = emptyMap(),
    val hints: List<String> = emptyList(),
)
