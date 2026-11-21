package ch.ausweg.app.screens.trip

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.AppCard
import ch.ausweg.app.components.EmptyState
import ch.ausweg.app.components.Gap
import ch.ausweg.app.components.LegStripView
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.StopTimeline
import ch.ausweg.app.components.Tag
import ch.ausweg.app.components.TimelineList
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.components.UpdateBanner
import ch.ausweg.app.components.WarningCard
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.i18n.formatUntil
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.routing.Stage
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.Tab
import ch.ausweg.shared.state.TripUi

/** 5 · Live trip: update banners, next step, the strip, how the disruption developed. Also the Trips tab. */
@Composable
fun TripScreen(app: AuswegApp) {
    val ui by app.trip.ui.collectAsState()
    val trip = ui
    if (trip == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(t("trip.title"))
            EmptyState(t("trip.empty"), action = t("trip.plan_one"), onAction = { app.navigator.select(Tab.PLAN) })
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            t("trip.title"),
            subtitle = trip.from?.let { f -> trip.to?.let { t("trip.from_to", "from" to f.name, "to" to it.name) } },
            actions = {
                TextButton(
                    { app.trip.end() },
                ) { Text(t("trip.end"), color = MaterialTheme.colorScheme.primary) }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { Gap(16) }
            items(trip.updates, key = { it.kind.name + it.at + it.disruption?.version }) { u ->
                UpdateBanner(u, { app.trip.dismiss(u) })
                Gap()
            }
            val advice = trip.advice
            if (advice != null && advice !is Advice.Ok) {
                item {
                    val until = (advice as? Advice.Wait)?.let { formatUntil(it.until, it.endType, strings()) }
                    WarningCard(listOfNotNull(t(I18nKeys.adviceReason(advice.reason)), until).joinToString(" "))
                    Gap()
                }
            }
            item { NextCard(trip) }
            item { SectionLabel(t("trip.timeline")) }
            item {
                if (trip.timeline.isEmpty()) {
                    Text(
                        t("trip.timeline_empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TimelineList(trip.timeline)
                }
            }
            item { SectionLabel(t("trip.stops"), action = t("trip.share"), onAction = { app.openShare() }) }
            item { AppCard { StopTimeline(trip.strip) } }
            item { Gap(24) }
        }
    }
}

/** «Next: change at Vevey» with the on-time tag, the strip (current ride in red) and the change details. */
@Composable
private fun NextCard(trip: TripUi) {
    val p = trip.progress
    val change = p.nextChange
    val title = nextTitle(trip)
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                val delay = p.delayMinutes
                when {
                    delay == null || p.stage == Stage.ARRIVED -> Unit
                    delay == 0 -> Tag(t("trip.on_time"), Extra.colors.good, Extra.colors.onGood)
                    else -> Tag(t("trip.late", "n" to delay), Extra.colors.warning, Extra.colors.onWarning)
                }
            }
            LegStripView(trip.strip, highlight = p.currentLeg.takeIf { p.stage != Stage.UNKNOWN })
            if (change != null) {
                val leg = trip.strip.legs.getOrNull(change.afterLeg)
                val parts =
                    listOfNotNull(
                        leg?.arrival?.let {
                            t(
                                "trip.arrive_at",
                                "station" to change.station.name,
                                "time" to SwissTime.hhmm(it),
                            )
                        },
                        if (change.arrivalPlatform != null && change.departurePlatform != null) {
                            t("trip.platforms", "from" to change.arrivalPlatform, "to" to change.departurePlatform)
                        } else {
                            null
                        },
                        change.minutes?.let { t("stops.minutes_to_change", "n" to it) },
                    )
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** «Next: change at Vevey», «Next: leave Lausanne at 16:40», … */
@Composable
private fun nextTitle(trip: TripUi): String {
    val p = trip.progress
    val change = p.nextChange
    val departure = trip.strip.departure
    return when {
        p.stage == Stage.ARRIVED -> t("trip.next_arrived")
        p.stage == Stage.BEFORE_DEPARTURE && departure != null ->
            t(
                "trip.next_leave",
                "station" to
                    trip.strip.from
                        ?.name
                        .orEmpty(),
                "time" to SwissTime.hhmm(departure),
            )
        change != null -> t("trip.next_change", "station" to change.station.name)
        else ->
            t(
                "trip.next_arrive",
                "station" to
                    trip.strip.to
                        ?.name
                        .orEmpty(),
            )
    }
}
