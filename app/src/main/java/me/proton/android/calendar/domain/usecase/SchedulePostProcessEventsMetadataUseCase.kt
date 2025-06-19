package me.proton.android.calendar.domain.usecase

import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject

/**
 * Schedules worker that processes all EventsMetadata from DB.
 */
class SchedulePostProcessEventsMetadataUseCase @Inject constructor(
    private val logger: Logger,
    private val workManager: WorkManager
) {

    fun execute(
        userId: String
    ): Operation {

        logger.v("executing SchedulePostProcessEventsMetadataUseCase")

        return workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.POST_PROCESS_EVENTS_METADATA,
                UseCaseWorker.INPUT_USER_ID to userId
            ),
            UseCaseWorker.UniqueWorkNames.POST_PROCESS_EVENTS_METADATA,
        )
    }

}
