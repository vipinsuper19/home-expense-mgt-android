package com.dhama.homefinance.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.local.OccurrenceWithPaid
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.repository.RecordResult
import com.dhama.homefinance.data.repository.RecurringRepository
import java.util.UUID
import com.dhama.homefinance.domain.recurrence.OccurrenceMaterializer
import com.dhama.homefinance.domain.recurrence.RecurrenceEngine
import com.dhama.homefinance.ui.transaction.TransactionFormLogic
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
    val isOverdue: Boolean,
    /** Actual receipts/payments linked to this occurrence, oldest first. */
    val receipts: List<Transaction> = emptyList()
)

/** Outcome of validating the payment dialog before anything is written. */
enum class PaymentInput { ACCEPTED, INVALID_AMOUNT, EXCEEDS_OUTSTANDING }

data class ScheduleDetailUiState(
    val loading: Boolean = true,
    val schedule: RecurringSchedule? = null,
    val currentAmountPaise: Long = 0,
    val nextDueDate: LocalDate? = null,
    val history: List<HistoryRow> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    /** True when the last action was rejected (e.g. already closed); the screen shows a message. */
    val actionFailed: Boolean = false,
    /** An action is running; further taps are ignored and confirm buttons disabled. */
    val isBusy: Boolean = false,
    val materializeFailed: Boolean = false
)

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RecurringRepository,
    private val materializer: OccurrenceMaterializer,
    private val ticker: DateTicker
) : ViewModel() {

    val scheduleId: String = requireNotNull(savedState.get<String>(ARG_SCHEDULE_ID)) { "scheduleId required" }
    private val failed = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val materializeFailed = MutableStateFlow(false)

    private val today = ticker.today.onEach { day -> materializeFailed.value = !materializer.materializeAround(day) }

    val uiState: StateFlow<ScheduleDetailUiState> = combine(
        repository.observeSchedule(scheduleId),
        combine(repository.observeHistory(scheduleId), repository.observeReceipts(scheduleId)) { h, r -> h to r },
        combine(repository.observeAllPauses(), repository.observeAllAmountChanges()) { p, c -> p to c },
        today,
        combine(failed, busy, materializeFailed) { f, b, m -> Triple(f, b, m) }
    ) { s, (history, receipts), (pauses, changes), day, (fail, isBusy, matFail) ->
        val byOccurrence = receipts.groupBy { it.occurrenceId }
        ScheduleDetailUiState(
            loading = false,
            schedule = s,
            currentAmountPaise = s?.let { RecurrenceEngine.amountOn(it, changes.filter { c -> c.scheduleId == it.id }, maxOf(day, it.startDate)) } ?: 0,
            nextDueDate = s?.takeIf { it.status == ScheduleStatus.ACTIVE }?.let { RecurrenceEngine.nextDueDate(it, pauses, day) },
            history = history.map {
                HistoryRow(it, it.occurrence.status.isOpen && it.occurrence.dueDate.isBefore(day), byOccurrence[it.occurrence.id].orEmpty())
            },
            today = day,
            actionFailed = fail,
            isBusy = isBusy,
            materializeFailed = matFail
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleDetailUiState())

    fun pause() = act { repository.pause(scheduleId, ticker.now()) }
    fun resume() = act { repository.resume(scheduleId, ticker.now()) }
    /** Stop after today: today's occurrence (if any) stays due; nothing later is generated. */
    fun stop() = act { repository.stop(scheduleId, ticker.now()) }
    fun skip(dueDate: LocalDate) = act { repository.skip(scheduleId, dueDate) }

    /** Remaining amount to prefill the payment dialog with ("" if nothing is left). */
    fun paymentPrefill(occurrenceId: String): String =
        uiState.value.history.firstOrNull { it.item.occurrence.id == occurrenceId }
            ?.item?.remainingPaise?.takeIf { it > 0 }
            ?.let(TransactionFormLogic::formatPaiseForInput).orEmpty()

    /**
     * Returns false (and does nothing) when [amountText] isn't a valid positive amount.
     * While another action is running the call is ignored (double-tap guard) and returns true.
     */
    fun recordPayment(occurrenceId: String, amountText: String, date: LocalDate, receiptId: String = UUID.randomUUID().toString()): PaymentInput {
        val paise = Money.parseRupeesToPaise(amountText) ?: return PaymentInput.INVALID_AMOUNT
        val remaining = uiState.value.history.firstOrNull { it.item.occurrence.id == occurrenceId }?.item?.remainingPaise
        if (remaining != null && paise > remaining) return PaymentInput.EXCEEDS_OUTSTANDING
        act {
            // The repository re-checks inside its DB transaction (state may be stale).
            val result = repository.recordPayment(occurrenceId, paise, date, receiptId = receiptId)
            check(result !is RecordResult.ExceedsOutstanding) { "Exceeds outstanding" }
        }
        return PaymentInput.ACCEPTED
    }

    fun dismissError() { failed.value = false }

    /** Busy is set synchronously before launching, so a second tap in the same frame is dropped. */
    private fun act(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                failed.value = runCatching { block() }.isFailure
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        const val ARG_SCHEDULE_ID = "scheduleId"
    }
}
