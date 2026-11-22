package ch.ausweg.shared.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark look. [SYSTEM] follows the device until the user picks one. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Whether to draw dark, given whether the device is dark right now. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }
}

/** The header's light/dark switch, saved in [Settings] so a reload keeps it. */
class ThemeState(
    private val settings: Settings,
) {
    private val _mode =
        MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == settings[SettingKeys.THEME] } ?: ThemeMode.SYSTEM)

    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Switches to the opposite of what is shown now and keeps it. */
    fun toggle(systemDark: Boolean) {
        val next = if (_mode.value.isDark(systemDark)) ThemeMode.LIGHT else ThemeMode.DARK
        _mode.value = next
        settings[SettingKeys.THEME] = next.name
    }
}
