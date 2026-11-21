package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.model.TripImpact

/**
 * What in [trip] is hit by [disruptions] (the list from `StateResponse`). Pure: no clock, no network.
 *
 * Rules:
 * - Only `ACTIVE` disruptions count. A leg departing at or after `expectedEnd` isn't hit.
 *   Leg times are `estimated ?: timetabled`.
 * - A leg's path is `from` + `intermediate` + `to`, compared by `StopRef.didok` (walk legs and stops
 *   without a didok are skipped).
 * - A leg is in a `Section(a, b)` if its path has a and b, in either order. If `sections` is empty,
 *   it's hit if its path has at least 2 of `affectedStops` (or the only one, if there is one).
 * - `LINE_CLOSED` blocks every non-walk leg in the section. `REPLACEMENT_BUS` blocks only `RAIL` legs:
 *   a `BUS` leg there is the replacement itself.
 * - `leg.cancelled`, or `notServiced` on the leg's `from` / `to`, puts the leg in `cancelledLegs`.
 *   `notServiced` on an intermediate stop only adds it to `skippedStops`.
 */
@Suppress("UnusedParameter") // stub: remove when implemented
fun affects(
    trip: Trip,
    disruptions: List<Disruption>,
): TripImpact = TODO("blue")
