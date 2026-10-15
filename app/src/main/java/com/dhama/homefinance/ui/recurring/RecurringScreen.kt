package com.dhama.homefinance.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    viewModel: RecurringListViewModel,
    onAddSchedule: () -> Unit,
    onOpenSchedule: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_recurring)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSchedule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule))
            }
        }
    ) { padding ->
        if (!state.loading && state.rows.isEmpty() && !state.materializeFailed) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recurring_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.materializeFailed) {
                item {
                    Text(
                        stringResource(R.string.materialize_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(state.rows, key = { it.schedule.id }) { row ->
                val s = row.schedule
                val sign = if (s.type == TransactionType.INCOME) "+" else "−"
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = {
                        val next = row.nextDueDate?.let { stringResource(R.string.next_due, it.medium()) } ?: statusText(s.status)
                        Text("${frequencyText(s)} · $next")
                    },
                    trailingContent = {
                        Text(
                            sign + Money.formatPaise(row.currentAmountPaise),
                            color = if (s.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    overlineContent = { Text(statusText(s.status)) },
                    modifier = Modifier.clickable { onOpenSchedule(s.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
