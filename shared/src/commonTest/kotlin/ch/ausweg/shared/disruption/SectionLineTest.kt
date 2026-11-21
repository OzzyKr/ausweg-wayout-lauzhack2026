package ch.ausweg.shared.disruption

import ch.ausweg.shared.disruption.Fx.FRIBOURG
import ch.ausweg.shared.disruption.Fx.LAUSANNE
import ch.ausweg.shared.disruption.Fx.PALEZIEUX
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.ROMONT
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.YVERDON
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.leg
import ch.ausweg.shared.disruption.Fx.trip
import ch.ausweg.shared.model.Section
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Trains that run through a closed section without stopping at its ends (OJP lists served stops only). */
class SectionLineTest {
    private val line = listOf(LAUSANNE, PUIDOUX, PALEZIEUX, VAUDERENS, ROMONT, FRIBOURG)
    private val withLine = fire.copy(sections = listOf(Section(PUIDOUX, VAUDERENS, line)))

    private fun blocked(
        vararg stops: Int,
        disruption: ch.ausweg.shared.model.Disruption = withLine,
    ) = affects(trip("t", "14:45", "15:30", leg(*stops, dep = "14:45")), listOf(disruption)).isBlocked

    @Test
    fun nonStopTrainThroughTheSectionIsBlocked() {
        assertTrue(blocked(LAUSANNE, FRIBOURG))
        assertTrue(blocked(FRIBOURG, ROMONT, LAUSANNE)) // other direction
    }

    @Test
    fun withoutTheLineOnlyServedEndsCount() {
        val noLine = fire.copy(sections = listOf(Section(PUIDOUX, VAUDERENS)))
        assertFalse(blocked(LAUSANNE, FRIBOURG, disruption = noLine))
    }

    @Test
    fun stoppingInsideTheSectionIsBlocked() {
        assertTrue(blocked(LAUSANNE, PALEZIEUX))
    }

    @Test
    fun touchingAnEndOrStayingOutsideIsNot() {
        assertFalse(blocked(LAUSANNE, PUIDOUX))
        assertFalse(blocked(VAUDERENS, ROMONT, FRIBOURG))
    }

    @Test
    fun leavingTheLineInBetweenIsNot() {
        // One vehicle Lausanne → Yverdon → Fribourg: Yverdon isn't on the line, so it didn't run through Puidoux.
        assertFalse(blocked(LAUSANNE, YVERDON, FRIBOURG))
    }
}
