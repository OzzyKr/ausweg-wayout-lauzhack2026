package ch.ausweg.server.ingest

import ch.ausweg.shared.model.Scenario
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Replay starts this long before the first publication, so the user sees the moment it breaks. */
val SCENARIO_LEAD_IN: Duration = 15.minutes

/** Replay keeps running this long after the last publication. */
val SCENARIO_TAIL: Duration = 30.minutes

/** One scenario per disruption, used when the data file has no curated scenarios. */
fun deriveScenarios(updates: List<DisruptionUpdate>): List<Scenario> =
    updates
        .groupBy { it.disruption.id }
        .map { (id, versions) ->
            val sorted = versions.map { it.disruption }.sortedBy { it.publishedAt }
            val first = sorted.first()
            Scenario(
                id = id,
                title =
                    first.texts["en"]?.title ?: first.texts.values
                        .firstOrNull()
                        ?.title ?: id,
                start = first.publishedAt - SCENARIO_LEAD_IN,
                end = sorted.last().publishedAt + SCENARIO_TAIL,
                disruptionIds = listOf(id),
            )
        }.sortedBy { it.start }
