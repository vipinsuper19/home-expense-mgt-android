package com.dhama.homefinance.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.DailyTotal
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.TransactionListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One day's section: header totals plus its rows (newest day first). */
data class DaySection(val total: DailyTotal, val items: List<TransactionListItem>) {
    val date: LocalDate get() = total.date
}

data class TransactionsUiState(
    val month: YearMonth,
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val search: String = "",
    val categories: List<Category> = emptyList(),
    val totals: ActualTotals = ActualTotals(0, 0, 0),
    val days: List<DaySection> = emptyList(),
    val isLoading: Boolean = true
) {
    val isFiltered: Boolean get() = type != null || categoryId != null || search.isNotBlank()
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
    val netPaise: Long get() = totals.incomePaise - totals.expensePaise
}

/**
 * Month, type, category and search live in SavedStateHandle so the list comes back
 * exactly as left after rotation or process death.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: TransactionListRepository,
    private val categoryRepository: CategoryRepository,
    clock: Clock
) : ViewModel() {

    private val currentMonth = YearMonth.now(clock)

    private val month = savedState.getStateFlow(KEY_MONTH, currentMonth.toString())
    private val type = savedState.getStateFlow<String?>(KEY_TYPE, null)
    private val categoryId = savedState.getStateFlow<String?>(KEY_CATEGORY, null)
    private val search = savedState.getStateFlow(KEY_SEARCH, "")

    private data class Query(val month: YearMonth, val filter: TransactionFilter)

    private val query = combine(
        month, type, categoryId,
        // Don't hit the DB on every keystroke; trim so trailing spaces don't refire.
        search.map { it.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS)
    ) { m, t, c, s ->
        Query(YearMonth.parse(m), TransactionFilter(t?.let(TransactionType::valueOf), c, s))
    }.distinctUntilChanged()

    private val data = query.flatMapLatest { q ->
        combine(
            repository.items(q.month, q.filter),
            repository.dailyTotals(q.month, q.filter),
            repository.filterCategories(q.month, q.filter.type),
            // Look the selected category up so an archived-but-selected one stays in the chip.
            kotlinx.coroutines.flow.flow {
                emit(q.filter.categoryId?.let { categoryRepository.get(it) })
            }
        ) { items, daily, cats, selectedCat ->
            val byDate = items.groupBy { it.date }
            // Month totals are summed from the same daily rows the list headers use, so
            // the card and the day headers can never disagree (all Long paise).
            val totals = ActualTotals(
                incomePaise = daily.sumOf { it.incomePaise },
                expensePaise = daily.sumOf { it.expensePaise },
                transactionCount = items.size
            )
            val options = if (selectedCat == null || cats.any { it.id == selectedCat.id }) cats
            else cats + selectedCat
            Loaded(q, totals, daily.map { DaySection(it, byDate[it.date].orEmpty()) }, options)
        }
    }

    private data class Loaded(
        val query: Query,
        val totals: ActualTotals,
        val days: List<DaySection>,
        val categories: List<Category>
    )

    private val initial = TransactionsUiState(month = YearMonth.parse(month.value))

    val uiState: StateFlow<TransactionsUiState> = combine(month, type, categoryId, search, data) { m, t, c, s, d ->
        TransactionsUiState(
            month = YearMonth.parse(m),
            type = t?.let(TransactionType::valueOf),
            categoryId = c,
            search = s,
            categories = d.categories,
            totals = d.totals,
            days = d.days,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    fun previousMonth() = setMonth(YearMonth.parse(month.value).minusMonths(1))
    fun nextMonth() = setMonth(YearMonth.parse(month.value).plusMonths(1))
    fun setMonth(value: YearMonth) { savedState[KEY_MONTH] = value.toString() }
    fun goToCurrentMonth() = setMonth(currentMonth)

    fun setType(value: TransactionType?) {
        savedState[KEY_TYPE] = value?.name
        // A category of the other type would make the list silently empty. Look it up in
        // the DB rather than uiState, which may not be loaded yet after recreation.
        val selected = categoryId.value
        if (value != null && selected != null) {
            viewModelScope.launch {
                val category = categoryRepository.get(selected)
                if (category?.type != value && categoryId.value == selected && type.value == value.name) {
                    savedState[KEY_CATEGORY] = null
                }
            }
        }
    }

    fun setCategory(value: String?) { savedState[KEY_CATEGORY] = value }
    fun setSearch(value: String) { savedState[KEY_SEARCH] = value }

    fun clearFilters() {
        savedState[KEY_TYPE] = null
        savedState[KEY_CATEGORY] = null
        savedState[KEY_SEARCH] = ""
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        internal const val KEY_MONTH = "list.month"
        internal const val KEY_TYPE = "list.type"
        internal const val KEY_CATEGORY = "list.categoryId"
        internal const val KEY_SEARCH = "list.search"
    }
}
