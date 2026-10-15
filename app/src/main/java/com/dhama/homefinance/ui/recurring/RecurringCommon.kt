package com.dhama.homefinance.ui.recurring

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dhama.homefinance.R
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.ui.transaction.TransactionFormLogic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

internal fun LocalDate.medium(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
internal fun weekdayName(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())
internal fun monthName(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

/** "Every month on day 31", "Every 2 weeks on Monday", … */
@Composable
internal fun frequencyText(s: RecurringSchedule): String {
    val every = when (s.frequency) {
        Frequency.DAILY -> pluralStringResource(R.plurals.every_n_days, s.interval, s.interval)
        Frequency.WEEKLY -> pluralStringResource(R.plurals.every_n_weeks, s.interval, s.interval)
        Frequency.MONTHLY -> pluralStringResource(R.plurals.every_n_months, s.interval, s.interval)
        Frequency.YEARLY -> pluralStringResource(R.plurals.every_n_years, s.interval, s.interval)
    }
    val on = when (s.frequency) {
        Frequency.DAILY -> null
        Frequency.WEEKLY -> weekdayName(s.dayOfWeek!!)
        Frequency.MONTHLY -> stringResource(R.string.on_day_n, s.dayOfMonth!!)
        Frequency.YEARLY -> "${s.dayOfMonth} ${monthName(s.monthOfYear!!)}"
    }
    return if (on == null) every else stringResource(R.string.frequency_on, every, on)
}

@Composable
internal fun statusText(s: ScheduleStatus): String = stringResource(
    when (s) {
        ScheduleStatus.ACTIVE -> R.string.schedule_active
        ScheduleStatus.PAUSED -> R.string.schedule_paused
        ScheduleStatus.STOPPED -> R.string.schedule_stopped
    }
)

@Composable
internal fun occurrenceStatusText(s: OccurrenceStatus, overdue: Boolean): String = stringResource(
    when {
        overdue -> R.string.occurrence_overdue
        s == OccurrenceStatus.PENDING -> R.string.occurrence_upcoming
        s == OccurrenceStatus.PARTIAL -> R.string.occurrence_partial
        s == OccurrenceStatus.COMPLETED -> R.string.occurrence_completed
        else -> R.string.occurrence_skipped
    }
)

/** Read-only date field with a picker; the picker commits in UTC (TransactionFormLogic). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearable: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.medium().orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        isError = isError,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            if (clearable && value != null && enabled) {
                IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) }
            } else {
                IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Default.CalendarMonth, label) }
            }
        },
        modifier = modifier
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: LocalDate.now()).let(TransactionFormLogic::dateToPickerMillis))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(TransactionFormLogic.pickerMillisToDate(it)) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}
