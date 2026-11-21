package ch.ausweg.shared.routing

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.model.CallTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimeShiftTest {
    private val zurich = TimeZone.of("Europe/Zurich")

    @Test
    fun sameWeekdayKeepsLocalTimeAcrossDst() {
        // Fri 25 Sep 2026 16:30 CEST (14:30Z); today is Sat 21 Nov 2026 (CET). Next Friday is 27 Nov, 16:30 CET.
        val target = Instant.parse("2026-09-25T14:30:00Z")
        val today = Instant.parse("2026-11-21T17:00:00Z")
        assertEquals(Instant.parse("2026-11-27T15:30:00Z"), sameWeekdayNear(target, today, zurich))
    }

    @Test
    fun sameWeekdayCanBeToday() {
        val target = Instant.parse("2026-09-26T08:00:00Z") // Saturday 10:00 CEST
        val today = Instant.parse("2026-11-21T17:00:00Z") // Saturday
        assertEquals(Instant.parse("2026-11-21T09:00:00Z"), sameWeekdayNear(target, today, zurich))
    }

    @Test
    fun daysBetweenCountsLocalDates() {
        val sep = Instant.parse("2026-09-25T22:30:00Z") // already 26 Sep in Zurich
        val nov = Instant.parse("2026-11-27T15:30:00Z")
        assertEquals(-62, daysBetween(nov, sep, zurich))
    }

    @Test
    fun movedTripKeepsLocalTimesAndDropsRealtime() {
        val leg = Fx.ic1.legs.first()
        val live =
            Fx.ic1.copy(
                departure = Instant.parse("2026-11-27T15:30:00Z"),
                arrival = Instant.parse("2026-11-27T16:15:00Z"),
                legs =
                    listOf(
                        leg.copy(
                            from =
                                leg.from.copy(
                                    departure =
                                        CallTime(
                                            Instant.parse("2026-11-27T15:30:00Z"),
                                            Instant.parse("2026-11-27T15:34:00Z"),
                                        ),
                                    notServiced = true,
                                ),
                            cancelled = true,
                            situationIds = listOf("s1"),
                        ),
                    ),
            )
        val moved = live.movedByDays(-63, zurich)
        assertEquals(Instant.parse("2026-09-25T14:30:00Z"), moved.departure)
        assertEquals(Instant.parse("2026-09-25T15:15:00Z"), moved.arrival)
        val from = moved.legs.first().from
        assertEquals(Instant.parse("2026-09-25T14:30:00Z"), from.departure?.timetabled)
        assertNull(from.departure?.estimated)
        assertFalse(from.notServiced)
        assertFalse(moved.legs.first().cancelled)
        assertTrue(
            moved.legs
                .first()
                .situationIds
                .isEmpty(),
        )
    }
}
