package com.dhama.homenotestracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable
    data object Dashboard : Route
    
    @Serializable
    data object Checklist : Route
    
    @Serializable
    data object Calendar : Route
    
    @Serializable
    data object Statistics : Route
    
    @Serializable
    data object Settings : Route

    @Serializable
    data object CategoryManagement : Route

    @Serializable
    data object Grocery : Route

    @Serializable
    data object Bills : Route
}

