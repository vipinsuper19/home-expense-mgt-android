package com.dhama.homenotestracker.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.model.Category
import com.dhama.homenotestracker.data.model.Priority
import com.dhama.homenotestracker.data.model.RecurringType
import com.dhama.homenotestracker.data.model.Task
import com.dhama.homenotestracker.data.repository.CategoryRepository
import com.dhama.homenotestracker.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChecklistUiState(
    val tasks: List<Task> = emptyList(),
    val categories: List<Category> = emptyList()
)

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<ChecklistUiState> = combine(
        taskRepository.getAllTasks(),
        categoryRepository.getAllCategories()
    ) { tasks, categories ->
        ChecklistUiState(tasks, categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChecklistUiState()
    )

    fun addTask(
        title: String,
        description: String,
        categoryId: Long,
        priority: Priority,
        recurringType: RecurringType,
        reminderTime: Long?,
        dueDate: Long?,
        notes: String,
        color: Int,
        icon: String
    ) {
        viewModelScope.launch {
            taskRepository.insertTask(
                Task(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    priority = priority,
                    recurringType = recurringType,
                    reminderTime = reminderTime,
                    dueDate = dueDate,
                    notes = notes,
                    color = color,
                    icon = icon
                )
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskRepository.updateTaskCompletionStatus(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }
}
