package com.dhama.homenotestracker.ui.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.model.Category
import com.dhama.homenotestracker.data.model.Grocery
import com.dhama.homenotestracker.data.repository.CategoryRepository
import com.dhama.homenotestracker.data.repository.GroceryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroceryUiState(
    val groceries: List<Grocery> = emptyList(),
    val categories: List<Category> = emptyList()
)

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val groceryRepository: GroceryRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<GroceryUiState> = combine(
        groceryRepository.getAllGroceries(),
        categoryRepository.getAllCategories()
    ) { groceries, categories ->
        GroceryUiState(groceries, categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GroceryUiState()
    )

    fun addGrocery(
        name: String,
        categoryId: Long,
        quantity: Double,
        unit: String,
        price: Double,
        isRecurring: Boolean
    ) {
        viewModelScope.launch {
            groceryRepository.insertGrocery(
                Grocery(
                    name = name,
                    categoryId = categoryId,
                    quantity = quantity,
                    unit = unit,
                    price = price,
                    isRecurring = isRecurring
                )
            )
        }
    }

    fun deleteGrocery(grocery: Grocery) {
        viewModelScope.launch {
            groceryRepository.deleteGrocery(grocery)
        }
    }

    fun updateGrocery(grocery: Grocery) {
        viewModelScope.launch {
            groceryRepository.updateGrocery(grocery)
        }
    }
}
