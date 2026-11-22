package ch.ausweg.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_add
import ch.ausweg.app.resources.ic_edit
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.StripKind
import ch.ausweg.shared.tips.StripLeg

private const val MIN_WEIGHT = 12f
private const val DEFAULT_WEIGHT = 30f

/**
 * The leg strip: one rectangle per ride (solid for trains, outlined for buses, dashed for replacement buses),
 * dotted connectors for changes, numbered red badges for notes pinned on a change.
 *
 * @param highlight ride drawn in red (the one the traveller is on).
 * @param editChange when set, every change is a button (share screen); [selectedChange] is drawn as selected and
 *   [noted] changes show a pencil.
 */
@Composable
fun LegStripView(
    strip: LegStrip,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    editChange: ((Int) -> Unit)? = null,
    selectedChange: Int? = null,
    noted: Set<Int> = emptySet(),
) {
    Row(
        modifier.fillMaxWidth().height(
            if (editChange !=
                null
            ) {
                44.dp
            } else {
                30.dp
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strip.legs.forEachIndexed { i, leg ->
            LegChip(leg, i == highlight, Modifier.weight(maxOf(leg.minutes?.toFloat() ?: DEFAULT_WEIGHT, MIN_WEIGHT)))
            val change = strip.changes.getOrNull(i)
            if (change != null) {
                if (editChange != null) {
                    ChangeButton(i == selectedChange, i in noted) { editChange(i) }
                } else {
                    Connector(change.notes.map { it.number })
                }
            }
        }
    }
}

@Composable
private fun LegChip(
    leg: StripLeg,
    highlighted: Boolean,
    modifier: Modifier,
) {
    val c = Extra.colors
    val shape = RoundedCornerShape(6.dp)
    val highlightedTrain = highlighted && leg.kind == StripKind.TRAIN
    val fg = if (highlightedTrain) c.onChip else MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.onSurface
    val label = rideLabel(leg)
    Box(
        modifier
            .height(30.dp)
            .then(
                when {
                    highlightedTrain -> Modifier.background(c.brand, shape).padding(horizontal = 8.dp)
                    leg.kind == StripKind.REPLACEMENT_BUS ->
                        Modifier.drawBehind {
                            drawRoundRect(
                                outline,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style =
                                    Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                                    ),
                            )
                        }
                    leg.kind == StripKind.BUS -> Modifier.border(1.5.dp, outline, shape)
                    else -> Modifier
                },
            ).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textDecoration = if (leg.cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

/** Dotted line between two rides, with the numbers of the notes pinned there. */
@Composable
private fun Connector(numbers: List<Int>) {
    val dots = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.width(if (numbers.isEmpty()) 18.dp else 26.dp).height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                dots,
                Offset(2f, size.height / 2),
                Offset(size.width - 2f, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
            )
        }
        if (numbers.isNotEmpty()) {
            Row(Modifier.offset(y = (-12).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                numbers.forEach { NoteBadge(it) }
            }
        }
    }
}

/** Red numbered circle for a note (strip and stop list). */
@Composable
fun NoteBadge(number: Int) {
    Box(Modifier.size(18.dp).background(Extra.colors.brand, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** A change on the share screen: «+» to add a note, a pencil when it has one, red ring when selected. */
@Composable
private fun ChangeButton(
    selected: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
) {
    val red = Extra.colors.brand
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(40.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, red, CircleShape)
                } else {
                    Modifier.drawBehind {
                        drawCircle(
                            outline,
                            radius = size.minDimension / 2 - 1.dp.toPx(),
                            style =
                                Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        )
                    }
                },
            ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(
            if (hasNote || selected) Res.drawable.ic_edit else Res.drawable.ic_add,
            t("share.add_note"),
            tint = if (selected) red else outline,
            size = 18,
        )
    }
}

/**
 * The station row under a strip: departure (with platform) on the left, change stations in between, arrival on
 * the right.
 */
@Composable
fun StripStations(
    strip: LegStrip,
    modifier: Modifier = Modifier,
) {
    val from = strip.from ?: return
    val to = strip.to ?: return
    val platform = strip.legs.firstOrNull()?.departurePlatform
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall
    Row(modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (platform !=
                null
            ) {
                t("strip.station_platform", "station" to from.name, "platform" to platform)
            } else {
                from.name
            },
            style = style,
            color = muted,
            maxLines = 1,
        )
        strip.changes.forEach { Text(it.station.name, style = style, color = muted, maxLines = 1) }
        Text(to.name, style = style, color = muted, maxLines = 1)
    }
}

/** The line on a ride's chip, or «Train» / «Bus» when it has none (a shared ride the user didn't name). */
@Composable
fun rideLabel(leg: StripLeg): String =
    leg.label?.takeIf { it.isNotBlank() } ?: when (leg.kind) {
        StripKind.OTHER -> "·"
        StripKind.TRAIN -> t("strip.train")
        StripKind.BUS, StripKind.REPLACEMENT_BUS -> t("strip.bus")
    }
