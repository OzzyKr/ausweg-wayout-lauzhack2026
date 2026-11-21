package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.DisruptionText

/** Languages SBB publishes in, in the order we fall back to when the UI language has no text. */
val PUBLISHED_LANGUAGES: List<String> = listOf("fr", "de", "it", "en")

/**
 * The text to show for [this] disruption: the UI language if SBB published one, else the first of
 * [PUBLISHED_LANGUAGES], else any. Never translated by us (AGENTS.md §3.2).
 */
fun Disruption.text(uiLanguage: String): DisruptionText? =
    texts[uiLanguage]
        ?: PUBLISHED_LANGUAGES.firstNotNullOfOrNull { texts[it] }
        ?: texts.values.firstOrNull()

/** True if the disruption stops trains (as opposed to a warning). */
val Disruption.isBlocking: Boolean get() = hints.any { it in DisruptionHints.BLOCKING }

/** Disruptions for the «Disruptions now» list: active ones first, then by newest version. */
fun sortForDisplay(disruptions: List<Disruption>): List<Disruption> =
    disruptions.sortedWith(
        compareBy<Disruption>({ it.status != DisruptionStatus.ACTIVE }, { !it.isBlocking })
            .thenByDescending { it.publishedAt },
    )
