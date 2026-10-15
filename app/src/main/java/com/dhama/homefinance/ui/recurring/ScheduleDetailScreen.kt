package com.dhama.homefinance.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.util.Money
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    viewModel: ScheduleDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val s = state.schedule
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var payFor by rememberSaveable { mutableStateOf<String?>(null) }
    var payAmount by rememberSaveable { mutableStateOf("") }
    var payDate by rememberSaveable { mutableStateOf<String?>(null) }
    var payError by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (s != null && s.status != ScheduleStatus.STOPPED) {
                        IconButton(onClick = { onEdit(s.id) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_schedule)) }
                    }
                }
            )
        }
    ) { padding ->
        if (s == null) return@Scaffold
        val income = s.type == TransactionType.INCOME
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Money.formatPaise(state.currentAmountPaise), style = MaterialTheme.typography.headlineSmall)
                    Text("${stringResource(if (income) R.string.type_income else R.string.type_expense)} · ${frequencyText(s)}")
                    Text(statusText(s.status), style = MaterialTheme.typography.labelLarge)
                    state.nextDueDate?.let { Text(stringResource(R.string.next_due, it.medium())) }
                    if (state.materializeFailed) {
                        Text(stringResource(R.string.materialize_failed), color = MaterialTheme.colorScheme.error)
                    }
                    s.endDate?.let { Text(stringResource(R.string.ends_on, it.medium())) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (s.status) {
                            ScheduleStatus.ACTIVE -> OutlinedButton(enabled = !state.isBusy, onClick = viewModel::pause) { Text(stringResource(R.string.pause)) }
                            ScheduleStatus.PAUSED -> OutlinedButton(enabled = !state.isBusy, onClick = viewModel::resume) { Text(stringResource(R.string.resume)) }
                            ScheduleStatus.STOPPED -> Unit
                        }
                        if (s.status != ScheduleStatus.STOPPED) {
                            OutlinedButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.stop)) }
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                if (state.history.isEmpty()) {
                    Text(stringResource(R.string.history_empty), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(state.history, key = { it.item.occurrence.id }) { row ->
                val o = row.item.occurrence
                ListItem(
                    headlineContent = { Text(o.dueDate.medium()) },
                    supportingContent = {
                        Column {
                            Text(occurrenceStatusText(o.status, row.isOverdue),
                                color = if (row.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.item.paidPaise > 0) {
                                Text(stringResource(if (income) R.string.received_amount else R.string.paid_amount, Money.formatPaise(row.item.paidPaise)))
                            }
                            if (o.status.isOpen) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        payFor = o.id
                                        payAmount = viewModel.paymentPrefill(o.id)
                                        payDate = state.today.toString()
                                        payError = false
                                    }) { Text(stringResource(if (income) R.string.mark_received else R.string.mark_paid)) }
                                    TextButton(enabled = !state.isBusy, onClick = { viewModel.skip(o.dueDate) }) { Text(stringResource(R.string.skip)) }
                                }
                            }
                        }
                    },
                    trailingContent = { Text(Money.formatPaise(o.expectedAmountPaise)) }
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_schedule_title)) },
            text = { Text(stringResource(R.string.stop_schedule_message)) },
            confirmButton = { TextButton(enabled = !state.isBusy, onClick = { viewModel.stop(); confirmStop = false }) { Text(stringResource(R.string.stop)) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    payFor?.let { occurrenceId ->
        val income = s?.type == TransactionType.INCOME
        AlertDialog(
            onDismissRequest = { payFor = null },
            title = { Text(stringResource(if (income) R.string.mark_received else R.string.mark_paid)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = payAmount,
                        onValueChange = { payAmount = it; payError = false },
                        label = { Text(stringResource(R.string.amount_label)) },
                        prefix = { Text("₹") },
                        isError = payError,
                        supportingText = if (payError) {{ Text(stringResource(R.string.amount_error)) }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DateField(
                        label = stringResource(R.string.date_label),
                        value = payDate?.let(LocalDate::parse),
                        onChange = { d -> d?.let { payDate = it.toString() } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !state.isBusy, onClick = {
                    val date = payDate?.let(LocalDate::parse) ?: state.today
                    if (viewModel.recordPayment(occurrenceId, payAmount, date)) payFor = null else payError = true
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { payFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (state.actionFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(stringResource(R.string.action_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) } }
        )
    }
}
