package ch.ausweg.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.strings
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_check
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.i18n.SwissTime
import ch.ausweg.shared.i18n.formatDate
import ch.ausweg.shared.i18n.formatDuration
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.routing.duration
import ch.ausweg.shared.tips.LegStrip
import ch.ausweg.shared.tips.legStrip
import kotlin.time.Instant

/** «1 change», «2 changes», «direct». */
@Composable
fun changesText(n: Int): String =
    when (n) {
        0 -> t("trip.direct")
        1 -> t("trip.one_change")
        else -> t("trip.changes", "n" to n)
    }

/** «Shared during this disruption», «From a similar disruption on 12 Sep 2026», «Always useful». */
@Composable
fun matchText(
    match: CommunityMatch,
    createdAt: Instant,
): String = t(I18nKeys.communityMatch(match), "date" to formatDate(createdAt, strings()))

/** «Via Yverdon-les-Bains», or «Direct» for a route without changes. */
@Composable
fun viaTitle(strip: LegStrip): String =
    if (strip.changes.isEmpty()) {
        t("route.direct")
    } else {
        t("route.via", "stations" to strip.changes.joinToString(" · ") { it.station.name })
    }

/** «16:40 – 17:52» and «1 h 12 · 1 change» for a trip, then its strip. */
@Composable
fun TripSummary(
    trip: Trip,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
) {
    val strip = legStrip(trip)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimesHeadline(
                t("trip.times", "dep" to SwissTime.hhmm(trip.departure), "arr" to SwissTime.hhmm(trip.arrival)),
                Modifier.weight(1f),
            )
            Text(
                t(
                    "trip.duration_changes",
                    "duration" to formatDuration(trip.duration, strings()),
                    "changes" to changesText(trip.transfers),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LegStripView(strip, Modifier.padding(top = 12.dp), highlight = highlight)
        StripStations(strip)
    }
}

/** A community route in the options list: votes, via title, strip, endorsement, why it's shown. */
@Composable
fun RouteCard(
    ranked: RankedRoute,
    mine: VoteValue?,
    onVoteUp: () -> Unit,
    onOpen: () -> Unit,
) {
    val strip = legStrip(ranked.route, ranked.notes)
    AppCard(onClick = onOpen) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteColumn(ranked.route.votes, mine, onVoteUp)
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(viaTitle(strip), style = MaterialTheme.typography.titleSmall)
                LegStripView(strip)
                if (ranked.route.staffEndorsed) EndorsedChip(t("community.endorsed"))
                val meta =
                    listOfNotNull(
                        matchText(ranked.match, ranked.route.createdAt),
                        when (val n = ranked.notes.size) {
                            0 -> null
                            1 -> t("route.one_note")
                            else -> t("route.notes", "n" to n)
                        },
                    )
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A tip: its text (as written, in the writer's language), why it's shown, and votes. */
@Composable
fun TipCard(
    ranked: RankedTip,
    mine: VoteValue?,
    onVote: (VoteValue) -> Unit,
) {
    val tip = ranked.tip
    AppCard {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteColumn(tip.votes, mine) { onVote(VoteValue.UP) }
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tip.text, style = MaterialTheme.typography.bodyMedium)
                if (tip.staffEndorsed) EndorsedChip(t("community.endorsed"))
                val platforms =
                    if (tip.platformFrom != null && tip.platformTo != null) {
                        t("tip.platforms", "from" to tip.platformFrom, "to" to tip.platformTo)
                    } else {
                        null
                    }
                Text(
                    listOfNotNull(matchText(ranked.match, tip.createdAt), platforms).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ranked.match == CommunityMatch.SIMILAR_DISRUPTION) ValidityVote(tip.votes, mine, onVote)
            }
        }
    }
}

/** Another connection in the options list, with «Blocked» when it runs through the closed section. */
@Composable
fun OtherTripCard(
    trip: Trip,
    blocked: Boolean,
    onTake: () -> Unit,
) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            TripSummary(trip)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (blocked) Tag(t("trip.blocked"), Extra.colors.warning, Extra.colors.onWarning)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onTake) { Text(t("trip.take"), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

/** «✓ Endorsed by SBB staff». */
@Composable
fun EndorsedChip(text: String) {
    Row(
        Modifier.background(Extra.colors.chip, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ic(Res.drawable.ic_check, null, tint = Extra.colors.onChip, size = 14)
        Text(text, style = MaterialTheme.typography.labelSmall, color = Extra.colors.onChip)
    }
}

/** Bold time headline, e.g. «16:40 – 17:52». */
@Composable
fun TimesHeadline(
    text: String,
    modifier: Modifier = Modifier,
) = Text(text, modifier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
