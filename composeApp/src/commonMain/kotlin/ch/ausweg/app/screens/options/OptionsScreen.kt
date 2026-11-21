package ch.ausweg.app.screens.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.AppCard
import ch.ausweg.app.components.EmptyState
import ch.ausweg.app.components.Gap
import ch.ausweg.app.components.Ic
import ch.ausweg.app.components.LegStripView
import ch.ausweg.app.components.OtherTripCard
import ch.ausweg.app.components.RouteCard
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.StripStations
import ch.ausweg.app.components.TimesHeadline
import ch.ausweg.app.components.TipCard
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.components.TripSummary
import ch.ausweg.app.components.WarningCard
import ch.ausweg.app.components.disruptionTitle
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_bus
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.i18n.formatUntil
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.TripSource
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.routing.Pick
import ch.ausweg.shared.routing.trip
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.OptionsUi
import ch.ausweg.shared.state.query
import ch.ausweg.shared.tips.busStrip
import kotlinx.coroutines.launch

/** 2 · Your options: our pick, community routes, tips, other connections. */
@Composable
fun OptionsScreen(app: AuswegApp) {
    val ui by app.options.ui.collectAsState()
    val now by app.session.now.collectAsState()
    val query = ui.query
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title =
                query?.let { t("options.title", "from" to it.from.name, "to" to it.to.name) }
                    ?: t("options.title_empty"),
            subtitle =
                query?.let { q ->
                    val at = q.departAt
                    if (at == null) {
                        t("options.leave_now", "time" to (now?.let { SwissTime.hhmm(it) } ?: ""))
                    } else {
                        t("plan.leave_at", "time" to SwissTime.hhmm(at))
                    }
                },
            onBack = { app.back() },
            backLabel = t("common.back"),
        )
        when (val s = ui) {
            OptionsUi.Idle -> EmptyState(t("options.idle"))
            is OptionsUi.Loading -> EmptyState(t("options.loading"), loading = true)
            is OptionsUi.Failed ->
                EmptyState(
                    t(I18nKeys.loadError(s.error)),
                    action = t("common.retry"),
                    onAction = { app.options.retry() },
                )
            is OptionsUi.Ready -> ReadyContent(app, s)
        }
    }
}

/** The answer: blocked-route banner, our pick, community routes, tips, other connections. */
@Composable
private fun ReadyContent(
    app: AuswegApp,
    s: OptionsUi.Ready,
) {
    val mine by app.voting.mine.collectAsState()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Gap(16) }
        val view = s.view
        val blocking = view.blockedBy.firstOrNull()
        if (blocking != null) {
            item {
                WarningCard(
                    t(
                        "options.usual_blocked",
                        "title" to disruptionTitle(blocking),
                        "until" to formatUntil(blocking.expectedEnd, blocking.expectedEndType, strings()),
                    ),
                )
            }
        }
        if (s.response.source != TripSource.LIVE || s.response.rerouted) {
            item { SourceNote(s.response.source, s.response.rerouted) }
        }
        item { SectionLabel(t("options.our_pick")) }
        view.pick?.let { pick -> item { PickCard(pick, view.advice) { app.takeTrip(pick.trip) } } }
        view.warnings.forEach { w ->
            item {
                Text(
                    t("options.warning", "title" to disruptionTitle(w)),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel(
                t("options.from_others"),
                action = t("options.share_yours"),
                onAction = { app.openShare() },
            )
        }
        if (s.routes.isEmpty()) {
            item {
                Text(
                    t("options.no_routes"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(s.routes, key = { "route-" + it.route.id }) { r ->
            RouteCard(
                r,
                mine[r.route.id],
                onVoteUp = { scope.launch { app.voting.route(r.route.id, VoteValue.UP) } },
                onOpen = { app.openRoute(r) },
            )
            Gap()
        }
        if (s.tips.isNotEmpty()) item { SectionLabel(t("options.tips")) }
        items(s.tips, key = { "tip-" + it.tip.id }) { tip ->
            TipCard(tip, mine[tip.tip.id]) { v -> scope.launch { app.voting.tip(tip.tip.id, v) } }
            Gap()
        }
        if (view.others.isNotEmpty()) item { SectionLabel(t("options.other_trips")) }
        items(view.others, key = { "trip-" + it.trip.id }) { o ->
            OtherTripCard(o.trip, o.blocked) { app.takeTrip(o.trip) }
            Gap()
        }
        item { Gap(24) }
    }
}

/** Where the trips came from, when it isn't today's live timetable. */
@Composable
private fun SourceNote(
    source: TripSource,
    rerouted: Boolean,
) {
    val parts =
        listOfNotNull(
            when (source) {
                TripSource.SHIFTED -> t("options.source.shifted")
                TripSource.FIXTURE -> t("options.source.fixture")
                TripSource.LIVE -> null
            },
            if (rerouted) t("options.rerouted") else null,
        )
    Text(
        parts.joinToString(" "),
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** «Our pick»: a red-outlined card. Its look depends on the advice: go, wait, or replacement bus. */
@Composable
private fun PickCard(
    pick: Pick,
    advice: Advice?,
    onTake: () -> Unit,
) {
    AppCard(border = Extra.colors.brand) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (pick) {
                is Pick.Go -> TripSummary(pick.trip)
                is Pick.Wait -> {
                    TimesHeadline(t("options.wait", "until" to formatUntil(pick.until, pick.endType, strings())))
                    TripSummary(pick.trip)
                }
                is Pick.Bus -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Ic(Res.drawable.ic_bus, null)
                        TimesHeadline(t("options.bus"))
                    }
                    val bus = pick.bus
                    val details =
                        listOfNotNull(
                            bus.eta?.let { t("options.bus_ready", "time" to SwissTime.hhmm(it)) },
                            bus.count?.let { t("options.bus_count", "n" to it) },
                        )
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val strip = busStrip(bus, pick.trip)
                    LegStripView(strip)
                    StripStations(strip)
                }
            }
            advice?.let {
                Text(
                    t(I18nKeys.adviceReason(it.reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onTake) { Text(t("options.take_pick"), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
