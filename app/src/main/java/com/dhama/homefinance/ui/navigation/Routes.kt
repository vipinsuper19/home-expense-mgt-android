package com.dhama.homefinance.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data object Transactions : Route
    @Serializable data object Recurring : Route
    @Serializable data object Reports : Route
    @Serializable data object Settings : Route

    companion object {
        /** Destinations shown in the bottom bar, in order. */
        val topLevel: List<Route> = listOf(Home, Transactions, Recurring, Reports)
    }
}
