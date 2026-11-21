package ch.ausweg.app.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.AppCard
import ch.ausweg.app.components.DisruptionCard
import ch.ausweg.app.components.Gap
import ch.ausweg.app.components.HeaderControls
import ch.ausweg.app.components.Ic
import ch.ausweg.app.components.LocalWide
import ch.ausweg.app.components.Logo
import ch.ausweg.app.components.PrimaryButton
import ch.ausweg.app.components.SectionLabel
import ch.ausweg.app.components.StopField
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_clock
import ch.ausweg.app.resources.ic_swap
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.api.LinkStatus
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.Tab
import kotlin.time.Instant
import ch.ausweg.shared.state.StopField as Field

/** How many disruptions the phone plan screen lists before «See all» (the wide layout shows them all). */
private const val DISRUPTIONS_SHOWN = 3

/** Widest the wide plan page gets. */
private const val WIDE_MAX = 1120

/** 1 · Plan a trip. Phone: red header with the card overlapping it. Wide: hero band, search left, disruptions right. */
@Composable
fun PlanScreen(
    app: AuswegApp,
    languages: List<String>,
) {
    if (LocalWide.current) WidePlan(app) else PhonePlan(app, languages)
}

@Composable
private fun PhonePlan(
    app: AuswegApp,
    languages: List<String>,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Extra.colors.brand)
                    .padding(start = 20.dp, end = 16.dp, top = 20.dp)
                    .height(190.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(Modifier.weight(1f))
                    HeaderControls(app, languages)
                }
                Text(
                    t("plan.tagline"),
                    Modifier.padding(top = 14.dp, end = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
            SearchCard(app, Modifier.padding(start = 16.dp, end = 16.dp, top = 140.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Disruptions(app, DISRUPTIONS_SHOWN)
            Gap(24)
        }
    }
}

@Composable
private fun WidePlan(app: AuswegApp) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Extra.colors.brand), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = WIDE_MAX.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp)) {
                Text(t("plan.hero"), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    t("plan.tagline"),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.widthIn(max = WIDE_MAX.dp).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                SearchCard(app, Modifier.width(420.dp).offset(y = (-20).dp))
                Column(Modifier.weight(1f)) {
                    Disruptions(app, Int.MAX_VALUE)
                    Gap(32)
                }
            }
        }
    }
}

/** From / to, «Leave now · Change», «Find my way». */
@Composable
private fun SearchCard(
    app: AuswegApp,
    modifier: Modifier,
) {
    val ui by app.plan.ui.collectAsState()
    val now by app.session.now.collectAsState()
    var timePicker by remember { mutableStateOf(false) }
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            StopField(t("plan.from"), ui.from, { app.plan.type(Field.FROM, it) }, { app.plan.choose(Field.FROM, it) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StopField(t("plan.to"), ui.to, { app.plan.type(Field.TO, it) }, { app.plan.choose(Field.TO, it) }) {
                IconButton(
                    { app.plan.swap() },
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) { Ic(Res.drawable.ic_swap, t("plan.swap")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(Res.drawable.ic_clock, null, size = 20)
                val at = ui.departAt
                Text(
                    if (at == null) t("plan.leave_now") else t("plan.leave_at", "time" to SwissTime.hhmm(at)),
                    Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton({ timePicker = true }) { Text(t("plan.change"), color = MaterialTheme.colorScheme.primary) }
            }
            PrimaryButton(
                t("plan.find"),
                { app.findMyWay() },
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                enabled = ui.query != null,
            )
        }
    }
    if (timePicker) {
        LeaveAtDialog(
            start = ui.departAt ?: now ?: app.session.currentNow(),
            onNow = {
                app.plan.leaveAt(null)
                timePicker = false
            },
            onPick = { h, m ->
                app.plan.leaveAt(SwissTime.at(now ?: app.session.currentNow(), h, m))
                timePicker = false
            },
            onDismiss = { timePicker = false },
        )
    }
}

/** «Disruptions now» with the first [max] cards and «See all». */
@Composable
private fun Disruptions(
    app: AuswegApp,
    max: Int,
) {
    val now by app.session.now.collectAsState()
    val alerts by app.alerts.collectAsState()
    val link by app.session.link.collectAsState()
    if (link == LinkStatus.OFFLINE) {
        Gap()
        Text(t("plan.offline"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    SectionLabel(t("plan.disruptions_now"), action = t("plan.see_all"), onAction = { app.navigator.select(Tab.ALERTS) })
    if (alerts.isEmpty()) {
        Text(
            t("plan.no_disruptions"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    alerts.take(max).forEach {
        DisruptionCard(it.disruption, it.affectsYou, now, maxBodyLines = 2)
        Gap()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveAtDialog(
    start: Instant,
    onNow: () -> Unit,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (h, m) = SwissTime.hourMinute(start)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("plan.leave_at_title")) },
        text = {
            Box(
                Modifier.fillMaxWidth().offset(y = 8.dp),
                contentAlignment = Alignment.Center,
            ) { TimePicker(state) }
        },
        confirmButton = { TextButton({ onPick(state.hour, state.minute) }) { Text(t("common.ok")) } },
        dismissButton = { TextButton(onNow) { Text(t("plan.leave_now")) } },
    )
}
