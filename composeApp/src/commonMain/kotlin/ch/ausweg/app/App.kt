package ch.ausweg.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.ausweg.app.i18n.LocalStrings
import ch.ausweg.app.i18n.loadLanguages
import ch.ausweg.app.i18n.loadStrings
import ch.ausweg.app.navigation.AppScaffold
import ch.ausweg.app.theme.AuswegTheme
import ch.ausweg.shared.api.HttpAuswegApi
import ch.ausweg.shared.i18n.FALLBACK_LANGUAGE
import ch.ausweg.shared.i18n.Strings
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.Settings

/**
 * The app. Platforms pass where the backend is, their [Settings] storage and the device languages (`fr-CH`, …);
 * everything else is shared.
 */
@Composable
fun App(
    apiUrl: String,
    settings: Settings,
    deviceLanguages: List<String>,
) {
    val scope = rememberCoroutineScope()
    val app = remember { AuswegApp(HttpAuswegApi(apiUrl), settings, scope).also { it.start() } }
    val languages by produceState(listOf(FALLBACK_LANGUAGE)) { value = loadLanguages() }
    LaunchedEffect(languages) { app.initLanguage(languages, deviceLanguages) }
    val language by app.language.collectAsState()
    val strings by produceState(Strings.EMPTY, language) {
        language?.let { value = loadStrings(it) }
    }
    AuswegTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            AppScaffold(app, languages)
        }
    }
}
