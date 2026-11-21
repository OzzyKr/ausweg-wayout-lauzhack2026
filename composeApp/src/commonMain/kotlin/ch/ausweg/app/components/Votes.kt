package ch.ausweg.app.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_down
import ch.ausweg.app.resources.ic_up
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.model.Votes

/** Net score shown next to the up arrow. */
private val Votes.net: Int get() = up - down

/** Up arrow over the net score: the compact form in lists. */
@Composable
fun VoteColumn(
    votes: Votes,
    mine: VoteValue?,
    onUp: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onUp, Modifier.size(32.dp)) {
            Ic(
                Res.drawable.ic_up,
                t("vote.up"),
                tint = if (mine == VoteValue.UP) Extra.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${votes.net}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** «^ 41» pill and a down button: the full form on the route screen. */
@Composable
fun VoteButtons(
    votes: Votes,
    mine: VoteValue?,
    onVote: (VoteValue) -> Unit,
) {
    val red = Extra.colors.brand
    val muted = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = { onVote(VoteValue.UP) },
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            modifier =
                Modifier.height(44.dp).border(
                    1.5.dp,
                    if (mine ==
                        VoteValue.UP
                    ) {
                        red
                    } else {
                        muted
                    },
                    RoundedCornerShape(50),
                ),
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(Res.drawable.ic_up, t("vote.up"), tint = red, size = 20)
                Text("${votes.net}", color = red, style = MaterialTheme.typography.titleSmall)
            }
        }
        Surface(
            onClick = { onVote(VoteValue.DOWN) },
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(44.dp).border(1.5.dp, if (mine == VoteValue.DOWN) red else muted, CircleShape),
        ) {
            Ic(
                Res.drawable.ic_down,
                t("vote.down"),
                Modifier.padding(10.dp),
                tint = if (mine == VoteValue.DOWN) red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Still valid? Yes (3) · Outdated (0)» for content from an earlier, similar disruption. */
@Composable
fun ValidityVote(
    votes: Votes,
    mine: VoteValue?,
    onVote: (VoteValue) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            t("vote.still_valid_question"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton({ onVote(VoteValue.STILL_VALID) }) {
            Text(
                t("vote.still_valid", "n" to votes.stillValid),
                fontWeight = if (mine == VoteValue.STILL_VALID) FontWeight.Bold else FontWeight.Normal,
            )
        }
        TextButton({ onVote(VoteValue.OUTDATED) }) {
            Text(
                t("vote.outdated", "n" to votes.outdated),
                fontWeight = if (mine == VoteValue.OUTDATED) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
