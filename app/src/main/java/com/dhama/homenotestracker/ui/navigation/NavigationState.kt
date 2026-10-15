package com.dhama.homenotestracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

class NavigationState(
    val backStack: SnapshotStateList<Route>
) {
    fun goTo(route: Route) {
        if (backStack.lastOrNull() != route) {
            // For top-level destinations, we might want to clear the backstack
            backStack.clear()
            backStack.add(route)
        }
    }
    
    fun onBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }
}

@Composable
fun rememberNavigationState(
    backStack: SnapshotStateList<Route> = remember { mutableStateListOf(Route.Dashboard) }
): NavigationState {
    return remember(backStack) {
        NavigationState(backStack)
    }
}


