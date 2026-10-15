package com.dhama.homefinance.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.CategoryTotal
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.domain.MonthlySummary
import com.dhama.homefinance.ui.common.AddTransactionFab
import com.dhama.homefinance.util.Money
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val incomeColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onAddTransaction: (TransactionType?) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSchedule: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = { AddTransactionFab(onClick = { onAddTransaction(null) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { MonthSelector(state, viewModel) }
            item { QuickActions(onAddTransaction) }
            item { ActualCard(state) }
            item { TodayCard(state) }
            if (state.hasExpected) item { ExpectedCard(state.summary!!) }
            if (state.toReceive.isNotEmpty()) item { ToReceiveCard(state, onOpenSchedule) }
            if (state.isEmpty) {
                item { EmptyHome(onAddTransaction) }
            } else {
                state.summary?.expenseByCategory?.take(HomeViewModel.TOP_CATEGORIES)?.takeIf { it.isNotEmpty() }?.let { cats ->
                    item { SectionHeader(stringResource(R.string.top_expenses)) }
                    items(cats, key = { "c-${it.categoryId}" }) { CategoryRow(it) }
                }
                if (state.recent.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.recent_transactions)) }
                    items(state.recent, key = { it.id }) { tx ->
                        RecentRow(tx, onClick = { onOpenTransaction(tx.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: HomeUiState, vm: HomeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = vm::previousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        TextButton(onClick = vm::goToCurrentMonth) {
            Text(
                state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconButton(onClick = vm::nextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun QuickActions(onAdd: (TransactionType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = { onAdd(TransactionType.EXPENSE) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.quick_add_expense), modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(onClick = { onAdd(TransactionType.INCOME) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.quick_add_income), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun ActualCard(state: HomeUiState) {
    val s = state.summary
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.actual_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoneyCell(stringResource(R.string.type_income), s?.actualIncomePaise ?: 0, incomeColor)
                MoneyCell(stringResource(R.string.type_expense), s?.actualExpensePaise ?: 0, MaterialTheme.colorScheme.error)
                MoneyCell(stringResource(R.string.net), s?.netPaise ?: 0, MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.today_label, state.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                style = MaterialTheme.typography.labelLarge
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoneyCell(stringResource(R.string.type_income), state.todayTotals.incomePaise, incomeColor)
                MoneyCell(stringResource(R.string.type_expense), state.todayTotals.expensePaise, MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Rent and other income still owed: expected − received, per occurrence (PRD 8). */
@Composable
private fun ToReceiveCard(state: HomeUiState, onOpenSchedule: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.to_receive_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Text(Money.formatPaise(state.toReceivePaise), style = MaterialTheme.typography.titleMedium)
            }
            state.toReceive.forEach { item ->
                val overdue = item.dueDate.isBefore(state.today)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSchedule(item.scheduleId) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.scheduleName)
                        Text(
                            stringResource(if (overdue) R.string.overdue_since else R.string.due_on, item.dueDate.format(dueFormatter)) +
                                if (item.paidPaise > 0) " · " + stringResource(R.string.received_amount, Money.formatPaise(item.paidPaise)) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(Money.formatPaise(item.outstandingPaise))
                }
            }
            Text(stringResource(R.string.to_receive_footnote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val dueFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun ExpectedCard(summary: MonthlySummary) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.expected_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoneyCell(stringResource(R.string.type_income), summary.expectedIncomePaise, incomeColor)
                MoneyCell(stringResource(R.string.type_expense), summary.expectedExpensePaise, MaterialTheme.colorScheme.error)
                MoneyCell(stringResource(R.string.projected_label), summary.projectedNetPaise, MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.expected_footnote, summary.openExpectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MoneyCell(label: String, paise: Long, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(Money.formatPaise(paise), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun CategoryRow(c: CategoryTotal) {
    ListItem(
        headlineContent = { Text(c.categoryName) },
        supportingContent = { Text(stringResource(R.string.transaction_count, c.transactionCount)) },
        trailingContent = {
            Text(Money.formatPaise(c.totalPaise), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        }
    )
    HorizontalDivider()
}

@Composable
private fun RecentRow(tx: TransactionListItem, onClick: () -> Unit) {
    val income = tx.type == TransactionType.INCOME
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(tx.description.ifBlank { tx.categoryName }) },
        supportingContent = {
            Text(
                listOfNotNull(
                    tx.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    tx.categoryName.takeIf { tx.description.isNotBlank() }
                ).joinToString(" · ")
            )
        },
        trailingContent = {
            Text(
                (if (income) "+" else "−") + Money.formatPaise(tx.amountPaise),
                color = if (income) incomeColor else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall
            )
        }
    )
    HorizontalDivider()
}

@Composable
private fun EmptyHome(onAdd: (TransactionType?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.empty_home), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = { onAdd(TransactionType.EXPENSE) }) {
            Text(stringResource(R.string.quick_add_expense))
        }
    }
}
