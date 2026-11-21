package ch.ausweg.shared.ojp

import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.StopCall
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Trip

/** Turns an OJP 2.0 `Trip` element into our [Trip]. Used by [OjpParser.trips]. */
internal object TripReader {
    fun trip(node: XmlNode): Trip {
        val legs = node.children("Leg").mapNotNull(::leg)
        val rides = legs.count { it.mode != LegMode.WALK }
        return Trip(
            id = node.required("Id"),
            departure = node.requiredInstant("StartTime"),
            arrival = node.requiredInstant("EndTime"),
            transfers = node.textAt("Transfers")?.toIntOrNull() ?: (rides - 1).coerceAtLeast(0),
            legs = legs,
        )
    }

    private fun leg(node: XmlNode): Leg? {
        val timed = node.child("TimedLeg")
        val walk = node.child("TransferLeg") ?: node.child("ContinuousLeg")
        return when {
            timed != null -> timedLeg(timed)
            walk != null -> walkLeg(walk)
            else -> null
        }
    }

    private fun timedLeg(node: XmlNode): Leg {
        val service = node.child("Service")
        val board = node.child("LegBoard") ?: throw OjpParseException("TimedLeg without LegBoard")
        val alight = node.child("LegAlight") ?: throw OjpParseException("TimedLeg without LegAlight")
        return Leg(
            mode = ptMode(service?.textAt("Mode", "PtMode")),
            from = stopCall(board),
            to = stopCall(alight),
            line = service?.textAt("PublishedServiceName", "Text"),
            direction = service?.textAt("DestinationText", "Text"),
            intermediate = node.children("LegIntermediate").map(::stopCall),
            cancelled = service?.flag("Cancelled") ?: false,
            situationIds =
                service
                    ?.child("SituationFullRefs")
                    ?.children("SituationFullRef")
                    ?.mapNotNull { it.textAt("SituationNumber") }
                    .orEmpty(),
        )
    }

    /** Transfer and continuous legs: walking between platforms or stops. Times come from the time window, if any. */
    private fun walkLeg(node: XmlNode): Leg {
        val start = node.child("LegStart") ?: throw OjpParseException("${node.name} without LegStart")
        val end = node.child("LegEnd") ?: throw OjpParseException("${node.name} without LegEnd")
        return Leg(
            mode = LegMode.WALK,
            from = StopCall(placeStop(start), departure = node.instantAt("TimeWindowStart")?.let { CallTime(it) }),
            to = StopCall(placeStop(end), arrival = node.instantAt("TimeWindowEnd")?.let { CallTime(it) }),
        )
    }

    /** A `LegBoard` / `LegIntermediate` / `LegAlight`. The expected platform wins over the planned one. */
    private fun stopCall(node: XmlNode): StopCall {
        val ref = node.required("StopPointRef")
        return StopCall(
            stop = StopRef(ref, node.textAt("StopPointName", "Text") ?: ref, didokOf(ref)),
            platform = node.textAt("EstimatedQuay", "Text") ?: node.textAt("PlannedQuay", "Text"),
            arrival = node.child("ServiceArrival")?.let(::callTime),
            departure = node.child("ServiceDeparture")?.let(::callTime),
            notServiced = node.flag("NotServicedStop"),
        )
    }

    private fun callTime(node: XmlNode) =
        CallTime(node.requiredInstant("TimetabledTime"), node.instantAt("EstimatedTime"))

    /** A place ref (`LegStart`, `LegEnd`): a stop point, a stop place, or just a named position. */
    private fun placeStop(node: XmlNode): StopRef {
        val ref = node.textAt("StopPointRef") ?: node.textAt("StopPlaceRef").orEmpty()
        return StopRef(ref, node.textAt("Name", "Text") ?: ref, didokOf(ref))
    }

    private fun ptMode(value: String?): LegMode =
        when (value?.lowercase()) {
            "rail", "intercityrail", "urbanrail" -> LegMode.RAIL
            "bus", "coach", "trolleybus" -> LegMode.BUS
            "tram" -> LegMode.TRAM
            "metro", "underground" -> LegMode.METRO
            "water", "ferry" -> LegMode.WATER
            "telecabin", "funicular", "cableway", "lift" -> LegMode.CABLEWAY
            else -> LegMode.OTHER
        }
}
