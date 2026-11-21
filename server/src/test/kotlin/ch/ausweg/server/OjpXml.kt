package ch.ausweg.server

/**
 * Minimal OJP 2.0 trip answers for server tests (the shared parser has the full, realistic fixtures).
 * A stop is a DIDOK number and a time, e.g. `stop(8501120, "2026-11-27T15:30:00Z")`.
 */
object OjpXml {
    data class Stop(
        val didok: Int,
        val time: String?,
    )

    fun stop(
        didok: Int,
        time: String? = null,
    ) = Stop(didok, time)

    /** One rail leg through [stops] (first = board, last = alight). */
    fun leg(
        line: String,
        vararg stops: Stop,
    ): String {
        fun call(
            tag: String,
            s: Stop,
            timeTag: String,
        ) = "<$tag><siri:StopPointRef>${s.didok}</siri:StopPointRef>" +
            "<StopPointName><Text>${s.didok}</Text></StopPointName>" +
            (s.time?.let { "<$timeTag><TimetabledTime>$it</TimetabledTime></$timeTag>" } ?: "") +
            "</$tag>"
        val middle = stops.drop(1).dropLast(1).joinToString("") { call("LegIntermediate", it, "ServiceDeparture") }
        return "<Leg><TimedLeg>" +
            call("LegBoard", stops.first(), "ServiceDeparture") + middle +
            call("LegAlight", stops.last(), "ServiceArrival") +
            "<Service><Mode><PtMode>rail</PtMode></Mode>" +
            "<PublishedServiceName><Text>$line</Text></PublishedServiceName></Service>" +
            "</TimedLeg></Leg>"
    }

    fun trip(
        id: String,
        start: String,
        end: String,
        vararg legs: String,
    ) = "<TripResult><Trip><Id>$id</Id><StartTime>$start</StartTime><EndTime>$end</EndTime>" +
        legs.joinToString("") + "</Trip></TripResult>"

    fun response(vararg trips: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
<OJPResponse><siri:ServiceDelivery><OJPTripDelivery>${trips.joinToString(
            "",
        )}</OJPTripDelivery></siri:ServiceDelivery></OJPResponse>
</OJP>"""

    fun locations(vararg names: Pair<Int, String>) =
        """<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
<OJPResponse><siri:ServiceDelivery><OJPLocationInformationDelivery>""" +
            names.joinToString("") { (didok, name) ->
                "<PlaceResult><Place><StopPlace><StopPlaceRef>$didok</StopPlaceRef>" +
                    "<StopPlaceName><Text>$name</Text></StopPlaceName></StopPlace></Place></PlaceResult>"
            } +
            "</OJPLocationInformationDelivery></siri:ServiceDelivery></OJPResponse></OJP>"

    /** Lausanne → Fribourg direct, through Puidoux – Palézieux – Vauderens (closed during the fire). */
    fun direct(
        start: String,
        end: String,
    ) = trip(
        "T1",
        start,
        end,
        leg("IR15", stop(8501120, start), stop(8504012), stop(8504014), stop(8504021), stop(8504100, end)),
    )

    /** Lausanne → Yverdon-les-Bains → Payerne → Fribourg, around the closure. */
    fun viaYverdon(
        start: String,
        end: String,
    ) = trip(
        "T1",
        start,
        end,
        leg("IC5", stop(8501120, start), stop(8504200)),
        leg("S30", stop(8504200, start), stop(8504134), stop(8504100, end)),
    )
}
