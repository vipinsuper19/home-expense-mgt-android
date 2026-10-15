package com.dhama.homefinance.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.domain.recurrence.RecurrenceEngine
import com.dhama.homefinance.util.DateTicker
import com.dhama.homefinance.util.Money
import com.dhama.homefinance.ui.transaction.TransactionFormLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** All form fields as strings/ids so they survive process death in SavedStateHandle. */
data class ScheduleForm(
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String? = null,
    val amountText: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val intervalText: String = "1",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val dayOfMonthText: String,
    val dayOfWeek: Int,
    val monthOfYear: Int,
    /** Edit only: the date the new amount takes effect. */
    val amountEffectiveFrom: LocalDate
) {
    val amountPaise: Long? get() = Money.parseRupeesToPaise(amountText)
    val interval: Int? get() = intervalText.trim().toIntOrNull()?.takeIf { it in 1..366 }
    val dayOfMonth: Int? get() = dayOfMonthText.trim().toIntOrNull()?.takeIf { it in 1..31 }

    val nameError get() = name.isBlank()
    val amountError get() = amountText.isNotBlank() && amountPaise == null
    val intervalError get() = interval == null
    val dayOfMonthError get() = frequency in setOf(Frequency.MONTHLY, Frequency.YEARLY) && dayOfMonth == null
    val endDateError get() = endDate != null && endDate.isBefore(startDate)
}

data class ScheduleFormUiState(
    val form: ScheduleForm,
    val isEdit: Boolean = false,
    val categories: List<Category> = emptyList(),
    /** Edit: amount currently in force, to tell whether the user changed it. */
    val originalAmountPaise: Long? = null,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false
)

/**
 * Create a schedule, or edit one. Editing changes the name and the amount (from a chosen
 * effective date, future-only per PRD). Type, category and the date rule are fixed once
 * created, because past occurrences snapshot them; to change the rule, stop and create anew.
 */
@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: RecurringRepository,
    private val categories: CategoryRepository,
    private val ticker: DateTicker
) : ViewModel() {

    private val scheduleId: String? = savedState.get<String>(ARG_SCHEDULE_ID)
    private val meta = MutableStateFlow(Meta())

    private data class Meta(
        val originalAmount: Long? = null,
        val saving: Boolean = false,
        val failed: Boolean = false,
        val done: Boolean = false
    )

    private val form = MutableStateFlow(restore())

    init {
        if (scheduleId != null && savedState.get<Boolean>(KEY_LOADED) != true) {
            viewModelScope.launch {
                val s = repository.get(scheduleId) ?: return@launch
                val today = ticker.now()
                val amount = currentAmount(s, today)
                update {
                    it.copy(
                        name = s.name, type = s.type, categoryId = s.categoryId,
                        amountText = TransactionFormLogic.formatPaiseForInput(amount),
                        frequency = s.frequency, intervalText = s.interval.toString(),
                        startDate = s.startDate, endDate = s.endDate,
                        dayOfMonthText = s.dayOfMonth?.toString() ?: "",
                        dayOfWeek = s.dayOfWeek ?: it.dayOfWeek, monthOfYear = s.monthOfYear ?: it.monthOfYear,
                        amountEffectiveFrom = maxOf(today, s.startDate)
                    )
                }
                savedState[KEY_LOADED] = true
            }
        }
        if (scheduleId != null) {
            viewModelScope.launch {
                val s = repository.get(scheduleId) ?: return@launch
                meta.value = meta.value.copy(originalAmount = currentAmount(s, maxOf(ticker.now(), s.startDate)))
            }
        }
    }

    private suspend fun currentAmount(s: RecurringSchedule, day: LocalDate): Long {
        val changes = repository.observeAllAmountChanges().first().filter { it.scheduleId == s.id }
        return RecurrenceEngine.amountOn(s, changes, maxOf(day, s.startDate))
    }

    val uiState: StateFlow<ScheduleFormUiState> = combine(
        form,
        form.map { it.type }.flatMapLatest { categories.observeActive(it) },
        meta
    ) { f, cats, m ->
        ScheduleFormUiState(
            form = f,
            isEdit = scheduleId != null,
            categories = cats,
            originalAmountPaise = m.originalAmount,
            canSave = isValid(f) && !m.saving,
            isSaving = m.saving,
            saveFailed = m.failed,
            done = m.done
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleFormUiState(form = form.value, isEdit = scheduleId != null))

    private fun isValid(f: ScheduleForm): Boolean =
        !f.nameError && f.amountPaise != null && !f.intervalError && !f.dayOfMonthError && !f.endDateError &&
            f.categoryId != null && (scheduleId == null || !f.amountEffectiveFrom.isBefore(f.startDate))

    fun setName(v: String) = update { it.copy(name = v) }
    fun setType(v: TransactionType) = update { if (scheduleId != null || it.type == v) it else it.copy(type = v, categoryId = null) }
    fun setCategory(v: String) = update { it.copy(categoryId = v) }
    fun setAmount(v: String) = update { it.copy(amountText = v) }
    fun setFrequency(v: Frequency) = update { if (scheduleId != null) it else it.copy(frequency = v) }
    fun setInterval(v: String) = update { it.copy(intervalText = v.filter(Char::isDigit).take(3)) }
    /** Moving the start date re-seeds the day fields from it (the usual case: "every month from this date"). */
    fun setStartDate(v: LocalDate) = update {
        if (scheduleId != null) it else it.copy(
            startDate = v, dayOfMonthText = v.dayOfMonth.toString(), dayOfWeek = v.dayOfWeek.value, monthOfYear = v.monthValue
        )
    }
    fun setEndDate(v: LocalDate?) = update { if (scheduleId != null) it else it.copy(endDate = v) }
    fun setDayOfMonth(v: String) = update { it.copy(dayOfMonthText = v.filter(Char::isDigit).take(2)) }
    fun setDayOfWeek(v: Int) = update { it.copy(dayOfWeek = v) }
    fun setMonthOfYear(v: Int) = update { it.copy(monthOfYear = v) }
    fun setAmountEffectiveFrom(v: LocalDate) = update { it.copy(amountEffectiveFrom = v) }

    fun save() {
        val f = form.value
        if (!isValid(f) || meta.value.saving) return
        meta.value = meta.value.copy(saving = true, failed = false)
        viewModelScope.launch {
            val ok = runCatching {
                if (scheduleId == null) {
                    repository.create(
                        RecurringSchedule(
                            name = f.name, type = f.type, categoryId = f.categoryId!!, amountPaise = f.amountPaise!!,
                            frequency = f.frequency, interval = f.interval!!, startDate = f.startDate, endDate = f.endDate,
                            dayOfMonth = f.dayOfMonth.takeIf { f.frequency == Frequency.MONTHLY || f.frequency == Frequency.YEARLY },
                            dayOfWeek = f.dayOfWeek.takeIf { f.frequency == Frequency.WEEKLY },
                            monthOfYear = f.monthOfYear.takeIf { f.frequency == Frequency.YEARLY }
                        )
                    )
                } else {
                    repository.rename(scheduleId, f.name)
                    if (f.amountPaise != meta.value.originalAmount) {
                        repository.changeAmount(scheduleId, f.amountEffectiveFrom, f.amountPaise!!)
                    }
                }
            }.isSuccess
            meta.value = meta.value.copy(saving = false, failed = !ok, done = ok)
        }
    }

    private fun update(block: (ScheduleForm) -> ScheduleForm) {
        val next = block(form.value)
        form.value = next
        persist(next)
    }

    private fun persist(f: ScheduleForm) {
        savedState[KEY_NAME] = f.name
        savedState[KEY_TYPE] = f.type.name
        savedState[KEY_CATEGORY] = f.categoryId
        savedState[KEY_AMOUNT] = f.amountText
        savedState[KEY_FREQUENCY] = f.frequency.name
        savedState[KEY_INTERVAL] = f.intervalText
        savedState[KEY_START] = f.startDate.toString()
        savedState[KEY_END] = f.endDate?.toString()
        savedState[KEY_DOM] = f.dayOfMonthText
        savedState[KEY_DOW] = f.dayOfWeek
        savedState[KEY_MONTH] = f.monthOfYear
        savedState[KEY_EFFECTIVE] = f.amountEffectiveFrom.toString()
    }

    private fun restore(): ScheduleForm {
        val today = ticker.now()
        val start = savedState.get<String>(KEY_START)?.let(LocalDate::parse) ?: today
        return ScheduleForm(
            name = savedState[KEY_NAME] ?: "",
            type = TransactionType.parseOrNull(savedState[KEY_TYPE]) ?: TransactionType.EXPENSE,
            categoryId = savedState[KEY_CATEGORY],
            amountText = savedState[KEY_AMOUNT] ?: "",
            frequency = savedState.get<String>(KEY_FREQUENCY)?.let { v -> Frequency.entries.firstOrNull { it.name == v } } ?: Frequency.MONTHLY,
            intervalText = savedState[KEY_INTERVAL] ?: "1",
            startDate = start,
            endDate = savedState.get<String>(KEY_END)?.let(LocalDate::parse),
            dayOfMonthText = savedState[KEY_DOM] ?: start.dayOfMonth.toString(),
            dayOfWeek = savedState[KEY_DOW] ?: start.dayOfWeek.value,
            monthOfYear = savedState[KEY_MONTH] ?: start.monthValue,
            amountEffectiveFrom = savedState.get<String>(KEY_EFFECTIVE)?.let(LocalDate::parse) ?: today
        )
    }

    companion object {
        const val ARG_SCHEDULE_ID = "scheduleId"
        private const val KEY_LOADED = "sf.loaded"
        private const val KEY_NAME = "sf.name"
        private const val KEY_TYPE = "sf.type"
        private const val KEY_CATEGORY = "sf.category"
        private const val KEY_AMOUNT = "sf.amount"
        private const val KEY_FREQUENCY = "sf.frequency"
        private const val KEY_INTERVAL = "sf.interval"
        private const val KEY_START = "sf.start"
        private const val KEY_END = "sf.end"
        private const val KEY_DOM = "sf.dom"
        private const val KEY_DOW = "sf.dow"
        private const val KEY_MONTH = "sf.month"
        private const val KEY_EFFECTIVE = "sf.effective"
    }
}
