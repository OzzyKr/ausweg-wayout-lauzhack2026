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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.AppCard
import ch.ausweg.app.components.LegStripView
import ch.ausweg.app.components.PrimaryButton
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.Tag
import ch.ausweg.app.components.TopBar
import ch.ausweg.app.i18n.t
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.NOTE_MAX_LENGTH
import ch.ausweg.shared.state.ShareScope
import ch.ausweg.shared.state.ShareStatus
import ch.ausweg.shared.tips.StripLeg

/** 4 · Share a route: pick the rides you took, pin notes on changes, «this disruption» or «always», post. */
@Composable
fun ShareScreen(app: AuswegApp) {
    val ui by app.share.ui.collectAsState()
    val strip = ui.strip
    Column(Modifier.fillMaxSize()) {
        TopBar(t("share.title"), onBack = { app.back() }, close = true, backLabel = t("common.close"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel(t("share.step_pick"))
            AppCard {
                Column {
                    ui.legs.forEachIndexed { i, leg ->
                        LegRow(leg, ui.checked.getOrElse(i) { false }) { app.share.toggle(i) }
                        if (i < ui.legs.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
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
            if (ui.hasGap) {
                Text(t("share.gap"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
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

@Composable
private fun LegRow(
    leg: StripLeg,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked,
            { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Extra.colors.brand, checkmarkColor = Color.White),
        )
        Tag(leg.label ?: t("strip.bus"), Extra.colors.chip, Extra.colors.onChip)
        val dep = leg.departure?.let { " " + SwissTime.hhmm(it) }.orEmpty()
        val arr = leg.arrival?.let { " " + SwissTime.hhmm(it) }.orEmpty()
        Text(
            t("share.leg", "from" to leg.from.name + dep, "to" to leg.to.name + arr),
            Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
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
