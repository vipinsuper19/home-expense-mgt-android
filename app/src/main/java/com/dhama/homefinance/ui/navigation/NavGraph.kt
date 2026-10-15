package com.dhama.homefinance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dhama.homefinance.ui.home.HomeScreen
import com.dhama.homefinance.ui.recurring.RecurringScreen
import com.dhama.homefinance.ui.reports.ReportsScreen
import com.dhama.homefinance.ui.settings.SettingsScreen
import com.dhama.homefinance.ui.transaction.AddEditTransactionScreen
import com.dhama.homefinance.ui.transaction.AddEditTransactionViewModel
import com.dhama.homefinance.ui.transactions.TransactionsScreen

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
                Route.Home -> NavEntry(key) {
                    HomeScreen(
                        viewModel = viewModel(),
                        onOpenSettings = { navigationState.push(Route.Settings) },
                        onAddTransaction = { type ->
                            navigationState.push(Route.AddEditTransaction(presetType = type?.name))
                        },
                        onOpenTransaction = { id -> navigationState.push(Route.AddEditTransaction(id)) }
                    )
                }
                Route.Transactions -> NavEntry(key) {
                    TransactionsScreen(
                        viewModel = viewModel(),
                        onAddTransaction = { navigationState.push(Route.AddEditTransaction()) },
                        onOpenTransaction = { id -> navigationState.push(Route.AddEditTransaction(id)) }
                    )
                }
                Route.Recurring -> NavEntry(key) { RecurringScreen() }
                Route.Reports -> NavEntry(key) { ReportsScreen() }
                is Route.AddEditTransaction -> NavEntry(key) {
                    AddEditTransactionScreen(
                        viewModel = viewModel(
                            extras = navArgsExtras(
                                AddEditTransactionViewModel.ARG_TRANSACTION_ID to key.transactionId,
                                AddEditTransactionViewModel.ARG_PRESET_TYPE to key.presetType
                            )
                        ),
                        onDone = { navigationState.onBack() }
                    )
                }
                Route.Settings -> NavEntry(key) {
                    SettingsScreen(
                        viewModel = viewModel(),
                        onBack = { navigationState.onBack() }
                    )
                }
            }
        }
    )
}
