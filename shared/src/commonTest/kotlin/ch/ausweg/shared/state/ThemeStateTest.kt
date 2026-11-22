package ch.ausweg.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeStateTest {
    @Test
    fun followsTheDeviceUntilToggled() {
        val theme = ThemeState(MemorySettings())
        assertEquals(ThemeMode.SYSTEM, theme.mode.value)
        assertTrue(theme.mode.value.isDark(systemDark = true))
        assertFalse(theme.mode.value.isDark(systemDark = false))
    }

    @Test
    fun toggleFlipsWhatIsShownAndSavesIt() {
        val settings = MemorySettings()
        val theme = ThemeState(settings)
        theme.toggle(systemDark = true)
        assertEquals(ThemeMode.LIGHT, theme.mode.value)
        assertFalse(theme.mode.value.isDark(systemDark = true))
        assertEquals("LIGHT", settings[SettingKeys.THEME])
        theme.toggle(systemDark = true)
        assertEquals(ThemeMode.DARK, theme.mode.value)
        assertTrue(theme.mode.value.isDark(systemDark = false))
    }

    @Test
    fun readsTheSavedChoiceAndIgnoresJunk() {
        assertEquals(ThemeMode.DARK, ThemeState(MemorySettings(mapOf(SettingKeys.THEME to "DARK"))).mode.value)
        assertEquals(ThemeMode.SYSTEM, ThemeState(MemorySettings(mapOf(SettingKeys.THEME to "purple"))).mode.value)
    }
}
