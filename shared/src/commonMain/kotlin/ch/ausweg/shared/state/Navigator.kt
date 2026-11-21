package ch.ausweg.shared.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bottom bar tabs. */
enum class Tab { PLAN, TRIPS, COMMUNITY, ALERTS }

/** Screens pushed on top of a tab. */
sealed interface Screen {
    /** «Your options» for the planned trip. */
    data object Options : Screen

    /** One community route. */
    data class Route(
        val routeId: String,
    ) : Screen

    /** «Share a route». */
    data object Share : Screen
}

/** The selected [tab] and the screens pushed on top of it, last on top. */
data class NavState(
    val tab: Tab = Tab.PLAN,
    val stack: List<Screen> = emptyList(),
) {
    val top: Screen? get() = stack.lastOrNull()
}

/** Navigation as plain state, so it's shared and tested; the UI renders [state] and calls [back] on back presses. */
class Navigator {
    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state.asStateFlow()

    fun push(screen: Screen) = _state.update { it.copy(stack = it.stack + screen) }

    /** Switches tab and drops pushed screens. */
    fun select(tab: Tab) = _state.update { NavState(tab) }

    /**
     * Pops the top screen, else goes back to the plan tab. False if there was nowhere to go (Android then closes
     * the app).
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.stack.isNotEmpty() -> {
                _state.value = s.copy(stack = s.stack.dropLast(1))
                true
            }
            s.tab != Tab.PLAN -> {
                _state.value = NavState(Tab.PLAN)
                true
            }
            else -> false
        }
    }
}
