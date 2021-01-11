package me.proton.android.calendar.common

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.SyncService
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams),
    KoinComponent {

    private val logger: Logger by inject()

    override suspend fun doWork(): Result {
        ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, SyncService::class.java))
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "SYNC_SERVER_EVENTS_PERIODIC"

        fun setup(context: Context) {

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                // TODO provide options for this in settings
//            .setRequiresBatteryNotLow(!BuildConfig.DEBUG)
//            .setRequiresDeviceIdle(!BuildConfig.DEBUG)
                .build()

            val work = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_EVENTS_PERIODIC_REFRESH_PERIOD)
                .setConstraints(constraints)
                .setInitialDelay(if (BuildConfig.DEBUG) 0L else SYNC_EVENTS_PERIODIC_DELAY_START.get(ChronoUnit.SECONDS), TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                work
            )
        }
    }

}
