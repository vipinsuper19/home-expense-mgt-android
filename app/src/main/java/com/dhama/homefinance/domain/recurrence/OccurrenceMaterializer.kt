package com.dhama.homefinance.domain.recurrence

import android.util.Log
import com.dhama.homefinance.data.repository.RecurringRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Keeps expected occurrences generated for the window around today. Called on app start,
 * when Home opens and when the Recurring screens open (and again after midnight via the
 * DateTicker). Idempotent, so calling it often is cheap and safe.
 */
fun interface OccurrenceMaterializer {
    /** Returns false if generation failed; callers show a message but keep working. */
    suspend fun materializeAround(today: LocalDate): Boolean

    companion object {
        /** Start of this month .. end of the month three months ahead. */
        fun window(today: LocalDate): Pair<LocalDate, LocalDate> =
            today.withDayOfMonth(1) to YearMonth.from(today).plusMonths(3).atEndOfMonth()
    }
}

@Singleton
class RoomOccurrenceMaterializer @Inject constructor(
    private val repository: RecurringRepository
) : OccurrenceMaterializer {
    override suspend fun materializeAround(today: LocalDate): Boolean = try {
        val (from, to) = OccurrenceMaterializer.window(today)
        repository.materialize(from, to)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching { Log.w("Materializer", "Generating occurrences failed", e) }
        false
    }
}
