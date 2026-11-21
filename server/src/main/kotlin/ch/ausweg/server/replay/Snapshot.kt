package ch.ausweg.server.replay

import ch.ausweg.server.ingest.DisruptionUpdate
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What a traveller could know at a given moment: only versions published at or before it. */
object Snapshot {
    /** An ended disruption stays visible («service resumed») this long after its last update. */
    val ENDED_RETENTION: Duration = 1.hours

    /** An active disruption with no update for this long is treated as stale (it was never closed in the data). */
    val STALE_AFTER: Duration = 24.hours

    fun at(
        updates: List<DisruptionUpdate>,
        t: Instant,
        endedRetention: Duration = ENDED_RETENTION,
        staleAfter: Duration = STALE_AFTER,
    ): List<Disruption> =
        updates
            .asSequence()
            .filter { it.disruption.publishedAt <= t }
            .groupBy { it.disruption.id }
            .values
            .map { versions -> versions.maxWith(compareBy({ it.disruption.publishedAt }, { it.disruption.version })) }
            .filterNot { it.deleted }
            .map { it.disruption }
            .filter { d ->
                val age = t - d.publishedAt
                when (d.status) {
                    DisruptionStatus.ENDED -> age <= endedRetention
                    DisruptionStatus.ACTIVE -> age <= staleAfter
                }
            }.sortedBy { it.publishedAt }
}
