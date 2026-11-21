package ch.ausweg.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ch.ausweg.app.components.HeaderControls
import ch.ausweg.app.components.Ic
import ch.ausweg.app.components.LocalWide
import ch.ausweg.app.components.Logo
import ch.ausweg.app.components.ReplayBar
import ch.ausweg.app.i18n.t
import ch.ausweg.app.resources.Res
import ch.ausweg.app.resources.ic_bell
import ch.ausweg.app.resources.ic_chat
import ch.ausweg.app.resources.ic_menu
import ch.ausweg.app.resources.ic_plan
import ch.ausweg.app.screens.alerts.AlertsScreen
import ch.ausweg.app.screens.community.CommunityScreen
import ch.ausweg.app.screens.options.OptionsScreen
import ch.ausweg.app.screens.plan.PlanScreen
import ch.ausweg.app.screens.route.RouteScreen
import ch.ausweg.app.screens.share.ShareScreen
import ch.ausweg.app.screens.trip.TripScreen
import ch.ausweg.app.theme.Extra
import ch.ausweg.shared.i18n.I18nKeys
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.state.AuswegApp
import ch.ausweg.shared.state.NavState
import ch.ausweg.shared.state.Screen
import ch.ausweg.shared.state.Tab

/** Phone-width column on narrow windows. */
private const val MAX_WIDTH = 480

/** From this window width on, the app is laid out as a website (top navigation, wider pages). */
private const val WIDE_FROM = 900

/** Width of the header and replay bar contents in the wide layout (matches the plan page). */
private const val WIDE_MAX = 1120

/** Reading width of pushed pages and tabs in the wide layout (the plan page sets its own). */
private const val WIDE_PAGE = 760

/**
 * Renders the shared [ch.ausweg.shared.state.Navigator]: the top pushed screen, else the selected tab, and the replay
 * bar in historical mode. Narrow windows get the phone layout (bottom bar); wide ones a website layout (red top bar
 * with the tabs as links, wider centered pages).
 */
@Composable
fun AppScaffold(
    app: AuswegApp,
    languages: List<String>,
) {
    val nav by app.navigator.state.collectAsState()
    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.PLAN) { app.back() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= WIDE_FROM.dp
        CompositionLocalProvider(LocalWide provides wide) {
            if (wide) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    WebHeader(app, languages, nav.tab)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        val page =
                            if (nav.top == null &&
                                nav.tab == Tab.PLAN
                            ) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.widthIn(max = WIDE_PAGE.dp).fillMaxHeight()
                            }
                        Box(page) { Content(app, languages, nav) }
                    }
                    Replay(app)
                }
            } else {
                Column(
                    Modifier
                        .widthIn(max = MAX_WIDTH.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { Content(app, languages, nav) }
                    Replay(app)
                    if (nav.top == null) BottomBar(nav.tab) { app.navigator.select(it) }
                }
            }
        }
    }
}

@Composable
private fun Content(
    app: AuswegApp,
    languages: List<String>,
    nav: NavState,
) {
    when (nav.top) {
        Screen.Options -> OptionsScreen(app)
        is Screen.Route -> RouteScreen(app)
        Screen.Share -> ShareScreen(app)
        null ->
            when (nav.tab) {
                Tab.PLAN -> PlanScreen(app, languages)
                Tab.TRIPS -> TripScreen(app)
                Tab.COMMUNITY -> CommunityScreen(app)
                Tab.ALERTS -> AlertsScreen(app)
            }
    }
}

/** The replay bar, in historical mode only. */
@Composable
private fun Replay(app: AuswegApp) {
    val clock by app.session.clock.collectAsState()
    val scenario by app.session.activeScenario.collectAsState()
    val now by app.session.now.collectAsState()
    val c = clock
    if (c != null && c.mode == Mode.HISTORICAL) {
        Box(Modifier.fillMaxWidth().background(Extra.colors.banner), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = WIDE_MAX.dp)) {
                ReplayBar(
                    c,
                    scenario,
                    now,
                    onTogglePlay = { app.session.togglePlay() },
                    onSeek = { app.session.seek(it) },
                    onSpeed = { app.session.cycleSpeed() },
                )
            }
        }
    }
}

/** Website header: logo, the tabs as links, mode chip and language. */
@Composable
private fun WebHeader(
    app: AuswegApp,
    languages: List<String>,
    selected: Tab,
) {
    Surface(color = Extra.colors.brand, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .wrapContentWidth()
                .widthIn(
                    max = WIDE_MAX.dp,
                ).fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(onClick = { app.navigator.select(Tab.PLAN) }, color = Color.Transparent) { Logo() }
            Spacer(Modifier.width(24.dp))
            Tab.entries.forEach { tab ->
                val active = tab == selected
                TextButton({ app.navigator.select(tab) }) {
                    Text(
                        t(I18nKeys.tab(tab)),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (active) TextDecoration.Underline else null,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HeaderControls(app, languages)
        }
    }
}

@Composable
private fun BottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val icon =
                when (tab) {
                    Tab.PLAN -> Res.drawable.ic_plan
                    Tab.TRIPS -> Res.drawable.ic_menu
                    Tab.COMMUNITY -> Res.drawable.ic_chat
                    Tab.ALERTS -> Res.drawable.ic_bell
                }
            val active = tab == selected
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(tab) },
                icon = {
                    val colors = MaterialTheme.colorScheme
                    val tint = if (active) colors.primary else colors.onSurfaceVariant
                    Ic(icon, null, tint = tint)
                },
                label = { Text(t(I18nKeys.tab(tab))) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}

/** System back (Android). Does nothing on the web. */
@Composable
expect fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
