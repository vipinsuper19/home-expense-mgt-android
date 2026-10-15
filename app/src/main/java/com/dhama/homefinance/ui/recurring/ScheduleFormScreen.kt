package com.dhama.homefinance.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormScreen(viewModel: ScheduleFormViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val f = state.form
    val locked = state.isEdit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (locked) R.string.edit_schedule else R.string.add_schedule)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = f.name, onValueChange = viewModel::setName, singleLine = true,
                label = { Text(stringResource(R.string.schedule_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            val types = listOf(TransactionType.EXPENSE to R.string.type_expense, TransactionType.INCOME to R.string.type_income)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = f.type == t, enabled = !locked, onClick = { viewModel.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size)
                    ) { Text(stringResource(label)) }
                }
            }
            Picker(
                label = stringResource(R.string.category_label),
                selected = state.categories.find { it.id == f.categoryId }?.name.orEmpty(),
                options = state.categories.map { it.id to it.name },
                enabled = !locked,
                onSelect = viewModel::setCategory
            )
            OutlinedTextField(
                value = f.amountText, onValueChange = viewModel::setAmount, singleLine = true,
                label = { Text(stringResource(R.string.amount_label)) }, prefix = { Text("₹") },
                isError = f.amountError,
                supportingText = if (f.amountError) {{ Text(stringResource(R.string.amount_error)) }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (locked) {
                DateField(
                    label = stringResource(R.string.amount_effective_from),
                    value = f.amountEffectiveFrom,
                    onChange = { d -> d?.let(viewModel::setAmountEffectiveFrom) },
                    isError = f.amountEffectiveFrom.isBefore(f.startDate),
                    supportingText = stringResource(R.string.amount_future_only),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val freqs = listOf(
                Frequency.DAILY to R.string.freq_daily, Frequency.WEEKLY to R.string.freq_weekly,
                Frequency.MONTHLY to R.string.freq_monthly, Frequency.YEARLY to R.string.freq_yearly
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                freqs.forEachIndexed { i, (fr, label) ->
                    SegmentedButton(
                        selected = f.frequency == fr, enabled = !locked, onClick = { viewModel.setFrequency(fr) },
                        shape = SegmentedButtonDefaults.itemShape(i, freqs.size)
                    ) { Text(stringResource(label)) }
                }
            }
            OutlinedTextField(
                value = f.intervalText, onValueChange = viewModel::setInterval, singleLine = true, enabled = !locked,
                label = { Text(stringResource(R.string.repeat_every)) }, isError = f.intervalError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            when (f.frequency) {
                Frequency.WEEKLY -> Picker(
                    label = stringResource(R.string.weekday), selected = weekdayName(f.dayOfWeek),
                    options = (1..7).map { it.toString() to weekdayName(it) }, enabled = !locked,
                    onSelect = { viewModel.setDayOfWeek(it.toInt()) }
                )
                Frequency.YEARLY -> Picker(
                    label = stringResource(R.string.month), selected = monthName(f.monthOfYear),
                    options = (1..12).map { it.toString() to monthName(it) }, enabled = !locked,
                    onSelect = { viewModel.setMonthOfYear(it.toInt()) }
                )
                else -> Unit
            }
            if (f.frequency == Frequency.MONTHLY || f.frequency == Frequency.YEARLY) {
                OutlinedTextField(
                    value = f.dayOfMonthText, onValueChange = viewModel::setDayOfMonth, singleLine = true, enabled = !locked,
                    label = { Text(stringResource(R.string.due_day)) }, isError = f.dayOfMonthError,
                    supportingText = { Text(stringResource(R.string.due_day_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DateField(
                label = stringResource(R.string.start_date), value = f.startDate, enabled = !locked,
                onChange = { d -> d?.let(viewModel::setStartDate) }, modifier = Modifier.fillMaxWidth()
            )
            DateField(
                label = stringResource(R.string.end_date_optional), value = f.endDate, enabled = !locked, clearable = true,
                isError = f.endDateError, onChange = viewModel::setEndDate, modifier = Modifier.fillMaxWidth()
            )
            if (locked) {
                Text(stringResource(R.string.rule_locked_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
