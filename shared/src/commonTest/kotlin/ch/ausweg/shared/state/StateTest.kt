package ch.ausweg.shared.state

import ch.ausweg.shared.api.LinkStatus
import ch.ausweg.shared.api.ServerEvent
import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.ic1
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.disruption.UpdateKind
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.InfoSource
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.StateResponse
import ch.ausweg.shared.model.TimelineEntry
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.routing.Pick
import ch.ausweg.shared.routing.Stage
import ch.ausweg.shared.tips.StripKind
import ch.ausweg.shared.tips.legStrip
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The state holders against [FakeApi]. Backend clock 14:30Z, the fire closes Puidoux – Vauderens until 16:00Z. */
class StateTest {
    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-11-21T12:00:00Z")
    }

    private val api = FakeApi()

    private fun TestScope.app(): AuswegApp {
        val app = AuswegApp(api, MemorySettings(), backgroundScope)
        app.start()
        runCurrent()
        return app
    }

    private suspend fun TestScope.state(vararg ds: ch.ausweg.shared.model.Disruption) {
        api.emit(ServerEvent.State(StateResponse(api.clock, ds.toList(), TIMELINE)))
        runCurrent()
    }

    @Test
    fun sessionFollowsServerEvents() =
        runTest {
            val session = AppSession(api, backgroundScope, FixedClock)
            session.start()
            api.emit(ServerEvent.Community(4))
            state(fire)
            assertEquals(LinkStatus.LIVE, session.link.value)
            assertEquals(listOf(fire), session.disruptions.value)
            assertEquals(4L, session.community.value)
            assertEquals(t("14:30"), session.now.value)
            assertEquals(FakeApi.SCENARIO, session.activeScenario.value)

            session.togglePlay()
            session.cycleSpeed()
            runCurrent()
            assertEquals(listOf("play", "speed 10.0"), api.calls.filter { it == "play" || it.startsWith("speed") })
        }

    @Test
    fun replayHelpers() {
        val playing = ClockState(Mode.HISTORICAL, t("14:30"), playing = true, speed = 60.0)
        assertEquals(t("14:40"), displayNow(playing, 10.seconds))
        assertEquals(t("14:30"), displayNow(playing.copy(playing = false), 10.seconds))
        assertEquals(10.0, nextSpeed(1.0))
        assertEquals(1.0, nextSpeed(300.0))
        assertEquals(0.2f, replayFraction(FakeApi.SCENARIO, t("14:30")))
        assertEquals(t("14:30"), replayInstant(FakeApi.SCENARIO, 0.2f))
        assertEquals(0f, replayFraction(FakeApi.SCENARIO, t("10:00")))
    }

    @Test
    fun navigatorBackStack() {
        val nav = Navigator()
        assertFalse(nav.back())
        nav.push(Screen.Options)
        nav.push(Screen.Route("r1"))
        assertEquals(Screen.Route("r1"), nav.state.value.top)
        assertTrue(nav.back())
        assertEquals(Screen.Options, nav.state.value.top)
        nav.select(Tab.TRIPS)
        assertNull(nav.state.value.top)
        assertTrue(nav.back())
        assertEquals(Tab.PLAN, nav.state.value.tab)
    }

    @Test
    fun voterIdIsMadeOnceAndKept() {
        val settings = MemorySettings()
        val id = settings.voterId(Random(1))
        assertEquals(16, id.length)
        assertEquals(id, settings.voterId(Random(2)))
    }

    @Test
    fun planPrefillsFromTheScenarioAndSearchesStops() =
        runTest {
            val app = app()
            state(fire)
            val ui = app.plan.ui.value
            assertEquals("Lausanne", ui.from.selected?.name)
            assertEquals(Fx.FRIBOURG, ui.to.selected?.didok)
            assertTrue(ui.prefilled)

            app.plan.type(StopField.FROM, "Laus")
            assertNull(app.plan.ui.value.query)
            advanceTimeBy(400)
            assertEquals(listOf(Location(FakeApi.LAUSANNE)), app.plan.ui.value.from.suggestions)
            app.plan.choose(StopField.FROM, Location(FakeApi.LAUSANNE))
            app.plan.swap()
            assertEquals("Fribourg/Freiburg", app.plan.ui.value.from.query)
            assertFalse(app.plan.ui.value.prefilled)
        }

    @Test
    fun findMyWayPlansAndFollowsTheDisruption() =
        runTest {
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            assertEquals(Screen.Options, app.navigator.state.value.top)
            val ready = assertIs<OptionsUi.Ready>(app.options.ui.value)
            assertIs<Pick.Go>(ready.view.pick)
            assertEquals(listOf(fire), ready.view.blockedBy)
            assertEquals(listOf(FakeApi.ROUTE), ready.routes)
            assertEquals(listOf(FakeApi.TIP), ready.tips)
            assertTrue("plan 8501120 8504100" in api.calls)

            // The fire ends: the usual IC1 is fine again.
            state(fire.copy(status = ch.ausweg.shared.model.DisruptionStatus.ENDED))
            val after = assertIs<OptionsUi.Ready>(app.options.ui.value)
            assertEquals(Pick.Go(ic1), after.view.pick)
            assertTrue(app.alerts.value.none { it.affectsYou })
        }

    @Test
    fun planErrorsAreKept() =
        runTest {
            val app = app()
            state(fire)
            api.planError = IllegalStateException("offline")
            app.findMyWay()
            runCurrent()
            assertEquals(LoadError.NETWORK, assertIs<OptionsUi.Failed>(app.options.ui.value).error)
            api.planError = null
            api.planResponse = api.planResponse?.copy(trips = emptyList())
            app.options.retry()
            runCurrent()
            assertEquals(LoadError.NO_CONNECTIONS, assertIs<OptionsUi.Failed>(app.options.ui.value).error)
        }

    @Test
    fun takingThePickWatchesTheAvoidedDisruption() =
        runTest {
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            val pick = assertIs<Pick.Go>(assertIs<OptionsUi.Ready>(app.options.ui.value).view.pick)
            app.takeTrip(pick.trip)
            runCurrent()
            assertEquals(Tab.TRIPS, app.navigator.state.value.tab)
            val trip = assertNotNull(app.trip.ui.value)
            assertEquals(setOf(fire.id), trip.watched)
            assertEquals(Stage.BEFORE_DEPARTURE, trip.progress.stage)
            assertEquals(listOf(TIMELINE[1], TIMELINE[0]), trip.timeline)
            assertTrue(
                app.alerts.value
                    .single()
                    .affectsYou,
            )

            state(fire.copy(version = 3, bus = ReplacementBus(BusStatus.SCHEDULED, Fx.PALEZIEUX)))
            val banner =
                assertNotNull(
                    app.trip.ui.value
                        ?.updates
                        ?.single(),
                )
            assertEquals(UpdateKind.BUS_RUNNING, banner.kind)
            app.trip.dismiss(banner)
            runCurrent()
            assertEquals(
                emptyList(),
                app.trip.ui.value
                    ?.updates,
            )
        }

    @Test
    fun communityRouteVoteAndTake() =
        runTest {
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            app.openRoute(FakeApi.ROUTE)
            runCurrent()
            assertEquals(Screen.Route("r1"), app.navigator.state.value.top)
            app.route.vote(VoteValue.UP)
            runCurrent()
            assertEquals(
                12,
                app.route.ui.value
                    ?.ranked
                    ?.route
                    ?.votes
                    ?.up,
            )
            assertEquals(
                VoteValue.UP,
                app.route.ui.value
                    ?.myVote,
            )
            app.route.vote(VoteValue.UP) // the same vote again takes it back
            runCurrent()
            assertEquals(2, api.calls.count { it.startsWith("voteRoute") })
            assertTrue(api.calls.last().startsWith("voteRoute r1 null"))
            assertNull(
                app.route.ui.value
                    ?.myVote,
            )
            app.route.vote(VoteValue.UP)
            runCurrent()
            assertEquals(
                VoteValue.UP,
                app.route.ui.value
                    ?.myVote,
            )

            app.takeRoute()
            runCurrent()
            val trip =
                assertIs<Taken.Shared>(
                    app.trip.ui.value
                        ?.taken,
                )
            assertEquals("r1", trip.route.route.id)
            assertEquals(
                Stage.UNKNOWN,
                app.trip.ui.value
                    ?.progress
                    ?.stage,
            )
        }

    @Test
    fun shareThePickWithANote() =
        runTest {
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            app.openShare()
            assertEquals(Screen.Share, app.navigator.state.value.top)
            val ui = app.share.ui.value
            assertEquals(ShareScope.THIS_DISRUPTION, ui.scope)
            assertEquals(1, ui.strip.changes.size)

            app.share.note("ignored without a selected change")
            assertTrue(
                app.share.ui.value.notes
                    .isEmpty(),
            )
            app.share.selectChange(0)
            app.share.note("  6 min at Yverdon is plenty  ")
            app.postShare()
            runCurrent()
            val posted = api.posted.single()
            assertEquals(fire.id, posted.disruptionId)
            assertFalse(posted.permanent)
            assertEquals("6 min at Yverdon is plenty", posted.notes.single().text)
            assertEquals(0, posted.notes.single().legIndex)
            assertEquals(listOf("IC5", "S30"), posted.legs.map { it.line })
            assertEquals(Screen.Options, app.navigator.state.value.top)
        }

    @Test
    fun shareRules() =
        runTest {
            val share = ShareState(api, backgroundScope)
            val legs =
                ch.ausweg.shared.tips
                    .legStrip(Fx.viaYverdon("14:40", "15:50"))
                    .legs
            share.start(legs, disruptionId = null)
            assertEquals(ShareScope.ALWAYS, share.ui.value.scope)
            share.scope(ShareScope.THIS_DISRUPTION)
            assertFalse(share.ui.value.canPost)
            share.scope(ShareScope.ALWAYS)
            share.selectChange(0)
            share.note("x".repeat(NOTE_MAX_LENGTH + 10))
            assertEquals(
                NOTE_MAX_LENGTH,
                share.ui.value.notes[0]
                    ?.length,
            )
            share.toggle(1)
            assertEquals(1, share.ui.value.strip.legs.size)
            assertTrue(
                share.ui.value.notes
                    .isEmpty(),
            )
            share.toggle(0)
            assertNull(newRoute(share.ui.value))

            share.toggle(0)
            assertTrue(share.ui.value.canPost)
            api.failPost = true
            share.post()
            runCurrent()
            assertEquals(ShareStatus.FAILED, share.ui.value.status)
        }

    @Test
    fun aMiddleRideCantBeLeftOut() {
        val two = legStrip(Fx.viaYverdon("14:40", "15:50")).legs
        val three = two + two.last()
        val ui = ShareUi(legs = three, checked = listOf(true, false, true))
        assertTrue(ui.hasGap)
        assertFalse(ui.copy(checked = listOf(false, false, false)).hasGap)
        assertFalse(ui.canPost)
        assertNull(newRoute(ui))
        assertTrue(ui.copy(checked = listOf(false, true, true)).canPost)
    }

    @Test
    fun sharedRoutesKeepTheRideMode() {
        val metro = ic1.copy(legs = ic1.legs.map { it.copy(mode = LegMode.METRO, line = "M2") })
        val ui = ShareUi(legs = legStrip(metro).legs, checked = listOf(true))
        assertEquals(listOf(LegMode.METRO), newRoute(ui)?.legs?.map { it.mode })
    }

    @Test
    fun shareSourceNeverTagsARouteThroughTheDisruption() {
        val bus = ReplacementBus(BusStatus.SCHEDULED, from = Fx.PALEZIEUX, to = Fx.VAUDERENS, eta = t("15:15"))
        val busPick = assertNotNull(shareSource(null, Pick.Bus(bus, ic1), listOf(fire)))
        assertEquals(
            StripKind.REPLACEMENT_BUS,
            busPick.strip.legs
                .single()
                .kind,
        )
        assertTrue(busPick.avoidsDisruptions)

        val waitPick = assertNotNull(shareSource(null, Pick.Wait(ic1, t("16:00"), null), listOf(fire)))
        assertFalse(waitPick.avoidsDisruptions)

        val takenBlocked = shareSource(Taken.Planned(ic1, emptyList()), Pick.Go(ic1), listOf(fire))
        assertEquals(false, takenBlocked?.avoidsDisruptions)
        val detour = Fx.viaYverdon("14:40", "15:50")
        assertEquals(true, shareSource(Taken.Planned(detour, emptyList()), null, listOf(fire))?.avoidsDisruptions)
        assertNull(shareSource(null, null, listOf(fire)))
    }

    @Test
    fun sharingAWaitPickOffersOnlyAlways() =
        runTest {
            api.planResponse = api.planResponse?.copy(trips = listOf(ic1))
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            assertIs<Pick.Wait>(assertIs<OptionsUi.Ready>(app.options.ui.value).view.pick)
            app.openShare()
            assertEquals(ShareScope.ALWAYS, app.share.ui.value.scope)
            assertFalse(app.share.ui.value.canUseThisDisruption)
        }

    @Test
    fun routesNeedBothStationNumbers() =
        runTest {
            val app = app()
            state(fire)
            val query = assertNotNull(app.plan.ui.value.query)
            app.options.search(query.copy(to = query.to.copy(ref = "ch:1:x", didok = null)))
            runCurrent()
            val ready = assertIs<OptionsUi.Ready>(app.options.ui.value)
            assertTrue(ready.routes.isEmpty())
            assertTrue(api.calls.none { it.startsWith("routes") })
        }

    @Test
    fun liveModeJudgesTheTakenTripByThePlanAnswer() =
        runTest {
            // Live: /state pushes no disruptions; the fire only comes with the /plan answer.
            api.clock = api.clock.copy(mode = Mode.LIVE, scenarioId = null)
            api.planResponse = api.planResponse?.copy(trips = listOf(ic1), disruptions = listOf(fire))
            val app = app()
            state()
            app.plan.prefill(FakeApi.SCENARIO)
            app.findMyWay()
            runCurrent()
            app.takeTrip(ic1)
            runCurrent()
            val trip = assertNotNull(app.trip.ui.value)
            assertIs<ch.ausweg.shared.model.Advice.Wait>(trip.advice)
            assertEquals(setOf(fire.id), trip.watched)
        }

    @Test
    fun replayNeverBringsBackTheStalePlanCopy() =
        runTest {
            // Historical: planned during the fire; an hour after it ended the server drops it (empty list).
            api.planResponse = api.planResponse?.copy(trips = listOf(ic1), disruptions = listOf(fire))
            val app = app()
            state(fire)
            app.findMyWay()
            runCurrent()
            app.takeTrip(ic1)
            runCurrent()
            state(fire.copy(status = ch.ausweg.shared.model.DisruptionStatus.ENDED))
            assertEquals(
                UpdateKind.RESOLVED,
                app.trip.ui.value
                    ?.updates
                    ?.first()
                    ?.kind,
            )
            state()
            val trip = assertNotNull(app.trip.ui.value)
            assertIs<ch.ausweg.shared.model.Advice.Ok>(trip.advice)
            assertTrue(trip.updates.none { it.kind == UpdateKind.BLOCKED })
            assertFalse(app.session.isLive)
        }

    @Test
    fun planDisruptionsStandInOnlyWhenLive() {
        assertEquals(listOf(fire), currentDisruptions(emptyList(), listOf(fire), live = true))
        assertEquals(emptyList(), currentDisruptions(emptyList(), listOf(fire), live = false))
        val pushed = listOf(fire.copy(version = 9))
        assertEquals(pushed, currentDisruptions(pushed, listOf(fire), live = true))
    }

    @Test
    fun bannersGetUniqueIdsAfterSeeks() {
        // Seek to the fire, back before it, to it again: two BLOCKED banners with the same time and version.
        val start = TripWatch(Taken.Planned(ic1, emptyList()), emptySet(), advice = null, disruptions = emptyList())
        val ok = next(start, emptyList(), t("14:30"))
        val blocked = next(ok, listOf(fire), t("14:30"))
        val ended = fire.copy(status = ch.ausweg.shared.model.DisruptionStatus.ENDED)
        val clear = next(blocked, listOf(ended), t("14:30"))
        val again = next(clear, listOf(fire), t("14:30"))
        val ids = again.updates.map { it.id }
        assertTrue(again.updates.count { it.kind == UpdateKind.BLOCKED } >= 2)
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun communityTabVotes() =
        runTest {
            val app = app()
            state(fire)
            assertEquals(listOf(FakeApi.TIP), app.community.ui.value.tips)
            app.community.vote("t1", VoteValue.UP)
            runCurrent()
            assertEquals(
                1,
                app.community.ui.value.tips
                    .single()
                    .tip.votes.up,
            )
        }

    @Test
    fun languageIsPickedAndCycled() =
        runTest {
            val settings = MemorySettings()
            val app = AuswegApp(api, settings, backgroundScope)
            val langs = listOf("en", "fr", "de", "it")
            app.initLanguage(langs, listOf("fr-CH"))
            assertEquals("fr", app.language.value)
            app.cycleLanguage(langs)
            assertEquals("de", app.language.value)
            assertEquals("de", settings[SettingKeys.LANGUAGE])
        }

    private companion object {
        val TIMELINE =
            listOf(
                TimelineEntry(fire.id, t("14:19"), InfoSource.INTERNAL, "INTERRUPTION", "Trains turn back"),
                TimelineEntry(fire.id, t("14:27"), InfoSource.PUBLIC, "INTERRUPTION", "Line interrupted"),
            )
    }
}
