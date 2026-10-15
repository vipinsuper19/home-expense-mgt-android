package com.dhama.homefinance.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.ui.common.AddTransactionFab
import com.dhama.homefinance.util.Money
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val incomeColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_transactions)) }) },
        floatingActionButton = { AddTransactionFab(onClick = onAddTransaction) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { MonthSelector(state, viewModel) }
            item {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            item { Filters(state, viewModel) }
            item { MonthTotals(state) }
            if (state.isEmpty) {
                item { EmptyState(state.isFiltered, viewModel::clearFilters) }
            }
            state.days.forEach { day ->
                item(key = "h-${day.date}") { DayHeader(day) }
                items(day.items, key = { it.id }) { tx ->
                    TransactionRow(tx, onClick = { onOpenTransaction(tx.id) })
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: TransactionsUiState, vm: TransactionsViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = vm::previousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        TextButton(onClick = vm::goToCurrentMonth) {
            Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = vm::nextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun Filters(state: TransactionsUiState, vm: TransactionsViewModel) {
    var categoryMenu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = state.type == null, onClick = { vm.setType(null) }, label = { Text(stringResource(R.string.filter_all)) })
        FilterChip(selected = state.type == TransactionType.INCOME, onClick = { vm.setType(TransactionType.INCOME) }, label = { Text(stringResource(R.string.type_income)) })
        FilterChip(selected = state.type == TransactionType.EXPENSE, onClick = { vm.setType(TransactionType.EXPENSE) }, label = { Text(stringResource(R.string.type_expense)) })
        Box {
            val selected = state.categories.find { it.id == state.categoryId }
            FilterChip(
                selected = selected != null,
                onClick = { categoryMenu = true },
                label = { Text(selected?.name ?: stringResource(R.string.filter_all_categories)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_all_categories)) }, onClick = { vm.setCategory(null); categoryMenu = false })
                state.categories.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name) }, onClick = { vm.setCategory(c.id); categoryMenu = false })
                }
            }
        }
    }
}

@Composable
private fun MonthTotals(state: TransactionsUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TotalCell(stringResource(R.string.type_income), Money.formatPaise(state.totals.incomePaise), incomeColor)
            TotalCell(stringResource(R.string.type_expense), Money.formatPaise(state.totals.expensePaise), MaterialTheme.colorScheme.error)
            TotalCell(stringResource(R.string.net), Money.formatPaise(state.netPaise), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DayHeader(day: DaySection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (day.total.incomePaise > 0) Text("+" + Money.formatPaise(day.total.incomePaise), color = incomeColor, style = MaterialTheme.typography.labelLarge)
            if (day.total.expensePaise > 0) Text("−" + Money.formatPaise(day.total.expensePaise), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionListItem, onClick: () -> Unit) {
    val income = tx.type == TransactionType.INCOME
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(tx.description.ifBlank { tx.categoryName }) },
        supportingContent = {
            val extra = listOfNotNull(tx.categoryName.takeIf { tx.description.isNotBlank() }, tx.notes.takeIf { it.isNotBlank() })
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), maxLines = 1)
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
private fun EmptyState(filtered: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(if (filtered) R.string.empty_filtered else R.string.empty_month),
            style = MaterialTheme.typography.bodyLarge
        )
        if (filtered) TextButton(onClick = onClear) { Text(stringResource(R.string.clear_filters)) }
    }
}
