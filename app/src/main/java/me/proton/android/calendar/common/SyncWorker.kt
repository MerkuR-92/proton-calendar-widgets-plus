package me.proton.android.calendar.common

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.SyncServerEventsUseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams),
    KoinComponent {

    private val logger: Logger by inject()
    private val database: AppDatabase by inject()

    private val syncServerEventsUseCase: SyncServerEventsUseCase by inject()

    val notificationBuilder = NotificationCompat.Builder(appContext, ShowNotificationUseCase.CHANNEL_ID_SYNC_SERVICE)

    override suspend fun doWork(): Result {

        setForeground(createForegroundInfo())

        syncServerEvents()

        return Result.success()
    }

    private suspend fun syncServerEvents() {

        try {
            database.usersDao().select().forEach {
                syncServerEventsUseCase.execute(UserId(it.id)).ifSuccessAndLogErrors(logger) {}
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.e("exception in syncServerEvents()", e)
            }
        }

    }

    private fun createForegroundInfo(): ForegroundInfo {

        val title = applicationContext.getString(me.proton.android.calendar.R.string.notification_sync)

        val notification = notificationBuilder
            .setSmallIcon(me.proton.android.calendar.R.drawable.ic_calendar_today)
            .setContentTitle(title)
            .setTicker(title)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        return ForegroundInfo(ShowNotificationUseCase.NOTIFICATION_ID_SYNC_SERVICE, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "SYNC_SERVER_EVENTS_PERIODIC"

        fun setup(context: Context, logger: Logger) {

            try {
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
                    if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
            } catch (e: Exception) {
                logger.e("exception in SyncWorker setup", e)
            }
        }
    }

}
