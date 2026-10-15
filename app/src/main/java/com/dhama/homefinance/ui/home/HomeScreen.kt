package com.dhama.homefinance.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dhama.homefinance.R
import com.dhama.homefinance.ui.common.AddTransactionFab
import com.dhama.homefinance.ui.common.PlaceholderScreen

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onAddTransaction: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_home),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        },
        floatingActionButton = { AddTransactionFab(onClick = onAddTransaction) }
    )
}
