package me.proton.android.calendar.common.worker

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.data.joinToCalendars
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStore
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.IndexEventForSearchUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase.Companion.NOTIFICATION_ID_FETCH_CALENDARS_WORKER
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FetchCalendarsWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams),
    KoinComponent {

    private val logger: Logger by inject()
    private val accountManager: AccountManager by inject()
    private val calendarsApi: CalendarsApi by inject()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val calendarsRepository: CalendarsRepository by inject()
    private val searchDatabase: SearchDatabase by inject()
    private val fetchEventsUseCase: FetchEventsUseCase by inject()
    private val indexEventForSearchUseCase: IndexEventForSearchUseCase by inject()

    private lateinit var startInstant: Instant
    private lateinit var calendarMetadata: List<Metadata>
    private val calendarProgress = mutableMapOf<String, Int>()
    private var lastSubmittedLeftSeconds = Long.MAX_VALUE

    private data class Metadata(
        val calendarId: String,
        val calendarName: String,
        val total: Int
    )

    override suspend fun doWork(): Result {

        setForeground(
            createForegroundInfo(
                applicationContext.getString(
                    R.string.search_downloading_calendars,
                    applicationContext.getString(R.string.search_downloading_time_remaining_unknown)
                ), "", 0
            )
        )

        val userId = accountManager.getPrimaryUserId().filterNotNull().firstOrNull()

        var result: Result = Result.failure()

        if (userId != null) {
            val userValueStore = valueStoreProvider.provideValueStore(userId.id)

            val calendars = calendarsRepository.selectUserCalendars(userId.id)

            // determine how many Events there are in each Calendar
            calendarMetadata = calendars.map { calendar ->

                val totalEvents = calendarsApi.getEventsCount(userId, calendar.id).valueOrNullAndLogErrors(logger)?.total

                if (totalEvents != null) {
                    calendarProgress[calendar.id] = 0

                    Metadata(
                        calendar.id,
                        calendar.name,
                        totalEvents
                    )

                } else return Result.retry()

            }

            startInstant = Instant.now()

            try {
                coroutineScope {
                    launch {
                        calendarMetadata.map { calendarMetadata ->
                            async {
                                fetchCalendar(userId, userValueStore, searchDatabase, calendarMetadata, this)
                            }
                        }.awaitAll()

                        result = Result.success()
                    }
                }
            } catch (e: Exception) {
                logger.e("Exception downloading calendars", e)
                result = Result.failure()
            }

        } else {
            logger.e("userId == null in FetchCalendarsWorker")
            result = Result.failure()
        }

        return result
    }

    private suspend fun fetchCalendar(
        userId: UserId,
        userValueStore: ValueStore,
        searchDatabase: SearchDatabase,
        calendarMetadata: Metadata,
        coroutineScope: CoroutineScope
    ): Result {

        coroutineScope.async {

            accountManager.getPrimaryUserId().filterNotNull().first()

            // will be empty the very first time, or will contain the Event ID we should start fetching from this time
            var lastEventId: String? =
                userValueStore.getStringFromSet(ValueSet.FETCHING_CALENDAR_LAST_EVENT_ID, calendarMetadata.calendarId)
            var totalDownloadedCount: Long? = userValueStore.getLongFromSet(
                ValueSet.FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT,
                calendarMetadata.calendarId
            )

            // how many events were downloaded, including the ones from previous runs
            var allEventCount = if (lastEventId != null) totalDownloadedCount ?: 0 else 0

            reportProgress(calendarMetadata.calendarId, allEventCount.toInt())


            val eventEntityBatches =
                fetchEventsUseCase.execute(userId, calendarMetadata.calendarId, lastEventId, coroutineScope)

            coroutineScope.launch {
                for (eventEntityBatch in eventEntityBatches) {

                    allEventCount += eventEntityBatch.size

                    saveProgressSoFar(
                        userId.id,
                        userValueStore,
                        searchDatabase,
                        calendarMetadata.calendarId,
                        eventEntityBatch.lastOrNull()?.id,
                        allEventCount,
                        eventEntityBatch
                    )

                    reportProgress(calendarMetadata.calendarId, allEventCount.toInt())
                }
            }

        }.await()

        return Result.success()
    }

    private suspend fun reportProgress(calendarId: String, downloaded: Int) {

        calendarProgress[calendarId] = downloaded

        val elapsedMillis = Instant.now().toEpochMilli() - startInstant.toEpochMilli()

        val sumOfTotal = calendarMetadata.sumOf { it.total }
        val sumOfDownloaded = calendarProgress.values.sum()
        val sumOfLeft = sumOfTotal - sumOfDownloaded

        val averageMillisPerEvent =
            if (sumOfDownloaded == 0) Double.MAX_VALUE else elapsedMillis / sumOfDownloaded.toDouble()

        val leftSeconds = (sumOfLeft * averageMillisPerEvent / 1000).roundToLong()
        lastSubmittedLeftSeconds = leftSeconds

        val hours = TimeUnit.SECONDS.toHours(leftSeconds).toInt()
        val minutes = (TimeUnit.SECONDS.toMinutes(leftSeconds) % 60).toInt()
        val seconds = (TimeUnit.SECONDS.toSeconds(leftSeconds) % 60).toInt()

        val timeRemaining = if (hours > 0) {
            val value = if (minutes >= 30) hours + 1 else hours
            "$value ${
                applicationContext.resources.getQuantityString(
                    R.plurals.plural_hour,
                    value,
                    value
                )
            }"
        } else if (minutes > 0) {
            val value = if (seconds >= 30) minutes + 1 else minutes
            "$value ${
                applicationContext.resources.getQuantityString(
                    R.plurals.plural_minute,
                    value,
                    value
                )
            }"
        } else {
            if (seconds == 0) applicationContext.getString(R.string.search_downloading_time_remaining_almost_done) else "$seconds ${
                applicationContext.resources.getQuantityString(
                    R.plurals.plural_seconds,
                    seconds,
                    seconds
                )
            }"
        }

        val formattedTimeRemaining = if (averageMillisPerEvent == Double.MAX_VALUE) {
            applicationContext.resources.getString(R.string.search_downloading_time_remaining_unknown)
        } else {
            applicationContext.resources.getString(R.string.search_downloading_time_remaining, timeRemaining)
        }

        val progressPercentage = (sumOfDownloaded / sumOfTotal.toFloat() * 100).roundToInt()
        val progressText = "${progressPercentage}%"

        // update notification
        setForegroundAsync(
            createForegroundInfo(
                applicationContext.getString(R.string.search_downloading_calendars, formattedTimeRemaining),
                progressText,
                progressPercentage
            )
        )

        // set progress of this Work
        setProgress(
            workDataOf(
                PROGRESS_KEY_PROGRESS_PERCENTAGE to progressPercentage,
                PROGRESS_KEY_PROGRESS_TIME_LEFT to formattedTimeRemaining
            )
        )
    }

    /**
     * In order to resume download process later, we need to save [lastEventId] and [totalDownloadedCount] together,
     * because just the [lastEventId] doesn't tell us how many events out of "total" we already have.
     */
    private suspend fun saveProgressSoFar(
        userId: String,
        userValueStore: ValueStore,
        searchDatabase: SearchDatabase,
        calendarId: String,
        lastEventId: String?,
        totalDownloadedCount: Long,
        events: List<EventEntity>
    ): Boolean {

        calendarsRepository.persistEvents(*events.toTypedArray())

        indexEventForSearchUseCase.execute(userId, events)

        // save helper data for this Calendar in case we need to resume work later
        lastEventId?.let {
            userValueStore.putStringInSet(ValueSet.FETCHING_CALENDAR_LAST_EVENT_ID, calendarId, lastEventId)
            userValueStore.putLongInSet(
                ValueSet.FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT,
                calendarId,
                totalDownloadedCount.toLong()
            )
        }

        return true
    }

    private fun createForegroundInfo(title: String, progressText: String, progressPercent: Int): ForegroundInfo {
        // PendingIntent to cancel the worker
        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId())

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShowNotificationUseCase.createNotificationChannels(applicationContext)
        }

        val notification =
            NotificationCompat.Builder(applicationContext, ShowNotificationUseCase.CHANNEL_ID_CALENDAR_FETCH)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_calendar_download)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(100, progressPercent, false)
                .addAction(0, applicationContext.getString(R.string.search_downloading_pause), intent)
                .build()

        return ForegroundInfo(NOTIFICATION_ID_FETCH_CALENDARS_WORKER, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "FETCH_CALENDARS_WORK"
        const val PROGRESS_KEY_PROGRESS_PERCENTAGE = "PROGRESS_KEY_PROGRESS_PERCENTAGE"
        const val PROGRESS_KEY_PROGRESS_TIME_LEFT = "PROGRESS_KEY_PROGRESS_TIME_LEFT"
    }

}
