package com.dhama.homenotestracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhama.homenotestracker.data.repository.BillRepository
import com.dhama.homenotestracker.data.repository.TaskRepository
import com.dhama.homenotestracker.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val billRepository: BillRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val currentTime = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val twoDaysMillis = 2 * oneDayMillis

        // Check tasks
        val tasks = taskRepository.getAllTasks().first()
        tasks.forEach { task ->
            if (!task.isCompleted && task.dueDate != null) {
                val timeLeft = task.dueDate - currentTime
                if (timeLeft in 0..twoDaysMillis) {
                    val daysLeft = (timeLeft / oneDayMillis).toInt()
                    notificationHelper.showNotification(
                        title = "Task Reminder",
                        message = "${task.title} is due in $daysLeft day(s)!",
                        notificationId = task.id.toInt()
                    )
                }
            }
        }

        // Check bills
        val bills = billRepository.getAllBills().first()
        bills.forEach { bill ->
            if (!bill.isPaid) {
                val timeLeft = bill.dueDate - currentTime
                if (timeLeft in 0..twoDaysMillis) {
                    val daysLeft = (timeLeft / oneDayMillis).toInt()
                    notificationHelper.showNotification(
                        title = "Bill Reminder",
                        message = "Your bill '${bill.name}' for $${bill.amount} is due in $daysLeft day(s)!",
                        notificationId = (bill.id + 10000).toInt()
                    )
                }
            }
        }

        return Result.success()
    }
}
