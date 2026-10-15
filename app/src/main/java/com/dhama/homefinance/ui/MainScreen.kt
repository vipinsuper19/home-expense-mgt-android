package com.dhama.homefinance.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import com.dhama.homefinance.R
import com.dhama.homefinance.ui.navigation.NavGraph
import com.dhama.homefinance.ui.navigation.Route
import com.dhama.homefinance.ui.navigation.rememberNavigationState

private data class TabItem(val route: Route, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Route.Home, R.string.nav_home, Icons.Default.Home),
    TabItem(Route.Transactions, R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
    TabItem(Route.Recurring, R.string.nav_recurring, Icons.Default.Repeat),
    TabItem(Route.Reports, R.string.nav_reports, Icons.Default.BarChart),
)

@Composable
fun MainScreen() {
    val navigationState = rememberNavigationState()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { }
        )
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Scaffold(
        bottomBar = {
            // Settings is a pushed screen, so the bar stays visible only on the four tabs.
            if (navigationState.current in Route.topLevel) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val label = stringResource(tab.labelRes)
                        NavigationBarItem(
                            selected = navigationState.selectedTab == tab.route,
                            onClick = { navigationState.selectTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavGraph(
            navigationState = navigationState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding)
        )
    }
}
