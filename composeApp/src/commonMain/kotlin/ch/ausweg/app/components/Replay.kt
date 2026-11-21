package ch.ausweg.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_check
import ch.ausweg.app.resources.ic_history
import ch.ausweg.app.resources.ic_live
import ch.ausweg.app.resources.ic_pause
import ch.ausweg.app.resources.ic_play
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.i18n.formatDate
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.state.replayFraction
import ch.ausweg.shared.state.replayInstant
import kotlin.time.Instant

/** Dark replay bar for the stage demo: time, play / pause, seek, speed. */
@Composable
fun ReplayBar(
    clock: ClockState,
    scenario: Scenario?,
    now: Instant?,
    onTogglePlay: () -> Unit,
    onSeek: (Instant) -> Unit,
    onSpeed: () -> Unit,
) {
    val at = now ?: clock.now
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.let { f -> scenario?.let { replayInstant(it, f) } } ?: at
    val c = Extra.colors
    Surface(color = c.banner) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("replay.label").uppercase(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onBanner.copy(alpha = 0.75f),
                )
                Text(
                    t("replay.when", "date" to formatDate(shown, strings()), "time" to SwissTime.hhmm(shown)),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.onBanner,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = onTogglePlay, shape = CircleShape, color = c.brand, modifier = Modifier.size(44.dp)) {
                    Ic(
                        if (clock.playing) Res.drawable.ic_pause else Res.drawable.ic_play,
                        if (clock.playing) t("replay.pause") else t("replay.play"),
                        Modifier.padding(10.dp),
                        tint = Color.White,
                    )
                }
                Slider(
                    value = dragging ?: scenario?.let { replayFraction(it, at) } ?: 0f,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        val f = dragging
                        if (f != null && scenario != null) onSeek(replayInstant(scenario, f))
                        dragging = null
                    },
                    enabled = scenario != null,
                    modifier = Modifier.weight(1f),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = c.brand,
                            activeTrackColor = c.brand,
                            inactiveTrackColor = Color.White.copy(alpha = 0.85f),
                        ),
                )
                Surface(
                    onClick = onSpeed,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(44.dp).border(1.dp, c.onBanner.copy(alpha = 0.5f), CircleShape),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t("replay.speed", "x" to clock.speed.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.onBanner,
                        )
                    }
                }
            }
        }
    }
}

/** Bottom sheet to switch between live mode and a replayed disruption. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(
    clock: ClockState?,
    scenarios: List<Scenario>,
    onLive: () -> Unit,
    onScenario: (Scenario) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismiss) {
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item {
                Text(t("mode.title"), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                ModeRow(
                    t("mode.live"),
                    t("mode.live_sub"),
                    Res.drawable.ic_live,
                    clock?.mode == Mode.LIVE,
                ) { onLive() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    t("mode.replay_title").uppercase(),
                    Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(scenarios, key = { it.id }) { s ->
                ModeRow(
                    s.title,
                    t("mode.replay_sub", "date" to formatDate(s.start, strings()), "time" to SwissTime.hhmm(s.start)),
                    Res.drawable.ic_history,
                    clock?.mode == Mode.HISTORICAL && clock.scenarioId == s.id,
                ) { onScenario(s) }
            }
            if (scenarios.isEmpty()) {
                item { EmptyState(t("mode.no_scenarios")) }
            }
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ic(icon, null, tint = if (selected) Extra.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Ic(
                    Res.drawable.ic_check,
                    t("mode.selected"),
                    Modifier.background(Extra.colors.warning, CircleShape).padding(4.dp),
                    tint = Extra.colors.brand,
                    size = 16,
                )
            }
        }
    }
}
