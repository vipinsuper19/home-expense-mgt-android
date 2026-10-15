package com.dhama.homenotestracker.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dhama.homenotestracker.ui.dashboard.DashboardScreen
import com.dhama.homenotestracker.ui.dashboard.DashboardViewModel
import com.dhama.homenotestracker.ui.category.CategoryManagementScreen
import com.dhama.homenotestracker.ui.category.CategoryViewModel
import com.dhama.homenotestracker.ui.checklist.ChecklistScreen
import com.dhama.homenotestracker.ui.checklist.ChecklistViewModel
import com.dhama.homenotestracker.ui.grocery.GroceryScreen
import com.dhama.homenotestracker.ui.grocery.GroceryViewModel
import com.dhama.homenotestracker.ui.bill.BillScreen
import com.dhama.homenotestracker.ui.bill.BillViewModel
import com.dhama.homenotestracker.ui.calendar.CalendarScreen
import com.dhama.homenotestracker.ui.calendar.CalendarViewModel
import com.dhama.homenotestracker.ui.statistics.StatisticsScreen
import com.dhama.homenotestracker.ui.statistics.StatisticsViewModel
import com.dhama.homenotestracker.ui.settings.SettingsScreen
import com.dhama.homenotestracker.ui.settings.SettingsViewModel

@Composable
fun NavGraph(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = navigationState.backStack,
        modifier = modifier,
        onBack = { navigationState.onBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = { key: Route ->
            when (key) {
                Route.Dashboard -> NavEntry(key) {
                    val viewModel: DashboardViewModel = viewModel()
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToCategories = { navigationState.goTo(Route.CategoryManagement) },
                        onNavigateToGrocery = { navigationState.goTo(Route.Grocery) },
                        onNavigateToBills = { navigationState.goTo(Route.Bills) }
                    )
                }
                Route.Checklist -> NavEntry(key) {
                    val viewModel: ChecklistViewModel = viewModel()
                    ChecklistScreen(viewModel = viewModel)
                }
                Route.Calendar -> NavEntry(key) {
                    val viewModel: CalendarViewModel = viewModel()
                    CalendarScreen(viewModel = viewModel)
                }
                Route.Statistics -> NavEntry(key) {
                    val viewModel: StatisticsViewModel = viewModel()
                    StatisticsScreen(viewModel = viewModel)
                }
                Route.Settings -> NavEntry(key) {
                    val viewModel: SettingsViewModel = viewModel()
                    SettingsScreen(viewModel = viewModel)
                }
                Route.CategoryManagement -> NavEntry(key) {
                    val viewModel: CategoryViewModel = viewModel()
                    CategoryManagementScreen(
                        viewModel = viewModel,
                        onBack = { navigationState.onBack() }
                    )
                }
                Route.Grocery -> NavEntry(key) {
                    val viewModel: GroceryViewModel = viewModel()
                    GroceryScreen(
                        viewModel = viewModel,
                        onBack = { navigationState.onBack() }
                    )
                }
                Route.Bills -> NavEntry(key) {
                    val viewModel: BillViewModel = viewModel()
                    BillScreen(
                        viewModel = viewModel,
                        onBack = { navigationState.onBack() }
                    )
                }
            }
        }
    )
}




@Composable
fun PlaceholderScreen(name: String) {
    Text(text = "$name Screen Placeholder")
}



