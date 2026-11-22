package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Community tips and routes. No names or contact details anywhere: a voter is a random id the device makes up
 * once, and tips are anonymous. Tip and route texts are what users wrote, in their language.
 */

/** What a tip is about. */
@Serializable
enum class TipKind {
    /** Written during a disruption, stored against its sections (and cause): shown again on similar disruptions. */
    DISRUPTION,

    /** A permanent transfer tip at a station (platform pair), no disruption attached. */
    TRANSFER,
}

/** One person's vote. A new vote from the same voter replaces their old one. */
@Serializable
enum class VoteValue { UP, DOWN, STILL_VALID, OUTDATED }

/** Vote counts. [stillValid] / [outdated] are for tips shown again on a later, similar disruption. */
@Serializable
data class Votes(
    val up: Int = 0,
    val down: Int = 0,
    val stillValid: Int = 0,
    val outdated: Int = 0,
)

/**
 * A tip. Notes pinned on a community route's change are tips with [routeId] and [legIndex].
 *
 * @property station DIDOK number of the station a transfer tip is about.
 * @property platformFrom / platformTo the platform pair of a transfer tip, if it's about one.
 * @property sections / reasonCode copied from the disruption the tip was written during, to find similar ones later.
 * @property disruptionId the disruption it was written during.
 * @property legIndex the route leg the note sits after (0 = the change between leg 0 and leg 1).
 */
@Serializable
data class Tip(
    val id: String,
    val kind: TipKind,
    val text: String,
    val createdAt: Instant,
    val station: Int? = null,
    val platformFrom: String? = null,
    val platformTo: String? = null,
    val sections: List<Section> = emptyList(),
    val reasonCode: String? = null,
    val disruptionId: String? = null,
    val routeId: String? = null,
    val legIndex: Int? = null,
    val staffEndorsed: Boolean = false,
    val votes: Votes = Votes(),
)

/** One ride of a community route, as the leg strip shows it. */
@Serializable
data class RouteLeg(
    val from: StopRef,
    val to: StopRef,
    val line: String? = null,
    val mode: LegMode = LegMode.RAIL,
    val departurePlatform: String? = null,
    val arrivalPlatform: String? = null,
)

/**
 * A connection a user drew. [permanent] routes («always») are shown whenever someone goes [from] → [to];
 * the others («this disruption») only on the same or a similar disruption.
 */
@Serializable
data class CommunityRoute(
    val id: String,
    val from: StopRef,
    val to: StopRef,
    val legs: List<RouteLeg>,
    val createdAt: Instant,
    val permanent: Boolean = false,
    val sections: List<Section> = emptyList(),
    val reasonCode: String? = null,
    val disruptionId: String? = null,
    val staffEndorsed: Boolean = false,
    val votes: Votes = Votes(),
)

/** Why a tip or route is shown now. */
@Serializable
enum class CommunityMatch {
    /** Written during the disruption that is happening now. */
    SAME_DISRUPTION,

    /** Written during an earlier disruption on the same stretch: «from a similar disruption on <createdAt>». */
    SIMILAR_DISRUPTION,

    /** Not tied to a disruption (transfer tips, «always» routes). */
    PERMANENT,
}

@Serializable
data class RankedTip(
    val tip: Tip,
    val match: CommunityMatch,
    val score: Double,
)

/** A route with its notes, ordered by [Tip.legIndex]. */
@Serializable
data class RankedRoute(
    val route: CommunityRoute,
    val notes: List<Tip>,
    val match: CommunityMatch,
    val score: Double,
)

/**
 * `POST /tips`. With [disruptionId] of a current disruption, the backend copies its sections and cause onto the tip.
 * A [TipKind.TRANSFER] tip needs a [station].
 */
@Serializable
data class NewTip(
    val kind: TipKind,
    val text: String,
    val station: Int? = null,
    val platformFrom: String? = null,
    val platformTo: String? = null,
    val disruptionId: String? = null,
)

/** A note on a change of a route being shared: after leg [legIndex]. */
@Serializable
data class NewNote(
    val legIndex: Int,
    val text: String,
)

/** `POST /routes` («share a route»). */
@Serializable
data class NewRoute(
    val legs: List<RouteLeg>,
    val permanent: Boolean = false,
    val disruptionId: String? = null,
    val notes: List<NewNote> = emptyList(),
)

/**
 * `POST /tips/{id}/vote`, `POST /routes/{id}/vote`. [voterId]: a random id the device keeps. [value] null takes the
 * voter's vote back.
 */
@Serializable
data class VoteRequest(
    val voterId: String,
    val value: VoteValue? = null,
)
