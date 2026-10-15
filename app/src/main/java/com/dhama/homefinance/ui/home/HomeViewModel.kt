package com.dhama.homefinance.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.repository.SummaryRepository
import com.dhama.homefinance.data.repository.TransactionListRepository
import com.dhama.homefinance.domain.MonthlySummary
import com.dhama.homefinance.domain.ObserveMonthlySummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val today: LocalDate,
    /** Null until the first load. */
    val summary: MonthlySummary? = null,
    /** Always today's actual totals, whatever month is selected. */
    val todayTotals: ActualTotals = ActualTotals(0, 0, 0),
    val recent: List<TransactionListItem> = emptyList()
) {
    val isLoading: Boolean get() = summary == null
    /** Nothing actual and nothing expected in the selected month. */
    val isEmpty: Boolean
        get() = summary != null && summary.transactionCount == 0 && summary.openExpectedCount == 0
    val hasExpected: Boolean get() = (summary?.openExpectedCount ?: 0) > 0
}

/**
 * Home dashboard. The selected month lives in SavedStateHandle. Actual figures come from
 * transactions only and expected figures from open occurrences only (via
 * [ObserveMonthlySummaryUseCase]); the screen labels them separately and never adds them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val observeMonthlySummary: ObserveMonthlySummaryUseCase,
    private val summaryRepository: SummaryRepository,
    private val listRepository: TransactionListRepository,
    private val clock: Clock
) : ViewModel() {

    private val month = savedState.getStateFlow(KEY_MONTH, YearMonth.now(clock).toString())
        .map(YearMonth::parse)

    private val today: LocalDate get() = LocalDate.now(clock)

    private val monthData = month.flatMapLatest { m ->
        combine(
            observeMonthlySummary(m),
            listRepository.items(m, TransactionFilter()).map { it.take(RECENT_LIMIT) }
        ) { summary, recent -> summary to recent }
    }

    private val todayTotals = summaryRepository.actualTotals(today, today)

    val uiState: StateFlow<HomeUiState> = combine(month, monthData, todayTotals) { m, (summary, recent), todayT ->
        HomeUiState(
            month = m,
            isCurrentMonth = m == YearMonth.from(today),
            today = today,
            summary = summary,
            todayTotals = todayT,
            recent = recent
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        YearMonth.parse(savedState.get<String>(KEY_MONTH) ?: YearMonth.now(clock).toString()).let {
            HomeUiState(month = it, isCurrentMonth = it == YearMonth.from(today), today = today)
        }
    )

    private fun current(): YearMonth =
        YearMonth.parse(savedState.get<String>(KEY_MONTH) ?: YearMonth.now(clock).toString())

    fun previousMonth() = setMonth(current().minusMonths(1))
    fun nextMonth() = setMonth(current().plusMonths(1))
    fun goToCurrentMonth() = setMonth(YearMonth.now(clock))
    fun setMonth(value: YearMonth) { savedState[KEY_MONTH] = value.toString() }

    companion object {
        const val RECENT_LIMIT = 5
        const val TOP_CATEGORIES = 5
        internal const val KEY_MONTH = "home.month"
    }
}
