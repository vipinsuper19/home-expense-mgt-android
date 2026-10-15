package com.dhama.homenotestracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.repository.BillRepository
import com.dhama.homenotestracker.data.repository.GroceryRepository
import com.dhama.homenotestracker.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatisticsUiState(
    val taskCompletionRate: Float = 0f,
    val totalGrocerySpending: Double = 0.0,
    val totalBillSpending: Double = 0.0,
    val completedTasksCount: Int = 0,
    val pendingTasksCount: Int = 0
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val groceryRepository: GroceryRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        taskRepository.getAllTasks(),
        groceryRepository.getAllGroceries(),
        billRepository.getAllBills()
    ) { tasks, groceries, bills ->
        val completed = tasks.count { it.isCompleted }
        val total = tasks.size
        val rate = if (total > 0) completed.toFloat() / total else 0f
        
        StatisticsUiState(
            taskCompletionRate = rate,
            totalGrocerySpending = groceries.sumOf { it.price * it.quantity },
            totalBillSpending = bills.sumOf { it.amount },
            completedTasksCount = completed,
            pendingTasksCount = total - completed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState()
    )
}
