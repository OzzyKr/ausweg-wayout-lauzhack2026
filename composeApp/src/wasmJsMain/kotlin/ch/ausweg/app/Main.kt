package ch.ausweg.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ch.ausweg.shared.state.MemorySettings
import ch.ausweg.shared.state.Settings
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val app = AppHolder.get(apiUrl(), BrowserSettings())
    ComposeViewport(document.body!!) { App(app, browserLanguages()) }
}

/** `?api=http://host:port` wins; else the backend on port 8080 of the host that served the page. */
private fun apiUrl(): String =
    URLSearchParams(window.location.search.toJsString()).get("api")
        ?: "${window.location.protocol}//${window.location.hostname.ifEmpty { "localhost" }}:8080"

private fun browserLanguages(): List<String> {
    val langs = window.navigator.languages
    return (0 until langs.length).mapNotNull { langs[it]?.toString() }.ifEmpty { listOf(window.navigator.language) }
}

/** [Settings] in localStorage; in memory if storage is blocked (private mode). */
private class BrowserSettings : Settings {
    private val fallback = MemorySettings()

    override fun get(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull() ?: fallback[key]

    override fun set(
        key: String,
        value: String,
    ) {
        fallback[key] = value
        runCatching { localStorage.setItem(key, value) }
    }
}
