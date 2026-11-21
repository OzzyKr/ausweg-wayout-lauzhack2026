package ch.ausweg.app.screens.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.EmptyState
import ch.ausweg.app.components.EndorsedChip
import ch.ausweg.app.components.LegStripView
import ch.ausweg.app.components.PrimaryButton
import ch.ausweg.app.components.StopTimeline
import ch.ausweg.app.components.StripStations
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.components.ValidityVote
import ch.ausweg.app.components.VoteButtons
import ch.ausweg.app.components.matchText
import ch.ausweg.app.components.viaTitle
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.shared.i18n.formatAgo
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.state.AuswegApp

/** 3 · Community route: strip with numbered notes, stop list, votes, «Take this route». */
@Composable
fun RouteScreen(app: AuswegApp) {
    val ui by app.route.ui.collectAsState()
    val now by app.session.now.collectAsState()
    val r = ui
    if (r == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(t("route.title_empty"), onBack = { app.back() }, backLabel = t("common.back"))
            EmptyState(t("route.missing"))
        }
        return
    }
    val route = r.ranked.route
    Column(Modifier.fillMaxSize()) {
        TopBar(
            viaTitle(r.strip),
            subtitle =
                t(
                    "route.shared_by",
                    "ago" to (now?.let { formatAgo(route.createdAt, it, strings()) } ?: ""),
                ),
            onBack = { app.back() },
            backLabel = t("common.back"),
        )
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                LegStripView(r.strip, Modifier.padding(top = 8.dp))
                StripStations(r.strip)
                Text(
                    matchText(r.ranked.match, route.createdAt),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StopTimeline(r.strip)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (r.ranked.match == CommunityMatch.SIMILAR_DISRUPTION) {
                    ValidityVote(route.votes, r.myVote) { app.route.vote(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f)) { if (route.staffEndorsed) EndorsedChip(t("community.endorsed")) }
                    VoteButtons(route.votes, r.myVote) { app.route.vote(it) }
                }
                PrimaryButton(t("route.take"), { app.takeRoute() })
            }
        }
    }
}
