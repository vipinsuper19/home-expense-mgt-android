package com.dhama.homefinance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhama.homefinance.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily reminder worker. Kept as wiring only; bill and expected-income
 * reminders are added with the recurring schedules work.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    @Suppress("unused") private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
