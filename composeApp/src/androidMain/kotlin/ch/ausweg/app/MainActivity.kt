package ch.ausweg.app

import android.content.Context
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.ausweg.shared.state.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = PrefsSettings(applicationContext.getSharedPreferences("ausweg", Context.MODE_PRIVATE))
        val app = AppHolder.get(BuildConfig.API_URL, settings)
        val locales = LocaleList.getDefault()
        val languages = (0 until locales.size()).map { locales[it].toLanguageTag() }
        setContent { App(app, languages) }
    }
}

/** [Settings] in SharedPreferences. */
private class PrefsSettings(
    private val prefs: android.content.SharedPreferences,
) : Settings {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(
        key: String,
        value: String,
    ) = prefs.edit().putString(key, value).apply()
}
