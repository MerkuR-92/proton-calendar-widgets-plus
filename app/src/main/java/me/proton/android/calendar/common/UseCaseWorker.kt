package me.proton.android.calendar.common

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.SyncServerEventsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject

class UseCaseWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val logger: Logger by inject()

    /**
     * Used to inject and execute different usecases from this Worker
     */
    class UseCaseId {
        companion object {
            const val SYNC_SERVER_EVENTS = "SYNC_SERVER_EVENTS"
            const val UPDATE_SERVER_CALENDAR_SETTINGS = "UPDATE_SERVER_CALENDAR_SETTINGS"
        }
    }

    /**
     * Work Input Data keys.
     */
    companion object {
        const val INPUT_USE_CASE_ID = "INPUT_USE_CASE_ID"
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_CALENDAR_ID = "INPUT_CALENDAR_ID"
        const val INPUT_DISPLAY_ID = "INPUT_DISPLAY_ID"
        const val INPUT_EVENT_ID = "INPUT_EVENT_ID"
    }

    /**
     * Used by WorkManager to ensure uniqueness.
     */
    class UniqueWorkNames {
        companion object {
            const val SYNC_SERVER_EVENTS = "SYNC_SERVER_EVENTS"
            const val UPDATE_SERVER_CALENDAR_SETTINGS = "UPDATE_SERVER_CALENDAR_SETTINGS"
        }
    }

    override suspend fun doWork(): Result {

        logger.v("inside UseCaseWorker doWork()")

        val useCaseId = inputData.getString(INPUT_USE_CASE_ID)
        val useCaseResult = when (useCaseId) {
            UseCaseId.SYNC_SERVER_EVENTS -> {
                val syncServerEventsUseCase: SyncServerEventsUseCase = get()
                syncServerEventsUseCase.execute(inputData.getString(INPUT_USER_ID) ?: return Result.failure())
            }
            UseCaseId.UPDATE_SERVER_CALENDAR_SETTINGS -> {
                val updateCalendarUseCase: UpdateCalendarUseCase = get()
                updateCalendarUseCase.executeServerUpdate(
                    inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure(),
                    inputData.getInt(INPUT_DISPLAY_ID, 1))
            }
            else -> {
                TODO("unsupported or empty UseCaseId: $useCaseId")
            }
        }

        // updating progress for the UI
//        val progressUpdate = workDataOf("progress_key" to 100)
//        this.setProgressAsync(progressUpdate)

        return when (useCaseResult) {
            UseCase.Result.Success -> {
                logger.v("UseCaseId=$useCaseId success")
                Result.success()
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("UseCaseId=$useCaseId failure, reason: ${useCaseResult.message}")
                Result.failure()
            }
            is UseCase.Result.Error -> {
                logger.i("UseCaseId=$useCaseId error, reason: ${useCaseResult.message}")
                Result.retry()
            }
        }
    }

}