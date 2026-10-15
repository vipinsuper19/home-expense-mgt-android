package com.dhama.homefinance.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.local.OccurrenceWithPaid
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.domain.recurrence.RecurrenceEngine
import com.dhama.homefinance.util.DateTicker
import com.dhama.homefinance.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryRow(
    val item: OccurrenceWithPaid,
    /** Derived, never stored: still open and due before today. */
    val isOverdue: Boolean
)

data class ScheduleDetailUiState(
    val loading: Boolean = true,
    val schedule: RecurringSchedule? = null,
    val currentAmountPaise: Long = 0,
    val nextDueDate: LocalDate? = null,
    val history: List<HistoryRow> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    /** True when the last action was rejected (e.g. already closed); the screen shows a message. */
    val actionFailed: Boolean = false
)

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RecurringRepository,
    private val ticker: DateTicker
) : ViewModel() {

    val scheduleId: String = requireNotNull(savedState.get<String>(ARG_SCHEDULE_ID)) { "scheduleId required" }
    private val failed = MutableStateFlow(false)

    private val today = ticker.today.onEach { day ->
        val (from, to) = materializeWindow(day)
        repository.materialize(from, to)
    }

    val uiState: StateFlow<ScheduleDetailUiState> = combine(
        repository.observeSchedule(scheduleId),
        repository.observeHistory(scheduleId),
        combine(repository.observeAllPauses(), repository.observeAllAmountChanges()) { p, c -> p to c },
        today,
        failed
    ) { s, history, (pauses, changes), day, fail ->
        ScheduleDetailUiState(
            loading = false,
            schedule = s,
            currentAmountPaise = s?.let { RecurrenceEngine.amountOn(it, changes.filter { c -> c.scheduleId == it.id }, maxOf(day, it.startDate)) } ?: 0,
            nextDueDate = s?.takeIf { it.status == ScheduleStatus.ACTIVE }?.let { RecurrenceEngine.nextDueDate(it, pauses, day) },
            history = history.map { HistoryRow(it, it.occurrence.status.isOpen && it.occurrence.dueDate.isBefore(day)) },
            today = day,
            actionFailed = fail
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleDetailUiState())

    fun pause() = act { repository.pause(scheduleId, ticker.now()) }
    fun resume() = act { repository.resume(scheduleId, ticker.now()) }
    /** Stop after today: today's occurrence (if any) stays due; nothing later is generated. */
    fun stop() = act { repository.stop(scheduleId, ticker.now()) }
    fun skip(dueDate: LocalDate) = act { repository.skip(scheduleId, dueDate) }

    /** Returns false (and does nothing) when [amountText] isn't a valid positive amount. */
    fun recordPayment(occurrenceId: String, amountText: String, date: LocalDate): Boolean {
        val paise = Money.parseRupeesToPaise(amountText) ?: return false
        act { repository.recordPayment(occurrenceId, paise, date) }
        return true
    }

    fun dismissError() { failed.value = false }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            failed.value = runCatching { block() }.isFailure
        }
    }

    companion object {
        const val ARG_SCHEDULE_ID = "scheduleId"
    }
}
