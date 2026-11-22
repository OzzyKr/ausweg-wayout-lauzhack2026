package ch.ausweg.app.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_check
import ch.ausweg.app.resources.ic_down
import ch.ausweg.app.resources.ic_history
import ch.ausweg.app.resources.ic_live
import ch.ausweg.app.resources.ic_logo
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.state.AuswegApp

/** True on wide windows (desktop browser): website layout with top navigation instead of the phone layout. */
val LocalWide = compositionLocalOf { false }

/** «» Ausweg» on red. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Ic(Res.drawable.ic_logo, null, tint = Color.White, size = 30)
        Text(
            t("app.name"),
            Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
    }
}

/** Live / Replay chip (opens the mode sheet) and the language menu, for a red header. */
@Composable
fun HeaderControls(
    app: AuswegApp,
    languages: List<String>,
) {
    val clock by app.session.clock.collectAsState()
    val scenarios by app.session.scenarios.collectAsState()
    val language by app.language.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val replay = clock?.mode == Mode.HISTORICAL
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { sheet = true }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Ic(if (replay) Res.drawable.ic_history else Res.drawable.ic_live, null, tint = Color.White, size = 16)
                Text(
                    if (replay) t("mode.replay_chip") else t("mode.live_chip"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
        ThemeToggle(app.theme)
        LanguageMenu(language ?: "en", languages) { app.setLanguage(it, languages) }
    }
    if (sheet) {
        ModeSheet(
            clock,
            scenarios,
            onLive = {
                app.session.goLive()
                sheet = false
            },
            onScenario = {
                app.session.startScenario(it.id)
                sheet = false
            },
            onDismiss = { sheet = false },
        )
    }
}

/** «EN ▾» on the red header; opens a menu with every language under its own name. */
@Composable
private fun LanguageMenu(
    current: String,
    languages: List<String>,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            modifier = Modifier.height(44.dp).border(1.5.dp, Color.White, RoundedCornerShape(50)),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(current.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.White)
                Ic(Res.drawable.ic_down, t("language.choose"), tint = Color.White, size = 16)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            languages.forEach { code ->
                DropdownMenuItem(
                    text = { Text(t(I18nKeys.languageName(code))) },
                    onClick = {
                        open = false
                        onChoose(code)
                    },
                    trailingIcon = { if (code == current) Ic(Res.drawable.ic_check, null, size = 18) },
                )
            }
        }
    }
}
