package ch.ausweg.app.screens.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.AppCard
import ch.ausweg.app.components.Ic
import ch.ausweg.app.components.LegStripView
import ch.ausweg.app.components.PrimaryButton
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.StopField
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_add
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.NOTE_MAX_LENGTH
import ch.ausweg.shared.state.ShareScope
import ch.ausweg.shared.state.ShareState
import ch.ausweg.shared.state.ShareStatus
import ch.ausweg.shared.state.ShareUi
import ch.ausweg.shared.state.StopEdit
import ch.ausweg.shared.state.StopTarget
import ch.ausweg.shared.tips.StripLeg
import ch.ausweg.shared.tips.isBus

/** 4 · Share your route: change stops and add changes, name the lines, pin tips on changes, choose when, post. */
@Composable
fun ShareScreen(app: AuswegApp) {
    val ui by app.share.ui.collectAsState()
    val strip = ui.strip
    Column(Modifier.fillMaxSize()) {
        TopBar(t("share.title"), onBack = { app.back() }, close = true, backLabel = t("common.close"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel(t("share.step_pick"))
            AppCard { RouteEditor(ui, app.share) }
            if (ui.hasLoop) {
                Text(t("share.loop"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            }
            SectionLabel(t("share.step_note"))
            if (strip.changes.isEmpty()) {
                Text(
                    t("share.no_changes"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AppCard {
                    LegStripView(
                        strip,
                        Modifier.padding(12.dp),
                        editChange = { app.share.selectChange(it) },
                        selectedChange = ui.selectedChange,
                        noted = ui.notes.filterValues { it.isNotBlank() }.keys,
                    )
                }
                val selected = ui.selectedChange?.let { strip.changes.getOrNull(it) }
                if (selected != null) {
                    Text(
                        t("share.note_for", "station" to selected.station.name),
                        Modifier.padding(top = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = ui.notes[ui.selectedChange] ?: "",
                        onValueChange = { app.share.note(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        placeholder = { Text(t("share.note_hint")) },
                        supportingText = { Text(t("share.note_limit", "n" to NOTE_MAX_LENGTH)) },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
            SectionLabel(t("share.step_when"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScopeCard(
                    t("share.scope_this"),
                    if (ui.canUseThisDisruption) t("share.scope_this_sub") else t("share.scope_this_none"),
                    ui.scope == ShareScope.THIS_DISRUPTION,
                    ui.canUseThisDisruption,
                    Modifier.weight(1f),
                ) { app.share.scope(ShareScope.THIS_DISRUPTION) }
                ScopeCard(
                    t("share.scope_always"),
                    t("share.scope_always_sub"),
                    ui.scope == ShareScope.ALWAYS,
                    true,
                    Modifier.weight(1f),
                ) {
                    app.share.scope(ShareScope.ALWAYS)
                }
            }
            if (ui.status == ShareStatus.FAILED) {
                Text(t("share.failed"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface) {
            PrimaryButton(
                t("share.post"),
                { app.postShare() },
                Modifier.padding(16.dp),
                enabled = ui.canPost,
                busy = ui.status == ShareStatus.POSTING,
            )
        }
    }
}

/** Every stop of the route, with the ride after it: line name, train or bus, and «Add a change». */
@Composable
private fun RouteEditor(
    ui: ShareUi,
    share: ShareState,
) {
    val stops = ui.stops
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        stops.forEachIndexed { i, stop ->
            val label =
                when (i) {
                    0 -> t("share.stop_start")
                    stops.lastIndex -> t("share.stop_end")
                    else -> t("share.stop_change")
                }
            val edit = ui.editing
            if (edit != null && edit.target == StopTarget.Stop(i)) {
                StopSearch(label, edit, share)
            } else {
                StopRow(
                    label,
                    stop.name,
                    onChange = { share.search(StopTarget.Stop(i)) },
                    onRemove = if (i in 1 until stops.lastIndex) ({ share.removeChange(i) }) else null,
                )
            }
            ui.legs.getOrNull(i)?.let { leg ->
                RideRow(leg, onLine = { share.ride(i, line = it) }, onBus = { share.ride(i, bus = it) })
                if (edit != null && edit.target == StopTarget.NewChange(i)) {
                    StopSearch(t("share.new_change"), edit, share)
                } else if (ui.canAddChange) {
                    TextButton({ share.search(StopTarget.NewChange(i)) }, Modifier.padding(start = 4.dp)) {
                        Ic(Res.drawable.ic_add, null, tint = Extra.colors.brand, size = 18)
                        Text(t("share.add_change"), Modifier.padding(start = 6.dp), color = Extra.colors.brand)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    label: String,
    name: String,
    onChange: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onChange) { Text(t("share.change_stop")) }
        if (onRemove != null) {
            TextButton(onRemove) { Text(t("share.remove_stop"), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun StopSearch(
    label: String,
    edit: StopEdit,
    share: ShareState,
) {
    StopField(
        label,
        edit.input,
        onType = { share.typeStop(it) },
        onChoose = { share.chooseStop(it) },
        trailing = { TextButton({ share.search(null) }) { Text(t("share.cancel")) } },
    )
}

/** The ride between two stops: its line name and whether it was a train or a bus. */
@Composable
private fun RideRow(
    leg: StripLeg,
    onLine: (String) -> Unit,
    onBus: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = leg.label.orEmpty(),
            onValueChange = onLine,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(t("share.line_hint")) },
            shape = MaterialTheme.shapes.medium,
        )
        FilterChip(!leg.isBus, { onBus(false) }, label = { Text(t("share.train")) })
        FilterChip(leg.isBus, { onBus(true) }, label = { Text(t("share.bus")) })
    }
}

@Composable
private fun ScopeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    AppCard(
        modifier,
        border = if (selected) Extra.colors.brand else MaterialTheme.colorScheme.outlineVariant,
        onClick = if (enabled) onClick else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected,
                    if (enabled) onClick else null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = Extra.colors.brand),
                )
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
