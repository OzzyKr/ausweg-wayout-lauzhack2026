package ch.ausweg.app.screens.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.EmptyState
import ch.ausweg.app.components.Gap
import ch.ausweg.app.components.TipCard
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.i18n.t
import ch.ausweg.shared.state.AuswegApp

/** Community tab: tips that apply now (this disruption, similar ones, permanent transfer tips). */
@Composable
fun CommunityScreen(app: AuswegApp) {
    val ui by app.community.ui.collectAsState()
    val mine by app.voting.mine.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("community.title"), subtitle = t("community.subtitle"))
        when {
            ui.loading && ui.tips.isEmpty() -> EmptyState(t("community.loading"), loading = true)
            ui.tips.isEmpty() ->
                EmptyState(
                    if (ui.failed) t("community.failed") else t("community.empty"),
                    action = t("common.retry"),
                    onAction = { app.community.refresh() },
                )
            else ->
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item { Gap(16) }
                    items(ui.tips, key = { it.tip.id }) { tip ->
                        TipCard(tip, mine[tip.tip.id]) { app.community.vote(tip.tip.id, it) }
                        Gap()
                    }
                    item {
                        Text(
                            t("community.anonymous"),
                            Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }
    }
}
