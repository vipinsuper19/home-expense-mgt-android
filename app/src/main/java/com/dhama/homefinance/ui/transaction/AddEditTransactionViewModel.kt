package com.dhama.homefinance.ui.transaction

import androidx.lifecycle.SavedStateHandle
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
    /** Linked to a scheduled occurrence: the type must match it, so the toggle is locked. */
    val typeLocked: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val deleteFailed: Boolean = false,
    /** One-shot: screen should close. */
    val done: Boolean = false
) {
    val canSave: Boolean get() = form.isValid && !isSaving && !isLoading && !done
}

/**
 * The transaction id comes from the nav argument ([ARG_TRANSACTION_ID] in the
 * SavedStateHandle; null = add). Every form field is mirrored into the SavedStateHandle,
 * so after process death the in-progress form is restored as typed and the DB is not re-read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddEditTransactionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock
) : ViewModel() {

    private val transactionId: String? = savedState[ARG_TRANSACTION_ID]

    private val state = MutableStateFlow(
        AddEditUiState(
            form = restoreForm() ?: TransactionForm(
                type = presetType() ?: TransactionType.EXPENSE,
                date = LocalDate.now(clock)
            ),
            isEdit = transactionId != null,
            typeLocked = savedState.get<Boolean>(KEY_TYPE_LOCKED) == true
        )
    )

    init {
        if (transactionId != null && savedState.get<Boolean>(KEY_FORM_SAVED) != true) {
            loadForEdit(transactionId)
        }
    }

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

    private fun loadForEdit(id: String) {
        state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val tx = transactions.get(id)
            if (tx == null) {
                state.update { it.copy(isLoading = false, done = true) }
                return@launch
            }
            savedState[KEY_TYPE_LOCKED] = tx.occurrenceId != null
            state.update { it.copy(typeLocked = tx.occurrenceId != null) }
            setForm(
                TransactionForm(
                    type = tx.type,
                    amountText = TransactionFormLogic.formatPaiseForInput(tx.amountPaise),
                    categoryId = tx.categoryId,
                    date = tx.date,
                    description = tx.description,
                    notes = tx.notes,
                    quantityText = tx.quantityMilli?.let(TransactionFormLogic::formatQuantityMilli).orEmpty()
                )
            )
            state.update { it.copy(isLoading = false) }
        }
    }

    private fun setForm(form: TransactionForm) {
        persist(form)
        state.update { it.copy(form = form, saveFailed = false, deleteFailed = false) }
    }

    private fun edit(block: (TransactionForm) -> TransactionForm) = setForm(block(state.value.form))

    fun setType(type: TransactionType) = edit {
        if (it.type == type || state.value.typeLocked) it else it.copy(type = type, categoryId = null)
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
        state.update { it.copy(isSaving = true, saveFailed = false, deleteFailed = false) }
        viewModelScope.launch {
            val amount = form.amountPaise!!
            val quantity = (form.quantity as? QuantityResult.Valid)?.milli
            // Always derived from what's being saved, so an edit never keeps a stale rate.
            val unitRate = quantity?.let { Money.unitRatePaise(amount, it) }
            val result = runCatching {
                if (transactionId == null) {
                    transactions.add(
                        Transaction(
                            type = form.type,
                            categoryId = form.categoryId!!,
                            amountPaise = amount,
                            quantityMilli = quantity,
                            unitRatePaise = unitRate,
                            description = form.description,
                            date = form.date,
                            notes = form.notes
                        )
                    )
                } else {
                    // Read at save time (not cached) so a restored form still keeps id,
                    // createdAt and schedule/occurrence links; the repository bumps updatedAt.
                    // TODO(recurring milestone): if the transaction is linked to an occurrence,
                    //  recompute that occurrence's status (PENDING/PARTIAL/COMPLETED) here.
                    val base = checkNotNull(transactions.get(transactionId)) { "Transaction was deleted" }
                    transactions.update(
                        base.copy(
                            type = form.type,
                            categoryId = form.categoryId!!,
                            amountPaise = amount,
                            quantityMilli = quantity,
                            unitRatePaise = unitRate,
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
        val id = transactionId ?: return
        if (state.value.isSaving || state.value.done) return
        state.update { it.copy(isSaving = true, saveFailed = false, deleteFailed = false) }
        viewModelScope.launch {
            // TODO(recurring milestone): deleting a transaction linked to an occurrence must
            //  recompute that occurrence's status, in the same DB transaction.
            val result = runCatching { transactions.delete(id) }
            state.update {
                if (result.isSuccess) it.copy(isSaving = false, done = true)
                else it.copy(isSaving = false, deleteFailed = true)
            }
        }
    }

    private fun persist(f: TransactionForm) {
        savedState[KEY_FORM_SAVED] = true
        savedState[KEY_TYPE] = f.type.name
        savedState[KEY_AMOUNT] = f.amountText
        savedState[KEY_CATEGORY] = f.categoryId
        savedState[KEY_DATE] = f.date.toString()
        savedState[KEY_DESCRIPTION] = f.description
        savedState[KEY_NOTES] = f.notes
        savedState[KEY_QUANTITY] = f.quantityText
        savedState[KEY_AMOUNT_TOUCHED] = f.amountTouched
        savedState[KEY_QUANTITY_TOUCHED] = f.quantityTouched
    }

    /** Only applies to add mode; an edited transaction keeps its own type. */
    private fun presetType(): TransactionType? =
        if (transactionId != null) null
        else TransactionType.parseOrNull(savedState.get<String>(ARG_PRESET_TYPE))

    private fun restoreForm(): TransactionForm? {
        if (savedState.get<Boolean>(KEY_FORM_SAVED) != true) return null
        return TransactionForm(
            type = TransactionType.parseOrNull(savedState.get<String>(KEY_TYPE)) ?: TransactionType.EXPENSE,
            amountText = savedState[KEY_AMOUNT] ?: "",
            categoryId = savedState[KEY_CATEGORY],
            date = savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: LocalDate.now(clock),
            description = savedState[KEY_DESCRIPTION] ?: "",
            notes = savedState[KEY_NOTES] ?: "",
            quantityText = savedState[KEY_QUANTITY] ?: "",
            amountTouched = savedState[KEY_AMOUNT_TOUCHED] ?: false,
            quantityTouched = savedState[KEY_QUANTITY_TOUCHED] ?: false
        )
    }

    companion object {
        /** Nav argument key; matches Route.AddEditTransaction.transactionId. */
        const val ARG_TRANSACTION_ID = "transactionId"
        /** Nav argument key; matches Route.AddEditTransaction.presetType. */
        const val ARG_PRESET_TYPE = "presetType"
        internal const val KEY_TYPE_LOCKED = "typeLocked"
        internal const val KEY_FORM_SAVED = "form.saved"
        internal const val KEY_TYPE = "form.type"
        internal const val KEY_AMOUNT = "form.amountText"
        internal const val KEY_CATEGORY = "form.categoryId"
        internal const val KEY_DATE = "form.date"
        internal const val KEY_DESCRIPTION = "form.description"
        internal const val KEY_NOTES = "form.notes"
        internal const val KEY_QUANTITY = "form.quantityText"
        internal const val KEY_AMOUNT_TOUCHED = "form.amountTouched"
        internal const val KEY_QUANTITY_TOUCHED = "form.quantityTouched"
    }
}
