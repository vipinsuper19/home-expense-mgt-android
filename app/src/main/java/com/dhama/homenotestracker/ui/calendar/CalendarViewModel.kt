package com.dhama.homenotestracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhama.homenotestracker.data.model.Task
import com.dhama.homenotestracker.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class CalendarUiState(
    val tasks: List<Task> = emptyList(),
    val selectedDate: Long = System.currentTimeMillis()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate

    val uiState: StateFlow<CalendarUiState> = combine(
        taskRepository.getAllTasks(),
        _selectedDate
    ) { tasks, selectedDate ->
        CalendarUiState(
            tasks = tasks.filter { task ->
                val taskCal = Calendar.getInstance().apply { timeInMillis = task.dueDate ?: task.createdAt }
                val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                taskCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                taskCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
            },
            selectedDate = selectedDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun onDateSelected(date: Long) {
        _selectedDate.value = date
    }
}
