package ch.ausweg.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import ch.ausweg.app.resources.Res
import ch.ausweg.shared.i18n.FALLBACK_LANGUAGE
import ch.ausweg.shared.i18n.Strings
import ch.ausweg.shared.i18n.parseLanguages
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** The UI strings of the current language. Read them with [t]. */
val LocalStrings = staticCompositionLocalOf { Strings.EMPTY }

/**
 * The text for [key] in the current language, English if it's missing. Keys must be literal strings: the key-check
 * test (`shared/src/jvmTest`) finds every call with a literal key and checks it exists in `en.json`.
 */
@Composable
@ReadOnlyComposable
fun t(
    key: String,
    vararg args: Pair<String, Any?>,
): String = LocalStrings.current.get(key, *args)

/** The current [Strings], for shared formatters (`formatDuration(d, strings())`). */
@Composable
@ReadOnlyComposable
fun strings(): Strings = LocalStrings.current

private const val DIR = "files/i18n"

/** `languages.json`, or just English if it can't be read. */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadLanguages(): List<String> =
    runCatching { parseLanguages(Res.readBytes("$DIR/languages.json").decodeToString()) }
        .getOrDefault(listOf(FALLBACK_LANGUAGE))

/** [language]'s strings over English. */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadStrings(language: String): Strings {
    val english = Res.readBytes("$DIR/$FALLBACK_LANGUAGE.json").decodeToString()
    val own =
        if (language ==
            FALLBACK_LANGUAGE
        ) {
            null
        } else {
            runCatching { Res.readBytes("$DIR/$language.json") }.getOrNull()
        }
    return Strings.of(language, own?.decodeToString(), english)
}
