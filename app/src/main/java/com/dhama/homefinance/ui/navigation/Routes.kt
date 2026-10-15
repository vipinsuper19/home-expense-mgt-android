package com.dhama.homefinance.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data object Transactions : Route
    @Serializable data object Recurring : Route
    @Serializable data object Reports : Route
    @Serializable data object Settings : Route
    /**
     * [transactionId] null = add, otherwise edit. [presetType] ("INCOME"/"EXPENSE")
     * pre-selects the type when adding (Home quick actions); ignored when editing.
     */
    @Serializable data class AddEditTransaction(
        val transactionId: String? = null,
        val presetType: String? = null
    ) : Route

    @Serializable data class ScheduleDetail(val scheduleId: String) : Route
    /** [scheduleId] null = create. */
    @Serializable data class AddEditSchedule(val scheduleId: String? = null) : Route

    companion object {
        /** Destinations shown in the bottom bar, in order. */
        val topLevel: List<Route> = listOf(Home, Transactions, Recurring, Reports)
    }
}
