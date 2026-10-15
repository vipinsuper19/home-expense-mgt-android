package com.dhama.homefinance.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.domain.recurrence.OccurrenceMaterializer
import com.dhama.homefinance.domain.recurrence.RecurrenceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import com.dhama.homefinance.util.DateTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class ScheduleRow(
    val schedule: RecurringSchedule,
    /** Amount in force today (base amount or the latest effective change). */
    val currentAmountPaise: Long,
    /** Null when paused, stopped or past its end date. */
    val nextDueDate: LocalDate?
)

data class RecurringListUiState(
    val loading: Boolean = true,
    val rows: List<ScheduleRow> = emptyList(),
    /** Generating occurrences failed; the list still works, the screen shows a message. */
    val materializeFailed: Boolean = false
)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    private val repository: RecurringRepository,
    private val materializer: OccurrenceMaterializer,
    ticker: DateTicker
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    private val today = ticker.today.onEach { day -> failed.value = !materializer.materializeAround(day) }

    val uiState: StateFlow<RecurringListUiState> = combine(
        repository.observeSchedules(),
        repository.observeAllPauses(),
        repository.observeAllAmountChanges(),
        today,
        failed
    ) { schedules, pauses, changes, day, fail ->
        val rows = schedules.map { s ->
            val mine = changes.filter { it.scheduleId == s.id }
            ScheduleRow(
                schedule = s,
                currentAmountPaise = RecurrenceEngine.amountOn(s, mine, maxOf(day, s.startDate)),
                nextDueDate = if (s.status == ScheduleStatus.ACTIVE) RecurrenceEngine.nextDueDate(s, pauses, day) else null
            )
        }.sortedWith(compareBy<ScheduleRow> { it.schedule.status }.thenBy { it.nextDueDate ?: LocalDate.MAX }.thenBy { it.schedule.name })
        RecurringListUiState(loading = false, rows = rows, materializeFailed = fail)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringListUiState())
}
