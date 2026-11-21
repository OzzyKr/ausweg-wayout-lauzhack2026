package ch.ausweg.shared.ojp

import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Trip
import kotlinx.serialization.Serializable

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
        val situations =
            delivery
                .child("TripResponseContext")
                ?.descendants("PtSituation")
                ?.mapNotNull(::situation)
                ?.toList()
                .orEmpty()
        val trips = delivery.children("TripResult").mapNotNull { it.child("Trip") }.map(TripReader::trip)
        return OjpTripResponse(trips, situations, errorCode(delivery))
    }

    fun locations(xml: String): OjpLocationResponse {
        val delivery = delivery(xml, "OJPLocationInformationDelivery")
        val locations = delivery.children("PlaceResult").mapNotNull { it.child("Place")?.let(::location) }
        return OjpLocationResponse(locations, errorCode(delivery))
    }

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
 * publishing actions (`SummaryText`, `DescriptionText`), as the Swiss OJP cookbook shows.
 */
private fun situation(node: XmlNode): Situation? {
    val id = node.textAt("SituationNumber") ?: return null
    val summary = node.child("Summary") ?: node.descendants("SummaryText").firstOrNull()
    val description = node.child("Description") ?: node.descendants("DescriptionText").firstOrNull()
    return Situation(
        id = id,
        summary = summary?.text?.takeIf { it.isNotEmpty() },
        description = description?.text?.takeIf { it.isNotEmpty() },
        validFrom = node.instantAt("ValidityPeriod", "StartTime"),
        validTo = node.instantAt("ValidityPeriod", "EndTime"),
        language = summary?.attributes?.get("lang") ?: node.textAt("Language"),
    )
}
