package com.dhama.homenotestracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dhama.homenotestracker.ui.navigation.NavGraph
import com.dhama.homenotestracker.ui.navigation.Route
import com.dhama.homenotestracker.ui.navigation.rememberNavigationState
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navigationState = rememberNavigationState()
    val currentRoute = navigationState.backStack.lastOrNull() ?: Route.Dashboard

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted -> /* Handle permission result */ }
        )
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentRoute == Route.Dashboard,
                onClick = { navigationState.goTo(Route.Dashboard) },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                label = { Text("Dashboard") }
            )
            item(
                selected = currentRoute == Route.Checklist,
                onClick = { navigationState.goTo(Route.Checklist) },
                icon = { Icon(Icons.Default.Checklist, contentDescription = "Checklist") },
                label = { Text("Checklist") }
            )
            item(
                selected = currentRoute == Route.Calendar,
                onClick = { navigationState.goTo(Route.Calendar) },
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                label = { Text("Calendar") }
            )
            item(
                selected = currentRoute == Route.Statistics,
                onClick = { navigationState.goTo(Route.Statistics) },
                icon = { Icon(Icons.Default.StackedBarChart, contentDescription = "Statistics") },
                label = { Text("Statistics") }
            )
            item(
                selected = currentRoute == Route.Settings,
                onClick = { navigationState.goTo(Route.Settings) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
    ) {
        NavGraph(
            navigationState = navigationState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
    }
}


