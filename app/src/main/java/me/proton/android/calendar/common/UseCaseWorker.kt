package me.proton.android.calendar.common

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject
import java.util.*
import kotlin.collections.ArrayList

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
            const val UPDATE_PRIMARY_TIMEZONE = UpdateCalendarUserSettingsUseCase.WORKER_ID_TZ
            const val UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE = UpdateCalendarUserSettingsUseCase.WORKER_ID_AUTO_DETECT
            const val UPDATE_DISPLAY_WEEK_NUMBER = UpdateCalendarUserSettingsUseCase.WORKER_ID_WEEK_NUMBER
            const val UPDATE_TIME_FORMAT = UpdateUserSettingsUseCase.WORKER_ID_TIME_FORMAT
            const val UPDATE_WEEK_START = UpdateUserSettingsUseCase.WORKER_ID_WEEK_START
            const val UPDATE_PARTICIPATION_STATUS = UpdateParticipationStatusUseCase.WORKER_ID
            const val UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT = UpdateParticipationStatusUseCase.WORKER_ID_SINGLE_EDIT
            const val FETCH_ADDRESSES = FetchUserUseCase.WORKER_ID
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
        const val INPUT_ATTENDEE_ID = "INPUT_ATTENDEE_ID"
        const val INPUT_PARTICIPATION_STATUS = "INPUT_PARTICIPATION_STATUS"
        const val INPUT_PRIMARY_TIMEZONE = "INPUT_PRIMARY_TIMEZONE"
        const val INPUT_AUTO_DETECT_PRIMARY_TIMEZONE = "INPUT_AUTO_DETECT_PRIMARY_TIMEZONE"
        const val INPUT_DISPLAY_WEEK_NUMBER = "INPUT_DISPLAY_WEEK_NUMBER"
        const val INPUT_TIME_FORMAT = "INPUT_TIME_FORMAT"
        const val INPUT_WEEK_START = "INPUT_WEEK_START"
        const val INPUT_PERSONAL_ICAL_STRING = "INPUT_PERSONAL_ICAL_STRING"
        const val INPUT_UPDATE_TIME = "INPUT_UPDATE_TIME"
        const val INPUT_EVENT_UID = "INPUT_EVENT_UID"
        const val INPUT_USER_EMAILS = "INPUT_USER_EMAILS"

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
            const val UPDATE_PRIMARY_TIMEZONE = "UPDATE_PRIMARY_TIMEZONE"
            const val UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE = "UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE"
            const val UPDATE_DISPLAY_WEEK_NUMBER = "UPDATE_DISPLAY_WEEK_NUMBER"
            const val UPDATE_TIME_FORMAT = "UPDATE_TIME_FORMAT"
            const val UPDATE_WEEK_START = "UPDATE_WEEK_START"
            const val UPDATE_PARTICIPATION_STATUS = "UPDATE_PARTICIPATION_STATUS"
            const val UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT = "UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT"
            const val FETCH_ADDRESSES = "FETCH_ADDRESSES"
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
            UseCaseId.UPDATE_PRIMARY_TIMEZONE -> {
                val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase = get()
                updateCalendarUserSettingsUseCase.executePrimaryTimezone(
                    userId,
                    inputData.getString(INPUT_PRIMARY_TIMEZONE) ?: return Result.failure()
                )
            }
            UseCaseId.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE -> {
                val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase = get()
                updateCalendarUserSettingsUseCase.executeAutoDetectPrimaryTimezone(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Boolean>(INPUT_AUTO_DETECT_PRIMARY_TIMEZONE))
                        inputData.getBoolean(INPUT_AUTO_DETECT_PRIMARY_TIMEZONE, true)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_DISPLAY_WEEK_NUMBER -> {
                val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase = get()
                updateCalendarUserSettingsUseCase.executeDisplayWeekNumber(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Boolean>(INPUT_DISPLAY_WEEK_NUMBER))
                        inputData.getBoolean(INPUT_DISPLAY_WEEK_NUMBER, true)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_TIME_FORMAT -> {
                val updateUserSettingsUseCase: UpdateUserSettingsUseCase = get()
                updateUserSettingsUseCase.executeTimeFormat(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Int>(INPUT_TIME_FORMAT))
                        inputData.getInt(INPUT_TIME_FORMAT, 0)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_WEEK_START -> {
                val updateUserSettingsUseCase: UpdateUserSettingsUseCase = get()
                updateUserSettingsUseCase.executeWeekStart(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Int>(INPUT_WEEK_START))
                        inputData.getInt(INPUT_WEEK_START, 0)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_PARTICIPATION_STATUS -> {
                val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase = get()
                val updateTime = inputData.getInt(INPUT_UPDATE_TIME, 0)
                updateParticipationStatusUseCase.execute(
                    userId,
                    inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure(),
                    inputData.getString(INPUT_EVENT_ID) ?: return Result.failure(),
                    inputData.getString(INPUT_ATTENDEE_ID) ?: return Result.failure(),
                    inputData.getInt(INPUT_PARTICIPATION_STATUS, 0),
                    inputData.getString(INPUT_PERSONAL_ICAL_STRING),
                    if (updateTime == 0) null else updateTime)
            }
            UseCaseId.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT -> {
                val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase = get()
                updateParticipationStatusUseCase.executeClearSingleEdits(
                    userId,
                    inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure(),
                    inputData.getString(INPUT_EVENT_UID) ?: return Result.failure(),
                    inputData.getStringArray(INPUT_USER_EMAILS)?.toList() ?: return Result.failure(),
                    inputData.getInt(INPUT_PARTICIPATION_STATUS, 0))
            }
            UseCaseId.FETCH_ADDRESSES -> {
                val fetchUserUseCase: FetchUserUseCase = get()
                fetchUserUseCase.executeGetAddresses(
                    userId
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
            is UseCase.Result.Success<*> -> {
                logger.v("UseCaseId=$useCaseId success")
                Result.success()
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("UseCaseId=$useCaseId failure, reason: ${useCaseResult.message}")
                Result.failure()
            }
            is UseCase.Result.Error -> {
                if (this.runAttemptCount >= WORKER_MAX_RETRY_COUNT) {
                    logger.e("UseCaseId=$useCaseId error, reason: ${useCaseResult.message}, max retry exceeded")
                    Result.failure()
                } else {
                    logger.i("UseCaseId=$useCaseId error, reason: ${useCaseResult.message}, retrying")
                    Result.retry()
                }
            }
        }
    }

}
