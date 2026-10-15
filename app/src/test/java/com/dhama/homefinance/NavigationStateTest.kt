package com.dhama.homefinance

import com.dhama.homefinance.ui.navigation.NavigationState
import com.dhama.homefinance.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateTest {
    private fun state() = NavigationState(mutableListOf(Route.Home))

    @Test fun bottomBarHasFourTabsInPrdOrder() {
        assertEquals(listOf(Route.Home, Route.Transactions, Route.Recurring, Route.Reports), Route.topLevel)
    }

    @Test fun selectTabResetsStack() {
        val s = state()
        s.push(Route.Settings)
        s.selectTab(Route.Reports)
        assertEquals(listOf<Route>(Route.Reports), s.backStack)
    }

    @Test fun settingsPushesOnHomeAndBackReturns() {
        val s = state()
        s.push(Route.Settings)
        assertEquals(Route.Settings, s.current)
        assertEquals(Route.Home, s.selectedTab)
        s.push(Route.Settings)
        assertEquals(2, s.backStack.size)
        assertTrue(s.onBack())
        assertEquals(Route.Home, s.current)
        assertFalse(s.onBack())
    }
}
