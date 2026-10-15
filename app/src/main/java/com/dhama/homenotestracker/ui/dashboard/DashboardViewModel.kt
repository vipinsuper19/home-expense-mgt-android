package com.dhama.homenotestracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.local.BillDao
import com.dhama.homenotestracker.data.local.TaskDao
import com.dhama.homenotestracker.data.model.Bill
import com.dhama.homenotestracker.data.model.Priority
import com.dhama.homenotestracker.data.model.RecurringType
import com.dhama.homenotestracker.data.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val userName: String = "Vipin",
    val todayTasks: List<Task> = emptyList(),
    val pendingTasks: List<Task> = emptyList(),
    val completedTodayCount: Int = 0,
    val upcomingBills: List<Bill> = emptyList(),
    val completionPercentage: Float = 0f
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val billDao: BillDao
) : ViewModel() {

    // Mock data for initial UI implementation
    private val mockTasks = listOf(
        Task(id = 1, title = "Buy Milk", description = "", categoryId = 1, priority = Priority.MEDIUM, recurringType = RecurringType.DAILY, reminderTime = null, dueDate = null, notes = "", color = 0xFF4CAF50.toInt(), icon = "shopping_cart"),
        Task(id = 2, title = "Pay Rent", description = "", categoryId = 2, priority = Priority.HIGH, recurringType = RecurringType.MONTHLY, reminderTime = null, dueDate = null, notes = "", color = 0xFFF44336.toInt(), icon = "receipt"),
        Task(id = 3, title = "Gym Workout", description = "", categoryId = 1, priority = Priority.LOW, recurringType = RecurringType.DAILY, reminderTime = null, dueDate = null, notes = "", color = 0xFF2196F3.toInt(), icon = "fitness_center")
    )

    private val mockBills = listOf(
        Bill(id = 1, name = "Electricity Bill", amount = 120.0, dueDate = System.currentTimeMillis() + 86400000 * 2, categoryId = 2),
        Bill(id = 2, name = "Internet Bill", amount = 60.0, dueDate = System.currentTimeMillis() + 86400000 * 5, categoryId = 2)
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        taskDao.getAllTasks(),
        billDao.getAllBills()
    ) { tasks, bills ->
        val effectiveTasks = if (tasks.isEmpty()) mockTasks else tasks
        val effectiveBills = if (bills.isEmpty()) mockBills else bills

        val todayTasks = effectiveTasks.filter { !it.isCompleted }
        val completedToday = effectiveTasks.count { it.isCompleted }
        val totalToday = todayTasks.size + completedToday
        val percentage = if (totalToday > 0) completedToday.toFloat() / totalToday else 0.72f // Mock percentage if nothing done
        
        DashboardUiState(
            todayTasks = todayTasks,
            pendingTasks = effectiveTasks.filter { !it.isCompleted },
            completedTodayCount = completedToday,
            upcomingBills = effectiveBills.filter { !it.isPaid },
            completionPercentage = percentage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(completionPercentage = 0.72f) // Initial mock
    )

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskDao.updateTaskCompletionStatus(task.id, !task.isCompleted)
        }
    }
}

