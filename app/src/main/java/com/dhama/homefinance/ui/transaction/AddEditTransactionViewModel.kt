package com.dhama.homefinance.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.TransactionRepository
import com.dhama.homefinance.ui.transaction.TransactionFormLogic.QuantityResult
import com.dhama.homefinance.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val date: LocalDate,
    val description: String = "",
    val notes: String = "",
    val quantityText: String = "",
    /** Errors only show after the user has touched the field (or tried to save). */
    val amountTouched: Boolean = false,
    val quantityTouched: Boolean = false
) {
    val amountPaise: Long? get() = Money.parseRupeesToPaise(amountText)
    val quantity: QuantityResult get() = TransactionFormLogic.parseQuantityMilli(quantityText)

    val amountError: Boolean get() = amountTouched && amountPaise == null
    val quantityError: Boolean get() = quantityTouched && quantity == QuantityResult.Invalid

    val isValid: Boolean
        get() = amountPaise != null && categoryId != null && quantity != QuantityResult.Invalid
}

data class AddEditUiState(
    val form: TransactionForm,
    val categories: List<Category> = emptyList(),
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** One-shot: screen should close. */
    val done: Boolean = false
) {
    val canSave: Boolean get() = form.isValid && !isSaving && !isLoading && !done
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddEditTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock
) : ViewModel() {

    private val state = MutableStateFlow(AddEditUiState(form = TransactionForm(date = LocalDate.now(clock))))
    private var original: Transaction? = null
    private var started = false

    private val visibleCategories = state
        .map { it.form.type to it.form.categoryId }
        .distinctUntilChanged()
        .flatMapLatest { (type, selectedId) ->
            categories.observeActive(type).map { active ->
                // Archived categories are never offered, except the one an edited
                // transaction already uses, so editing doesn't silently drop it.
                val selected = selectedId?.takeIf { id -> active.none { it.id == id } }
                    ?.let { categories.get(it) }?.takeIf { it.type == type }
                if (selected != null) active + selected else active
            }
        }

    val uiState: StateFlow<AddEditUiState> = combine(state, visibleCategories) { s, cats ->
        s.copy(categories = cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), state.value)

    /** Call once with the route's id; null means "add". */
    fun start(transactionId: String?) {
        if (started) return
        started = true
        if (transactionId == null) return
        state.update { it.copy(isEdit = true, isLoading = true) }
        viewModelScope.launch {
            val tx = transactions.get(transactionId)
            if (tx == null) {
                state.update { it.copy(isLoading = false, done = true) }
                return@launch
            }
            original = tx
            state.update {
                it.copy(
                    isLoading = false,
                    form = TransactionForm(
                        type = tx.type,
                        amountText = TransactionFormLogic.formatPaiseForInput(tx.amountPaise),
                        categoryId = tx.categoryId,
                        date = tx.date,
                        description = tx.description,
                        notes = tx.notes,
                        quantityText = tx.quantityMilli?.let(TransactionFormLogic::formatQuantityMilli).orEmpty()
                    )
                )
            }
        }
    }

    private fun edit(block: (TransactionForm) -> TransactionForm) =
        state.update { it.copy(form = block(it.form), saveFailed = false) }

    fun setType(type: TransactionType) = edit {
        if (it.type == type) it else it.copy(type = type, categoryId = null)
    }

    fun setAmount(text: String) = edit { it.copy(amountText = text, amountTouched = true) }
    fun setCategory(id: String) = edit { it.copy(categoryId = id) }
    fun setDateFromPicker(utcMillis: Long) = edit { it.copy(date = TransactionFormLogic.pickerMillisToDate(utcMillis)) }
    fun setDescription(text: String) = edit { it.copy(description = text) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setQuantity(text: String) = edit { it.copy(quantityText = text, quantityTouched = true) }

    fun save() {
        val current = state.value
        if (current.isSaving || current.done || current.isLoading) return // duplicate-tap guard
        val form = current.form
        if (!form.isValid) {
            edit { it.copy(amountTouched = true, quantityTouched = true) }
            return
        }
        state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val quantity = (form.quantity as? QuantityResult.Valid)?.milli
            val result = runCatching {
                val base = original
                if (base == null) {
                    transactions.add(
                        Transaction(
                            type = form.type,
                            categoryId = form.categoryId!!,
                            amountPaise = form.amountPaise!!,
                            quantityMilli = quantity,
                            description = form.description,
                            date = form.date,
                            notes = form.notes
                        )
                    )
                } else {
                    // Keeps id, createdAt and schedule/occurrence links; repository bumps updatedAt.
                    transactions.update(
                        base.copy(
                            type = form.type,
                            categoryId = form.categoryId!!,
                            amountPaise = form.amountPaise!!,
                            quantityMilli = quantity,
                            unitRatePaise = base.unitRatePaise.takeIf { quantity != null },
                            description = form.description,
                            date = form.date,
                            notes = form.notes
                        )
                    )
                }
            }
            state.update {
                if (result.isSuccess) it.copy(isSaving = false, done = true)
                else it.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    /** Only after the user confirmed in the dialog. */
    fun delete() {
        val tx = original ?: return
        if (state.value.isSaving || state.value.done) return
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = runCatching { transactions.delete(tx.id) }
            state.update {
                if (result.isSuccess) it.copy(isSaving = false, done = true)
                else it.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    internal val currentState: AddEditUiState get() = state.value
}
