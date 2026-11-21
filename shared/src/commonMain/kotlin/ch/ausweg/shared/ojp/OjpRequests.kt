package ch.ausweg.shared.ojp

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A trip search: from [from] to [to] leaving at [departAt], optionally through [via] stops
 * (routing re-queries through alternative stops this way). Refs are DIDOK numbers or SLOIDs.
 */
@Serializable
data class TripQuery(
    val from: String,
    val to: String,
    val departAt: Instant,
    val via: List<String> = emptyList(),
    val results: Int = 5,
)

/**
 * OJP 2.0 request bodies for `POST https://api.opentransportdata.swiss/ojp20`
 * (headers `Content-Type: application/xml`, `Authorization: Bearer <key>`; only the backend holds the key).
 *
 * Stop refs are DIDOK numbers or SLOIDs (`ch:1:sloid:1120`). Every caller-supplied value is XML-escaped.
 */
object OjpRequests {
    /**
     * Trips for [query]. Intermediate stops are always included: they are what tells us whether a leg
     * passes an affected section.
     */
    fun trip(
        query: TripQuery,
        now: Instant,
        requestorRef: String,
    ): String {
        val vias =
            query.via.joinToString("") { ref ->
                "<Via><ViaPoint>${placeRef(ref)}</ViaPoint></Via>"
            }
        return envelope(
            now,
            requestorRef,
            """
            <OJPTripRequest>
            <siri:RequestTimestamp>$now</siri:RequestTimestamp>
            <Origin><PlaceRef>${placeRef(query.from)}</PlaceRef><DepArrTime>${query.departAt}</DepArrTime></Origin>
            <Destination><PlaceRef>${placeRef(query.to)}</PlaceRef></Destination>
            $vias
            <Params>
            <NumberOfResults>${query.results}</NumberOfResults>
            <IncludeIntermediateStops>true</IncludeIntermediateStops>
            <IncludeSituationsContext>true</IncludeSituationsContext>
            <UseRealtimeData>full</UseRealtimeData>
            </Params>
            </OJPTripRequest>
            """,
        )
    }

    /** Stops whose name matches [query], best match first (plan screen autocomplete). */
    fun location(
        query: String,
        now: Instant,
        requestorRef: String,
        results: Int = 10,
    ): String =
        envelope(
            now,
            requestorRef,
            """
            <OJPLocationInformationRequest>
            <siri:RequestTimestamp>$now</siri:RequestTimestamp>
            <InitialInput><Name>${escapeXml(query)}</Name></InitialInput>
            <Restrictions><Type>stop</Type><NumberOfResults>$results</NumberOfResults></Restrictions>
            </OJPLocationInformationRequest>
            """,
        )

    private fun placeRef(ref: String): String {
        val escaped = escapeXml(ref)
        // The schema requires a Name; OJP ignores it when a ref is given.
        return "<siri:StopPointRef>$escaped</siri:StopPointRef><Name><Text>$escaped</Text></Name>"
    }

    private fun envelope(
        now: Instant,
        requestorRef: String,
        body: String,
    ): String =
        // Joined line by line: the XML declaration must be the very first characters.
        listOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">""",
            "<OJPRequest><siri:ServiceRequest>",
            "<siri:RequestTimestamp>$now</siri:RequestTimestamp>",
            "<siri:RequestorRef>${escapeXml(requestorRef)}</siri:RequestorRef>",
            body.trimIndent(),
            "</siri:ServiceRequest></OJPRequest>",
            "</OJP>",
        ).joinToString("\n")
}

internal fun escapeXml(value: String): String =
    buildString {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
