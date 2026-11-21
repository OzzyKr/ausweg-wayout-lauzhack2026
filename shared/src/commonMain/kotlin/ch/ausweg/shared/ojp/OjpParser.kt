package ch.ausweg.shared.ojp

import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.model.ValidityWindow
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Trips and situations from an `OJPTripDelivery`.
 *
 * @property error OJP problem code (e.g. `TRIP_NOTRIPFOUND`) when OJP reported one; trips may then be empty.
 */
@Serializable
data class OjpTripResponse(
    val trips: List<Trip>,
    val situations: List<Situation> = emptyList(),
    val error: String? = null,
)

/** Situations from an `OJPStopEventDelivery` (a departure board). The departures themselves aren't read. */
@Serializable
data class OjpStopEventResponse(
    val situations: List<Situation>,
    val error: String? = null,
)

/** Stops from an `OJPLocationInformationDelivery`, best match first. */
@Serializable
data class OjpLocationResponse(
    val locations: List<Location>,
    val error: String? = null,
)

/**
 * Reads OJP 2.0 responses. Only the elements we use are read; everything else is ignored, so extra or
 * reordered elements don't break parsing. Throws [OjpParseException] for non-XML or missing required elements.
 */
object OjpParser {
    fun trips(xml: String): OjpTripResponse {
        val delivery = delivery(xml, "OJPTripDelivery")
        val situations = situations(delivery, "TripResponseContext")
        val trips = delivery.children("TripResult").mapNotNull { it.child("Trip") }.map(TripReader::trip)
        return OjpTripResponse(trips, situations, errorCode(delivery))
    }

    /** Every situation on a departure board: what the live poller collects at the hubs. */
    fun stopEvents(xml: String): OjpStopEventResponse {
        val delivery = delivery(xml, "OJPStopEventDelivery")
        return OjpStopEventResponse(situations(delivery, "StopEventResponseContext"), errorCode(delivery))
    }

    fun locations(xml: String): OjpLocationResponse {
        val delivery = delivery(xml, "OJPLocationInformationDelivery")
        val locations = delivery.children("PlaceResult").mapNotNull { it.child("Place")?.let(::location) }
        return OjpLocationResponse(locations, errorCode(delivery))
    }

    private fun situations(
        delivery: XmlNode,
        context: String,
    ): List<Situation> =
        delivery
            .child(context)
            ?.descendants("PtSituation")
            ?.mapNotNull(::situation)
            ?.toList()
            .orEmpty()

    private fun delivery(
        xml: String,
        name: String,
    ): XmlNode =
        XmlNode.parse(xml).descendants(name).firstOrNull()
            ?: throw OjpParseException("No <$name> in response")

    /** OJP problem types (`TripProblemType`, `LocationProblemType`, …) win over a generic SIRI error text. */
    private fun errorCode(delivery: XmlNode): String? =
        delivery
            .descendants()
            .firstOrNull { it.name.endsWith("ProblemType") }
            ?.text
            ?.takeIf { it.isNotEmpty() }
            ?: delivery
                .descendants("ErrorText")
                .firstOrNull()
                ?.text
                ?.takeIf { it.isNotEmpty() }
}

/** A `PlaceResult/Place`; only stops are returned (no addresses, POIs or towns). */
private fun location(place: XmlNode): Location? {
    val stopPlace = place.child("StopPlace")
    val ref = stopPlace?.textAt("StopPlaceRef") ?: return null
    val geo = place.child("GeoPosition")
    return Location(
        stop =
            StopRef(
                ref,
                stopPlace.textAt("StopPlaceName", "Text") ?: place.textAt("Name", "Text") ?: ref,
                didokOf(ref),
            ),
        latitude = geo?.textAt("Latitude")?.toDoubleOrNull(),
        longitude = geo?.textAt("Longitude")?.toDoubleOrNull(),
    )
}

/**
 * A SIRI `PtSituation`. Text is either directly under it (`Summary`, `Description`) or inside its
 * publishing actions (`SummaryText`, `DescriptionText`, …), as the Swiss data has it. Stops come from every
 * `AffectedStopPlace` / `AffectedStopPoint`, wherever they are under `Affects` (directly, or per affected line).
 */
private fun situation(node: XmlNode): Situation? {
    val id = node.textAt("SituationNumber") ?: return null
    val summary = node.child("Summary") ?: node.descendants("SummaryText").firstOrNull()
    val validity = node.children("ValidityPeriod").mapNotNull(::validityWindow)
    return Situation(
        id = id,
        summary = summary?.text?.takeIf { it.isNotEmpty() },
        description = (node.child("Description") ?: node.descendants("DescriptionText").firstOrNull()).nonEmptyText(),
        validFrom = validity.firstOrNull()?.from,
        validTo = validity.lastOrNull()?.to,
        language = (summary?.attributes?.get("lang") ?: node.textAt("Language"))?.lowercase(),
        version = node.textAt("Version")?.toIntOrNull(),
        recordedAt =
            node.descendants("RecordedAtTime").mapNotNull { it.instantOrNull() }.maxOrNull()
                ?: node.child("CreationTime")?.instantOrNull(),
        cause = node.textAt("AlertCause"),
        planned = node.flag("Planned"),
        validity = validity,
        stops = affectedStops(node),
        lines =
            node
                .descendants("PublishedLineName")
                .map { it.text }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList(),
        consequence = node.descendants("ConsequenceText").firstOrNull().nonEmptyText(),
        recommendation = node.descendants("RecommendationText").firstOrNull().nonEmptyText(),
        duration = node.descendants("DurationText").firstOrNull().nonEmptyText(),
    )
}

private fun validityWindow(period: XmlNode): ValidityWindow? =
    period.instantAt("StartTime")?.let { ValidityWindow(it, period.instantAt("EndTime")) }

private fun affectedStops(node: XmlNode): List<StopRef> {
    val affects = node.descendants("Affects")
    val stops =
        affects.flatMap { it.descendants() }.mapNotNull { stop ->
            val ref =
                when (stop.name) {
                    "AffectedStopPlace" -> stop.textAt("StopPlaceRef")
                    "AffectedStopPoint" -> stop.textAt("StopPointRef")
                    else -> null
                } ?: return@mapNotNull null
            StopRef(ref, stop.textAt("PlaceName") ?: stop.textAt("StopPointName") ?: ref, didokOf(ref))
        }
    // The same stop is often listed once per affected line; keep the first, but take a name from any of them.
    return stops
        .groupBy { it.ref }
        .map { (_, same) ->
            same.first().copy(name = same.firstOrNull { it.name != it.ref }?.name ?: same.first().name)
        }
}

private fun XmlNode?.nonEmptyText(): String? = this?.text?.takeIf { it.isNotEmpty() }

private fun XmlNode.instantOrNull(): Instant? =
    try {
        Instant.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }
