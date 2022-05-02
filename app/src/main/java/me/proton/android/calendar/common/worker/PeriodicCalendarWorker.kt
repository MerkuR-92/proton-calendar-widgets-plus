package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.PERIODIC_CALENDAR_WORKER_REFRESH_PERIOD
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getAccounts
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.concurrent.TimeUnit

class PeriodicCalendarWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams),
    KoinComponent {

    private val logger: Logger by inject()
    private val accountManager: AccountManager by inject()
    private val widgetRefresher: WidgetRefresher by inject()

    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    override suspend fun doWork(): Result {

        forceShowLateAlarms()

        forceRefreshWidget()

        return Result.success()
    }

    private fun forceRefreshWidget() {
        widgetRefresher.broadcastRefresh()
    }

    private suspend fun forceShowLateAlarms() {

        try {
            accountManager.getAccounts(AccountState.Ready).first().forEach {
                handleAlarmsUseCase.execute(it.userId)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.e("exception in forceShowLateAlarms()", e)
            }
        }

    }

    companion object {
        const val UNIQUE_WORK_NAME = "PERIODIC_CALENDAR_WORKER"

        fun setup(context: Context, logger: Logger) {

            try {
                val constraints = Constraints.Builder().build()

                val work = PeriodicWorkRequestBuilder<PeriodicCalendarWorker>(PERIODIC_CALENDAR_WORKER_REFRESH_PERIOD)
                    .setConstraints(constraints)
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
            } catch (e: Exception) {
                logger.e("exception in PeriodicCalendarWorker setup", e)
            }
        }
    }

}
