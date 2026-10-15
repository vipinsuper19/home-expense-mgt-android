package com.dhama.homenotestracker.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.model.Bill
import com.dhama.homenotestracker.data.model.Category
import com.dhama.homenotestracker.data.repository.BillRepository
import com.dhama.homenotestracker.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillUiState(
    val bills: List<Bill> = emptyList(),
    val categories: List<Category> = emptyList()
)

@HiltViewModel
class BillViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<BillUiState> = combine(
        billRepository.getAllBills(),
        categoryRepository.getAllCategories()
    ) { bills, categories ->
        BillUiState(bills, categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BillUiState()
    )

    fun addBill(
        name: String,
        amount: Double,
        dueDate: Long,
        categoryId: Long,
        recurringDay: Int?
    ) {
        viewModelScope.launch {
            billRepository.insertBill(
                Bill(
                    name = name,
                    amount = amount,
                    dueDate = dueDate,
                    categoryId = categoryId,
                    recurringDay = recurringDay
                )
            )
        }
    }

    fun toggleBillPayment(bill: Bill) {
        viewModelScope.launch {
            billRepository.updateBillPaymentStatus(bill.id, !bill.isPaid)
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            billRepository.deleteBill(bill)
        }
    }
}
