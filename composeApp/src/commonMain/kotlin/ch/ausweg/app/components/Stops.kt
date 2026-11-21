package ch.ausweg.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.t
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.model.Location
import ch.ausweg.shared.state.StopInput
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.NumberedNote
import kotlin.time.Instant

/** «From · you are here / Lausanne»: a label over a borderless text field, with suggestions below. */
@Composable
fun StopField(
    label: String,
    input: StopInput,
    onType: (String) -> Unit,
    onChoose: (Location) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = input.query,
                    onValueChange = onType,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    decorationBox = { inner ->
                        if (input.query.isEmpty()) {
                            Text(
                                t("plan.stop_hint"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    },
                )
            }
            trailing()
        }
        if (input.selected == null && input.suggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Column {
                    input.suggestions.take(MAX_SUGGESTIONS).forEach { loc ->
                        Surface(onClick = { onChoose(loc) }, color = Color.Transparent) {
                            Text(
                                loc.stop.name,
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 6

/**
 * The vertical stop list of a trip or route: each ride's boarding stop (time, station · platform, line towards
 * direction), the notes pinned on each change, and the destination in red.
 */
@Composable
fun StopTimeline(
    strip: LegStrip,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        strip.legs.forEachIndexed { i, leg ->
            val change = strip.changes.getOrNull(i)
            StopLine(
                time = leg.departure,
                title =
                    leg.departurePlatform?.let {
                        t(
                            "stops.station_platform",
                            "station" to leg.from.name,
                            "platform" to it,
                        )
                    }
                        ?: leg.from.name,
                subtitle =
                    when {
                        leg.label != null && leg.direction != null ->
                            t("stops.line_towards", "line" to leg.label, "direction" to leg.direction)
                        else -> leg.label ?: t("strip.bus")
                    },
                dot = DotKind.STOP,
                lineBelow = true,
            )
            if (change != null) {
                val platforms =
                    if (change.arrivalPlatform != null && change.departurePlatform != null) {
                        t(
                            "stops.change_platforms",
                            "station" to change.station.name,
                            "from" to change.arrivalPlatform,
                            "to" to change.departurePlatform,
                        )
                    } else {
                        change.station.name
                    }
                StopLine(
                    leg.arrival,
                    platforms,
                    change.minutes?.let { t("stops.minutes_to_change", "n" to it) },
                    DotKind.STOP,
                    true,
                )
                change.notes.forEach { NoteCard(it) }
            }
        }
        val last = strip.legs.lastOrNull()
        if (last != null) StopLine(last.arrival, last.to.name, null, DotKind.END, false)
    }
}

private enum class DotKind { STOP, END }

@Composable
private fun StopLine(
    time: Instant?,
    title: String,
    subtitle: String?,
    dot: DotKind,
    lineBelow: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            time?.let { SwissTime.hhmm(it) }.orEmpty(),
            Modifier.width(44.dp).padding(top = 1.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .then(
                        if (dot == DotKind.END) {
                            Modifier.background(Extra.colors.brand, CircleShape)
                        } else {
                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        },
                    ),
            )
            if (lineBelow) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(
                            if (subtitle !=
                                null
                            ) {
                                34.dp
                            } else {
                                22.dp
                            },
                        ).background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A numbered note card pinned on a change. */
@Composable
fun NoteCard(note: NumberedNote) {
    Row(Modifier.padding(start = 56.dp, top = 4.dp, bottom = 10.dp)) {
        AppCard {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NoteBadge(note.number)
                Text(note.tip.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
