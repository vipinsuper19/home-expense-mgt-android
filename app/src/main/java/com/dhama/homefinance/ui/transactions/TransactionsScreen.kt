package com.dhama.homefinance.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dhama.homefinance.R
import com.dhama.homefinance.ui.common.AddTransactionFab
import com.dhama.homefinance.ui.common.PlaceholderScreen

@Composable
fun TransactionsScreen(onAddTransaction: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_transactions),
        floatingActionButton = { AddTransactionFab(onClick = onAddTransaction) }
    )
}
