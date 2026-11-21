package ch.ausweg.shared.tips

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.LAUSANNE
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.Votes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class RankingTest {
    private val now = Fx.t("14:30")
    private val renensMorges = listOf(Section(8501118, 8501037)) // Renens VD - Morges

    /** A disruption tip on Puidoux - Vauderens unless [sections] says otherwise. */
    private fun tip(
        id: String,
        votes: Votes = Votes(),
        sections: List<Section> = listOf(Section(PUIDOUX, VAUDERENS)),
        disruptionId: String? = null,
    ) = Tip(id, TipKind.DISRUPTION, id, now, sections = sections, disruptionId = disruptionId, votes = votes)

    private fun transfer(
        id: String,
        station: Int,
        votes: Votes = Votes(),
    ) = Tip(id, TipKind.TRANSFER, id, now, station = station, votes = votes)

    @Test
    fun scoreCountsVotesStaffAndValidity() {
        assertEquals(3.0, score(Votes(up = 4, down = 1), false, now, now, decays = true))
        assertEquals(8.0, score(Votes(up = 4, down = 1), true, now, now, decays = true))
        assertEquals(1.0, score(Votes(stillValid = 3, outdated = 1), false, now, now, decays = false))
    }

    @Test
    fun disruptionTipsHalveEveryThirtyDaysButTransferTipsDont() {
        val old = now - 30.days
        assertEquals(5.0, score(Votes(up = 10), false, old, now, decays = true))
        assertEquals(10.0, score(Votes(up = 10), false, old, now, decays = false))
        // A tip dated after the clock (live tip during a replay) isn't boosted.
        assertEquals(10.0, score(Votes(up = 10), false, now + 5.days, now, decays = true))
    }

    @Test
    fun sameDisruptionWinsOverSimilar() {
        assertEquals(
            CommunityMatch.SAME_DISRUPTION,
            disruptionMatch(fire.id, emptyList(), null, listOf(fire)),
        )
        assertEquals(
            CommunityMatch.SIMILAR_DISRUPTION,
            disruptionMatch("old", listOf(Section(LAUSANNE, PUIDOUX)), null, listOf(fire)),
        )
    }

    @Test
    fun noSharedStationOrDifferentCauseIsNotSimilar() {
        assertNull(disruptionMatch("old", renensMorges, null, listOf(fire)))
        val withCause = fire.copy(reasonCode = "FIRE")
        assertNull(disruptionMatch("old", listOf(Section(PUIDOUX, VAUDERENS)), "TRACK_DAMAGE", listOf(withCause)))
        assertEquals(
            CommunityMatch.SIMILAR_DISRUPTION,
            disruptionMatch("old", listOf(Section(PUIDOUX, VAUDERENS)), "FIRE", listOf(withCause)),
        )
    }

    @Test
    fun endedDisruptionsDontMatch() {
        assertNull(disruptionMatch(fire.id, emptyList(), null, listOf(fire.copy(status = DisruptionStatus.ENDED))))
    }

    @Test
    fun rankTipsOrdersByMatchThenScoreAndFiltersStations() {
        val tips =
            listOf(
                tip("similar-popular", votes = Votes(up = 20)),
                tip("same", disruptionId = fire.id),
                tip("elsewhere", sections = renensMorges),
                transfer("transfer-lausanne", LAUSANNE, Votes(up = 50)),
                transfer("transfer-other", 8507000),
                tip("similar-weak", votes = Votes(up = 1)),
                tip("note", disruptionId = fire.id).copy(routeId = "r1", legIndex = 0),
            )
        val ranked = rankTips(tips, listOf(fire), now, stations = setOf(LAUSANNE))
        assertEquals(
            listOf("same", "similar-popular", "similar-weak", "transfer-lausanne"),
            ranked.map { it.tip.id },
        )
        assertEquals(CommunityMatch.PERMANENT, ranked.last().match)
    }

    @Test
    fun withoutDisruptionsOnlyTransferTipsShow() {
        val ranked = rankTips(listOf(transfer("t", LAUSANNE), tip("d")), emptyList(), now)
        assertEquals(listOf("t"), ranked.map { it.tip.id })
    }

    @Test
    fun rankRoutesKeepsPermanentAndMatchingRoutesWithNotesInLegOrder() {
        fun route(
            id: String,
            permanent: Boolean = false,
            sections: List<Section> = listOf(Section(PUIDOUX, VAUDERENS)),
            up: Int = 0,
        ) = CommunityRoute(
            id = id,
            from = StopRef("8501120", "Lausanne", LAUSANNE),
            to = StopRef("8504100", "Fribourg/Freiburg", Fx.FRIBOURG),
            legs = listOf(RouteLeg(StopRef("a", "A"), StopRef("b", "B"))),
            createdAt = now,
            permanent = permanent,
            sections = sections,
            votes = Votes(up = up),
        )
        val notes =
            listOf(
                tip("n2").copy(routeId = "similar", legIndex = 2),
                tip("n0").copy(routeId = "similar", legIndex = 0),
            )
        val ranked =
            rankRoutes(
                listOf(
                    route("always", permanent = true, up = 99),
                    route("similar"),
                    route("elsewhere", sections = renensMorges),
                ),
                notes,
                listOf(fire),
                now,
            )
        assertEquals(listOf("similar", "always"), ranked.map { it.route.id })
        assertEquals(listOf("n0", "n2"), ranked.first().notes.map { it.id })
        assertTrue(ranked.last().match == CommunityMatch.PERMANENT)
    }
}
