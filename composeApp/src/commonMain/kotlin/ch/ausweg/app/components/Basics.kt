package ch.ausweg.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_back
import ch.ausweg.app.resources.ic_close
import ch.ausweg.app.theme.Extra
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** A vector icon from `composeResources/drawable`. [description] null = decorative. */
@Composable
fun Ic(
    res: DrawableResource,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 24,
) = Icon(painterResource(res), description, modifier.size(size.dp), tint = tint)

/** The white rounded card used everywhere. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val stroke = border?.let { androidx.compose.foundation.BorderStroke(2.dp, it) }
    if (onClick != null) {
        Surface(
            onClick,
            modifier.fillMaxWidth(),
            shape = shape,
            border = stroke,
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    } else {
        Surface(modifier.fillMaxWidth(), shape = shape, border = stroke, color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}

/** «OUR PICK», «FROM OTHER TRAVELLERS»: bold label with an optional red action on the right. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (action != null) {
            TextButton(onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Full-width red button (Find my way, Take this route, Post route). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick,
        modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Extra.colors.brand, contentColor = Color.White),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick, modifier, shape = MaterialTheme.shapes.medium) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

/** A screen title bar with back (or close) and a subtitle. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    close: Boolean = false,
    backLabel: String = "",
    actions: @Composable () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onBack) { Ic(if (close) Res.drawable.ic_close else Res.drawable.ic_back, backLabel) }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** A small rounded tag: «Affects your trip», «On time», «Blocked». */
@Composable
fun Tag(
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.background(background, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = content,
    )
}

/** Centered message with an optional button, for empty and error states. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    loading: Boolean = false,
) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) SecondaryButton(action, onAction)
        }
    }
}

/** Thin gap between cards in a list. */
@Composable
fun Gap(height: Int = 10) = Spacer(Modifier.height(height.dp))
