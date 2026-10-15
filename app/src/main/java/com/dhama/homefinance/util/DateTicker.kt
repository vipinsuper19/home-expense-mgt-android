package com.dhama.homefinance.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * "Today" as a Flow, from the injected [Clock]. Re-checks every minute and whenever the
 * app resumes (MainActivity registers [resumeObserver]), and only emits when the date
 * actually changes, so screens roll over at midnight without a restart.
 */
@Singleton
class DateTicker @Inject constructor(private val clock: Clock) {

    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Register on an Activity's lifecycle; each ON_RESUME triggers a re-check. */
    val resumeObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) { resumes.tryEmit(Unit) }
    }

    fun now(): LocalDate = LocalDate.now(clock)

    val today: Flow<LocalDate> = merge(
        flow {
            while (true) {
                emit(Unit)
                delay(CHECK_INTERVAL_MS)
            }
        },
        resumes
    )
        .map { now() }
        .onStart { emit(now()) }
        .distinctUntilChanged()

    companion object {
        const val CHECK_INTERVAL_MS = 60_000L
    }
}
