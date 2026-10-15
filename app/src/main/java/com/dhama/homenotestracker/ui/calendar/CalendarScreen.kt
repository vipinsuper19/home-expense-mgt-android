package com.dhama.homenotestracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhama.homenotestracker.data.model.Task
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = uiState.selectedDate

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Calendar") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            MonthHeader(calendar)
            Spacer(modifier = Modifier.height(16.dp))
            CalendarGrid(
                currentDate = calendar,
                onDateSelected = { viewModel.onDateSelected(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tasks for ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(calendar.time)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.tasks) { task ->
                        CalendarTaskItem(task)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthHeader(calendar: Calendar) {
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.time)
    Text(
        text = monthName,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun CalendarGrid(currentDate: Calendar, onDateSelected: (Long) -> Unit) {
    val daysInMonth = currentDate.getActualMaximum(Calendar.DAY_OF_MONTH)
    val calendar = currentDate.clone() as Calendar
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed starting Sunday
    
    val totalDays = daysInMonth + firstDayOfWeek
    val rows = (totalDays + 6) / 7

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        for (i in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (j in 0 until 7) {
                    val dayIndex = i * 7 + j
                    val dayOfMonth = dayIndex - firstDayOfWeek + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val isSelected = dayOfMonth == currentDate.get(Calendar.DAY_OF_MONTH)
                        DayCell(
                            day = dayOfMonth,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val newCal = currentDate.clone() as Calendar
                                newCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                onDateSelected(newCal.timeInMillis)
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(day: Int, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            // Mock status indicator dots for demo
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Green))
                if (day % 3 == 0) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Red))
            }
        }
    }
}

@Composable
fun CalendarTaskItem(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = task.isCompleted, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = task.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
