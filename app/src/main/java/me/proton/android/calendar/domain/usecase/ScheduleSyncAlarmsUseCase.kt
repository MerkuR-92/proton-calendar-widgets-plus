package me.proton.android.calendar.domain.usecase

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScheduleSyncAlarmsUseCase @Inject constructor(
    private val logger: Logger,
    private val workManager: WorkManager
) : UseCase {

    fun execute(
        userId: UserId,
        initialDelay: Duration = Duration.ofSeconds(0),
        force: Boolean = false
    ): Operation {

        logger.v("executing ScheduleSyncAlarmsUseCase")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_ALARMS,
                    UseCaseWorker.INPUT_USER_ID to userId.id,
                    UseCaseWorker.INPUT_FORCE_SYNC_ALARMS to force
                )
            )
            .build()

        // TODO work is unique per user-id, make sure different inputdata => different unique work
        return workManager.enqueueUniqueWork(
            UseCaseWorker.UniqueWorkNames.SYNC_ALARMS,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work
        )
    }

}
