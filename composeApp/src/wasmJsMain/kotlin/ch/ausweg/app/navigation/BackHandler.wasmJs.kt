package ch.ausweg.app.navigation

import androidx.compose.runtime.Composable

/** The browser's back button is not wired up; the app's own back arrows work. */
@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
