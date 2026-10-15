package com.dhama.homefinance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * Back stack for the app shell. Selecting a bottom-bar tab resets the stack to
 * that tab; other screens (e.g. Settings) are pushed on top of the current one.
 */
class NavigationState(
    val backStack: MutableList<Route>
) {
    val current: Route get() = backStack.lastOrNull() ?: Route.Home

    /** The bottom-bar tab that should appear selected. */
    val selectedTab: Route get() = backStack.lastOrNull { it in Route.topLevel } ?: Route.Home

    fun selectTab(route: Route) {
        require(route in Route.topLevel) { "$route is not a bottom-bar destination" }
        if (backStack.size == 1 && backStack[0] == route) return
        backStack.clear()
        backStack.add(route)
    }

    fun push(route: Route) {
        if (current != route) backStack.add(route)
    }

    /** Returns true if something was popped. */
    fun onBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    return remember(backStack) { NavigationState(backStack) }
}
