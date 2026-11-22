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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import ch.ausweg.app.components.PrimaryButton
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.StopField
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.components.rideLabel
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

/** 4 · Share your route: the route with a tip per change, «Edit» to fix stops and lines, when to show it, post. */
@Composable
fun ShareScreen(app: AuswegApp) {
    val ui by app.share.ui.collectAsState()
    val share = app.share
    Column(Modifier.fillMaxSize()) {
        TopBar(t("share.title"), onBack = { app.back() }, close = true, backLabel = t("common.close"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel(
                t("share.step_pick"),
                action = if (ui.editingRoute) t("share.done") else t("share.edit_route"),
                onAction = { share.editRoute(!ui.editingRoute) },
            )
            AppCard {
                if (ui.editingRoute) RouteEditor(ui, share) else RouteView(ui, share)
            }
            if (ui.hasLoop) {
                Text(t("share.loop"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            }
            SectionLabel(t("share.step_when"))
            ScopeChoice(ui, share)
            if (ui.status == ShareStatus.FAILED) {
                Text(t("share.failed"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface) {
            PrimaryButton(
                if (ui.editingRoute) t("share.done") else t("share.post"),
                { if (ui.editingRoute) share.editRoute(false) else app.postShare() },
                Modifier.padding(16.dp),
                enabled = ui.canPost || (ui.editingRoute && ui.editing == null),
                busy = ui.status == ShareStatus.POSTING,
            )
        }
    }
}

/** The route as it will be shared: stops, the ride between them, and «Add a tip» on every change. */
@Composable
private fun RouteView(
    ui: ShareUi,
    share: ShareState,
) {
    val stops = ui.stops
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        stops.forEachIndexed { i, stop ->
            val change = ui.changeAt(i)
            val note = change?.let { ui.notes[it] }
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stop.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (change != null && note == null) {
                    TextButton({ share.note(change, "") }) {
                        Ic(Res.drawable.ic_add, null, tint = Extra.colors.brand, size = 18)
                        Text(t("share.add_note"), Modifier.padding(start = 6.dp), color = Extra.colors.brand)
                    }
                }
            }
            if (change != null && note != null) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { share.note(change, it) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).heightIn(min = 80.dp),
                    placeholder = { Text(t("share.note_hint")) },
                    supportingText = { Text(t("share.note_limit", "n" to NOTE_MAX_LENGTH)) },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            ui.legs.getOrNull(i)?.let { RideLine(it) }
        }
    }
}

/** «IC 5 · Train» under a stop: the ride to the next one. */
@Composable
private fun RideLine(leg: StripLeg) {
    val kind = if (leg.isBus) t("share.bus") else t("share.train")
    val text = leg.label?.takeIf { it.isNotBlank() }?.let { "$it · $kind" } ?: rideLabel(leg)
    Row(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "│  $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Edit mode: every stop with «Change» / «Remove», every ride with its line, train or bus, and «Add a change». */
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

/**
 * «Only during this disruption» / «Always», with one line saying what the selected one means. The choice shows when
 * the trip has a disruption; «only during» is off while the route still goes by train through it.
 */
@Composable
private fun ScopeChoice(
    ui: ShareUi,
    share: ShareState,
) {
    if (ui.disruption != null) {
        val options =
            listOf(ShareScope.THIS_DISRUPTION to t("share.scope_this"), ShareScope.ALWAYS to t("share.scope_always"))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (scope, label) ->
                SegmentedButton(
                    selected = ui.scope == scope,
                    onClick = { share.scope(scope) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    enabled = scope == ShareScope.ALWAYS || ui.canUseThisDisruption,
                ) { Text(label, maxLines = 2) }
            }
        }
    }
    Text(
        when {
            ui.scope == ShareScope.THIS_DISRUPTION -> t("share.scope_this_sub")
            ui.throughDisruption -> t("share.scope_this_through")
            else -> t("share.scope_always_sub")
        },
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
