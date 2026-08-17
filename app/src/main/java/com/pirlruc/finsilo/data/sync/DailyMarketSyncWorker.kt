package com.pirlruc.finsilo.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pirlruc.finsilo.FinsiloApplication
import com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyMarketSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FinsiloApplication).container
        val snapshot = container.repository.load()
        if (snapshot.isEmpty) return Result.success()
        val asOf = LocalDate.now()
        val synced = SyncMarketDataUseCase(container.marketFeed)(snapshot, asOf)
        container.repository.upsertQuotes(synced.marketData, synced.fxRates)
        return if (synced.failures.isEmpty() || synced.marketData.isNotEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "finsilo-daily-market-sync"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<DailyMarketSyncWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(millisUntil2300(), TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun millisUntil2300(): Long {
            val now = ZonedDateTime.now()
            var target = now.with(LocalTime.of(23, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).toMillis()
        }
    }
}
