package ch.ausweg.app.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_bell
import ch.ausweg.app.resources.ic_close
import ch.ausweg.app.resources.ic_warning
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.disruption.RouteUpdate
import ch.ausweg.shared.disruption.UpdateKind
import ch.ausweg.shared.disruption.isBlocking
import ch.ausweg.shared.disruption.text
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.i18n.formatAgo
import ch.ausweg.shared.i18n.formatUntil
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.TimelineEntry
import ch.ausweg.shared.model.summary
import kotlin.time.Instant

/** The disruption's title in its published language (never translated), or a generic label. */
@Composable
fun disruptionTitle(d: Disruption?): String = d?.text(strings().language)?.title ?: t("disruption.untitled")

/** One disruption: title, the latest published text, «Affects your trip», expected end, last update. */
@Composable
fun DisruptionCard(
    d: Disruption,
    affectsYou: Boolean,
    now: Instant?,
    modifier: Modifier = Modifier,
    maxBodyLines: Int = 3,
) {
    val text = d.text(strings().language)
    val ended = d.status == DisruptionStatus.ENDED
    AppCard(modifier) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ic(
                Res.drawable.ic_warning,
                null,
                tint = if (d.isBlocking && !ended) Extra.colors.brand else MaterialTheme.colorScheme.onSurface,
                size = 20,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(disruptionTitle(d), style = MaterialTheme.typography.titleSmall)
                val body = text?.body.orEmpty()
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = maxBodyLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (affectsYou &&
                        !ended
                    ) {
                        Tag(t("disruption.affects_you"), Extra.colors.warning, Extra.colors.onWarning)
                    }
                    val status =
                        when {
                            ended -> t("disruption.ended")
                            d.expectedEnd != null || d.isBlocking ->
                                formatUntil(d.expectedEnd, d.expectedEndType, strings())
                            else -> null
                        }
                    val updated = now?.let { t("disruption.updated", "ago" to formatAgo(d.publishedAt, it, strings())) }
                    Text(
                        listOfNotNull(status, updated).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The text of an update banner. */
@Composable
fun updateText(u: RouteUpdate): String {
    val d = u.disruption
    val main =
        t(
            I18nKeys.update(u.kind),
            "title" to disruptionTitle(d),
            "until" to formatUntil(d?.expectedEnd, d?.expectedEndType, strings()),
        )
    val advice = u.advice
    val extra =
        if (advice != null && advice !is Advice.Ok && u.kind in setOf(UpdateKind.BLOCKED, UpdateKind.ADVICE_CHANGED)) {
            " " + t(I18nKeys.adviceReason(advice.reason))
        } else {
            ""
        }
    return main + extra
}

/** Dark «New update · 16:45» banner. */
@Composable
fun UpdateBanner(
    update: RouteUpdate,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Extra.colors
    Surface(modifier.fillMaxWidth(), color = c.banner, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ic(Res.drawable.ic_bell, null, tint = c.onBanner, size = 20)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    t("update.header", "time" to SwissTime.hhmm(update.at)),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.bannerAccent,
                )
                Text(updateText(update), style = MaterialTheme.typography.bodyMedium, color = c.onBanner)
            }
            IconButton(onDismiss) { Ic(Res.drawable.ic_close, t("common.dismiss"), tint = c.onBanner, size = 18) }
        }
    }
}

/** «How the disruption developed»: newest first, the newest in red, with where it came from. */
@Composable
fun TimelineList(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            entries.forEachIndexed { i, e ->
                val first = i == 0
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        SwissTime.hhmm(e.t),
                        Modifier.width(44.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (first) FontWeight.Bold else FontWeight.Normal,
                        color = if (first) Extra.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(
                                    if (first) Extra.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant,
                                    CircleShape,
                                ),
                        )
                        if (i < entries.lastIndex) {
                            Box(Modifier.width(2.dp).height(44.dp).background(MaterialTheme.colorScheme.outline))
                        }
                    }
                    Column(
                        Modifier.weight(1f).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            e.summary(strings().language),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Tag(
                            t(I18nKeys.infoSource(e.source)),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Red-tinted card for «your usual route is interrupted …». */
@Composable
fun WarningCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = Extra.colors.warning, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ic(Res.drawable.ic_warning, null, tint = Extra.colors.onWarning, size = 22)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Extra.colors.onWarning)
        }
    }
}
