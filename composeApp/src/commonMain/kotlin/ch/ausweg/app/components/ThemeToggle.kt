package ch.ausweg.app.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_dark_mode
import ch.ausweg.app.resources.ic_light_mode
import ch.ausweg.shared.state.ThemeState

/** Round light/dark switch for a red header; shows the look it switches to. */
@Composable
fun ThemeToggle(theme: ThemeState) {
    val systemDark = isSystemInDarkTheme()
    val mode by theme.mode.collectAsState()
    val dark = mode.isDark(systemDark)
    Surface(
        onClick = { theme.toggle(systemDark) },
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(44.dp).border(1.5.dp, Color.White, CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Ic(
                if (dark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode,
                if (dark) t("theme.to_light") else t("theme.to_dark"),
                tint = Color.White,
                size = 20,
            )
        }
    }
}
