package ch.ausweg.app

import ch.ausweg.shared.api.HttpAuswegApi
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The one [AuswegApp] of the process, made on first use. It lives outside the composition and the Android activity,
 * so rotating the phone, switching dark mode or the system language (which re-create the activity) keep the plan,
 * the options and the taken trip.
 */
object AppHolder {
    private var app: AuswegApp? = null

    fun get(
        apiUrl: String,
        settings: Settings,
    ): AuswegApp =
        app ?: AuswegApp(HttpAuswegApi(apiUrl), settings, CoroutineScope(SupervisorJob() + Dispatchers.Main))
            .also {
                it.start()
                app = it
            }
}
