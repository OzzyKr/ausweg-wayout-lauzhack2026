package ch.ausweg.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import ch.ausweg.app.i18n.LocalStrings
import ch.ausweg.app.i18n.loadLanguages
import ch.ausweg.app.i18n.loadStrings
import ch.ausweg.app.navigation.AppScaffold
import ch.ausweg.app.theme.AuswegTheme
import ch.ausweg.shared.i18n.FALLBACK_LANGUAGE
import ch.ausweg.shared.i18n.Strings
import ch.ausweg.shared.state.AuswegApp

/**
 * The app. Platforms pass the [AuswegApp] (from [AppHolder]) and the device languages (`fr-CH`, …); everything else
 * is shared.
 */
@Composable
fun App(
    app: AuswegApp,
    deviceLanguages: List<String>,
) {
    // Null until languages.json is read, so a saved language isn't first replaced by the English-only placeholder.
    val languages by produceState<List<String>?>(null) { value = loadLanguages() }
    LaunchedEffect(languages) { languages?.let { app.initLanguage(it, deviceLanguages) } }
    val language by app.language.collectAsState()
    val strings by produceState<Strings?>(null, language) {
        language?.let { value = loadStrings(it) }
    }
    val themeMode by app.theme.mode.collectAsState()
    AuswegTheme(dark = themeMode.isDark(isSystemInDarkTheme())) {
        // Nothing but the theme's background until the first strings are in: no raw keys on the first frame.
        strings?.let {
            CompositionLocalProvider(LocalStrings provides it) {
                AppScaffold(app, languages ?: listOf(FALLBACK_LANGUAGE))
            }
        }
    }
}
