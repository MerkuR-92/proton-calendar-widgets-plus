package me.proton.android.calendar.common

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject

class UseCaseWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val logger: Logger by inject()
    private val valueStoreProvider: ValueStoreProvider by inject()

    /**
     * Used to inject and execute different usecases from this Worker
     */
    class UseCaseId {
        companion object {
            const val SYNC_SERVER_EVENTS = SyncServerEventsUseCase.WORKER_ID
            const val SYNC_ALARMS = SyncAlarmsUseCase.WORKER_ID
            const val UPDATE_CALENDAR = UpdateCalendarUseCase.WORKER_ID
            const val UPDATE_CALENDAR_LIST = UpdateCalendarUseCase.WORKER_LIST_ID
            const val SEND_BUG_REPORT = SendBugReportUseCase.WORKER_ID
            const val UPDATE_CALENDAR_USER_SETTINGS = UpdateCalendarUserSettingsUseCase.WORKER_ID
        }
    }

    /**
     * Work Input Data keys.
     */
    companion object {
        const val INPUT_USE_CASE_ID = "INPUT_USE_CASE_ID"
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_CALENDAR_ID = "INPUT_CALENDAR_ID"
        const val INPUT_EVENT_ID = "INPUT_EVENT_ID"
        const val INPUT_PRIMARY_TIMEZONE = "INPUT_PRIMARY_TIMEZONE"
        const val INPUT_AUTO_DETECT_PRIMARY_TIMEZONE = "INPUT_AUTO_DETECT_PRIMARY_TIMEZONE"

        // Bug Report
        const val INPUT_OS_NAME = "INPUT_OS_NAME"
        const val INPUT_OS_VERSION = "INPUT_OS_VERSION"
        const val INPUT_CLIENT = "INPUT_CLIENT"
        const val INPUT_APP_VERSION_NAME = "INPUT_APP_VERSION_NAME"
        const val INPUT_TITLE = "INPUT_TITLE"
        const val INPUT_DESCRIPTION = "INPUT_DESCRIPTION"
        const val INPUT_USERNAME = "INPUT_USERNAME"
        const val INPUT_EMAIL = "INPUT_EMAIL"
    }

    /**
     * Used by WorkManager to ensure uniqueness.
     */
    class UniqueWorkNames {
        companion object {
            const val SYNC_SERVER_EVENTS = "SYNC_SERVER_EVENTS"
            const val SYNC_ALARMS = "SYNC_ALARMS"
            const val UPDATE_CALENDAR = "UPDATE_CALENDAR"
            const val UPDATE_CALENDAR_LIST = "UPDATE_CALENDAR_LIST"
            const val SEND_BUG_REPORT = "SEND_BUG_REPORT"
            const val UPDATE_CALENDAR_USER_SETTINGS = "UPDATE_CALENDAR_USER_SETTINGS"
        }
    }

    override suspend fun doWork(): Result {

        logger.v("inside UseCaseWorker doWork(), usecaseid: ${inputData.getString(INPUT_USE_CASE_ID)}")

        val userId = inputData.getString(INPUT_USER_ID)?.let { UserId(it) } ?: return Result.failure()
        val useCaseId = inputData.getString(INPUT_USE_CASE_ID)
        val useCaseResult = when (useCaseId) {
            UseCaseId.SYNC_SERVER_EVENTS -> {
                val syncServerEventsUseCase: SyncServerEventsUseCase = get()
                syncServerEventsUseCase.execute(userId)
            }
            UseCaseId.SYNC_ALARMS -> {
                val syncAlarmsUseCase: SyncAlarmsUseCase = get()
                syncAlarmsUseCase.execute(userId)
            }
            UseCaseId.UPDATE_CALENDAR -> {
                val updateCalendarUseCase: UpdateCalendarUseCase = get()
                updateCalendarUseCase.executeUpdate(
                    userId,
                    inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure())
            }
            UseCaseId.UPDATE_CALENDAR_LIST -> {
                val updateCalendarUseCase: UpdateCalendarUseCase = get()
                updateCalendarUseCase.executeUpdateList(userId)
            }
            UseCaseId.SEND_BUG_REPORT -> {
                val sendBugReportUseCase: SendBugReportUseCase = get()
                sendBugReportUseCase.execute(
                    userId,
                    inputData.getString(INPUT_OS_NAME) ?: return Result.failure(),
                    inputData.getString(INPUT_OS_VERSION) ?: return Result.failure(),
                    inputData.getString(INPUT_CLIENT) ?: return Result.failure(),
                    inputData.getString(INPUT_APP_VERSION_NAME) ?: return Result.failure(),
                    inputData.getString(INPUT_TITLE) ?: return Result.failure(),
                    inputData.getString(INPUT_DESCRIPTION) ?: return Result.failure(),
                    inputData.getString(INPUT_USERNAME) ?: return Result.failure(),
                    inputData.getString(INPUT_EMAIL) ?: return Result.failure())
            }
            UseCaseId.UPDATE_CALENDAR_USER_SETTINGS -> {
                val primaryTimezone = inputData.getString(INPUT_PRIMARY_TIMEZONE)
                val autoDetectPrimaryTimezone = inputData.getInt(INPUT_AUTO_DETECT_PRIMARY_TIMEZONE, -1)
                val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase = get()
                updateCalendarUserSettingsUseCase.execute(
                    userId,
                    primaryTimezone,
                    if (autoDetectPrimaryTimezone == -1) null else autoDetectPrimaryTimezone
                )
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
