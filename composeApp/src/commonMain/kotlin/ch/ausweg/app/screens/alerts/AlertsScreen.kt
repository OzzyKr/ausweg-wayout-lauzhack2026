package ch.ausweg.app.screens.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.DisruptionCard
import ch.ausweg.app.components.EmptyState
import ch.ausweg.app.components.Gap
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.i18n.t
import ch.ausweg.shared.state.AuswegApp

/** Alerts tab: every disruption known now, with its full published text. */
@Composable
fun AlertsScreen(app: AuswegApp) {
    val alerts by app.alerts.collectAsState()
    val now by app.session.now.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("alerts.title"), subtitle = t("alerts.subtitle"))
        if (alerts.isEmpty()) {
            EmptyState(t("plan.no_disruptions"))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { Gap(16) }
                items(alerts, key = { it.disruption.id }) {
                    DisruptionCard(it.disruption, it.affectsYou, now, maxBodyLines = Int.MAX_VALUE)
                    Gap()
                }
            }
        }
    }
}
